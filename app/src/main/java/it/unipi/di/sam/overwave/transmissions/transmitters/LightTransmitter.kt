package it.unipi.di.sam.overwave.transmissions.transmitters

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import it.unipi.di.sam.overwave.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


object LightTransmitter : Transmitter, SurfaceHolder.Callback, Camera.AutoFocusCallback  {

    private var camera: Camera? = null
    private var surfaceHolder: SurfaceHolder? = null

    override suspend fun transmit(context: Context, data: ByteArray, frequency: Int) {
        if (!hasHardwareSupport(context)) return
        camera = getCameraInstance()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                context as Activity,
                listOf(Manifest.permission.CAMERA).toTypedArray(),
                1
            )
        }
        camera?.run {
            val surfaceView = (context as Activity).findViewById<SurfaceView>(R.id.surface_view)
            surfaceHolder = surfaceView.holder
            surfaceHolder!!.addCallback(this@LightTransmitter)

            // If we don't recall this here then camera won't work after
            // resuming from sleep
            this@LightTransmitter.surfaceCreated(surfaceHolder!!);

            withContext(Dispatchers.IO) {
                setPreviewDisplay(surfaceHolder)
                // TODO use a fragment, so we can use a proper surface view
                val milliseconds = frequency.toLong()
                val toEmit = dataToBinaryString(data)
                startPreview()
                toEmit.forEach {
                    when(it) {
                        '1' -> switchOn()
                        else -> switchOff()
                    }
                    delay(milliseconds)
                }

            }
        }
        camera = null
    }

    private suspend fun switchOn() {
        withContext(Dispatchers.Main) {
            camera?.run {
                val parameters = parameters
                if (!parameters.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    this.parameters = parameters;
                    autoFocus(this@LightTransmitter);
                    startPreview();
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    setParameters(parameters);
                } else {
                    // Standard logic for device with support for FLASH_MODE_TORCH
                    // Some phones don't preserve parameters state after stopPreview(),
                    // we gotta set them each time here!
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    setParameters(parameters);
                    startPreview();
                }
            }
        }
    }

    private suspend fun switchOff() {
        withContext(Dispatchers.Main) {
            camera?.run {
                val parameters = parameters
                // Some phones don't turn off flash upon stopPreview(), we gotta do
                // it manually here!
                parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
                this.parameters = parameters
            }
        }
    }

    private fun dispose() {
        camera?.stopPreview()
        camera?.release()
        camera = null
        surfaceHolder = null
    }

    /** A safe way to get an instance of the Camera object. */
    private fun getCameraInstance(): Camera? = try {
        Camera.open() // attempt to get a Camera instance
    } catch (e: Exception) {
        // Camera is not available (in use or does not exist)
        null // returns null if camera is unavailable
    }

    override fun getDefaultFrequency(): Int = 100

    override fun hasHardwareSupport(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)


    override fun surfaceCreated(holder: SurfaceHolder) {
        // HACK: since we want to open the camera handle only when actually turning on
        // the light (to avoid occupying the camera handle when the light is off) we
        // need to surround this try/catch block with an "if guard" to avoid calling
        // setPreviewDisplay() with a still empty camera handle (which would crash)
        camera?.let {
            try {
                it.setPreviewDisplay(surfaceHolder)
            } catch (e: Exception) {
                Log.e("LightTransmitter", e.message ?: "surface Created error")
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}
    override fun onAutoFocus(success: Boolean, camera: Camera?) {}
}
