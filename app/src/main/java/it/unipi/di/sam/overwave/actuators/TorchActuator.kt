package it.unipi.di.sam.overwave.actuators

import android.Manifest
import android.hardware.Camera
import android.view.SurfaceHolder
import it.unipi.di.sam.overwave.transmitter.TransmitActivity
import it.unipi.di.sam.overwave.transmitter.TransmitViewModel
import it.unipi.di.sam.overwave.utils.dataToBinaryString
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter

@Suppress("DEPRECATION")
class TorchActuator(
    private val holder: SurfaceHolder,
    private val shouldSaveRawData: Boolean = false,
    private val storageDir: String?,
) : IActuator, SurfaceHolder.Callback, Camera.AutoFocusCallback {

    init {
        holder.addCallback(this)
    }

    private var camera: Camera? = null
        get() = synchronized(this@TorchActuator) { field }
        set(value) = synchronized(this@TorchActuator) { field = value}

    private var writer: FileWriter? = null
    private val timestamps = mutableListOf<Long>()

    override fun initialise() {
        if (shouldSaveRawData && storageDir != null && writer == null) {
            writer = try {
                FileWriter(File(storageDir, "camera" + System.currentTimeMillis() + ".csv"))
            } catch (e: Exception) {
                null
            }
        }
        if (camera == null) {
            try {
                camera = Camera.open() // attempt to get a Camera instance
                // If we don't recall this here then camera won't work after
                // resuming from sleep
                this.surfaceCreated(holder)
            } catch (e: Exception) {
                TransmitActivity.log("Can't open camera, maybe it's in use or not available. $e")
            }
        }
    }

    override suspend fun transmit(data: ByteArray, frequency: Int, viewModel: TransmitViewModel) {
        withContext(Dispatchers.IO) {
            camera?.run {
                val milliseconds = frequency.toLong()
                val toEmit = dataToBinaryString(data)
                val ponOld = parameters.apply { flashMode = Camera.Parameters.FLASH_MODE_ON }
                val ponNew = parameters.apply { flashMode = Camera.Parameters.FLASH_MODE_TORCH }
                val poff = parameters.apply { flashMode = Camera.Parameters.FLASH_MODE_OFF }
                startPreview()
                val length = toEmit.length
                var i = 0;
                // Make the coroutine cancellable
                while (isActive && i < length) {
                    val bit = toEmit[i]
                    when (bit) {
                        '1' -> {
                            // Hacked torch switch on logic for devices which do not support FLASH_MODE_TOCH (e.g. Galaxy Ace)
                            if (!parameters.supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                                parameters = ponOld
                                autoFocus(this@TorchActuator)
                            } else {
                                // Standard logic for device with support for FLASH_MODE_TORCH
                                // Some phones don't preserve parameters state after stopPreview(),
                                // we gotta set them each time here!
                                // parameters.focusMode = Camera.Parameters.FOCUS_MODE_INFINITY
                                parameters = ponNew
                            }
                        }
                        else -> parameters = poff
                    }
                    i++
                    if (i % 3 == 0) {
                        viewModel.publishProgress(100 * i / length)
                    }
                    delay(milliseconds)
                    timestamps.add(System.currentTimeMillis())
                }
                // Turn off the light after transmission.
                parameters = poff
                writer?.run {
                    timestamps.forEach {
                        write(String.format("%d \n", it))
                    }
                }
                timestamps.clear()
            }
        }
    }

    override fun dispose() {
        try {
            writer?.close()
        } catch (e: Exception) {}
        writer = null
        camera?.stopPreview()
        // Release camera resource
        camera?.release()
        camera = null
    }

    override fun onAutoFocus(success: Boolean, camera: Camera?) {}

    override fun surfaceCreated(holder: SurfaceHolder) {
        // HACK: since we want to open the camera handle only when actually turning on
        // the light (to avoid occupying the camera handle when the light is off) we
        // need to surround this try/catch block with an "if guard" to avoid calling
        // setPreviewDisplay() with a still empty camera handle (which would crash)
        camera?.run {
            try {
                setPreviewDisplay(holder)
            } catch (e: Exception) {
                TransmitActivity.log(e.message ?: "surface Created error")
            }
        }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun neededPermissions() = arrayOf(Manifest.permission.CAMERA)
}