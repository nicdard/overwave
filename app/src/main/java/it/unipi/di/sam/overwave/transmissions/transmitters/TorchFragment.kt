package it.unipi.di.sam.overwave.transmissions.transmitters

import android.hardware.Camera
import android.hardware.Camera.AutoFocusCallback
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import it.unipi.di.sam.overwave.R
import kotlinx.coroutines.*

class TorchFragment : Fragment(), SurfaceHolder.Callback, AutoFocusCallback {

    private var camera: Camera? = null
        get() = synchronized(this@TorchFragment) { field }
        set(value) = synchronized(this@TorchFragment) { field = value}
    private lateinit var surfaceHolder: SurfaceHolder

    private val job: Job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.light_transmitter_fragment, container, false)
        // Init Camera resources
        surfaceHolder = root.findViewById<SurfaceView>(R.id.surface_view).holder
        surfaceHolder.addCallback(this)
        return root
    }

    private fun initialiseCamera() {
        if (camera == null) {
            try {
                camera = Camera.open() // attempt to get a Camera instance
                // If we don't recall this here then camera won't work after
                // resuming from sleep
                this.surfaceCreated(surfaceHolder)
            } catch (e: Exception) {
                log("Can't open camera, maybe it's in use or not available. $e")
            }
        }
    }

    fun transmit(data: ByteArray, frequency: Int = getDefaultFrequency()) {
        scope.launch {
            initialiseCamera()
            camera?.run {
                val milliseconds = frequency.toLong()
                val toEmit = dataToBinaryString(data)
                val ponOld = parameters.apply { flashMode = Camera.Parameters.FLASH_MODE_ON }
                val ponNew = parameters.apply { flashMode = Camera.Parameters.FLASH_MODE_TORCH }
                val poff = parameters.apply { flashMode = Camera.Parameters.FLASH_MODE_OFF }
                startPreview()
                toEmit.forEach {
                    when (it) {
                        '1' -> {
                            // Hacked torch switch on logic for devices which do not support FLASH_MODE_TOCH (e.g. Galaxy Ace)
                            if (!parameters.supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                                parameters = ponOld
                                autoFocus(this@TorchFragment)
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
                    delay(milliseconds)
                }
                dispose()
            }
        }
    }

    private fun getDefaultFrequency(): Int = 100

    private fun dispose() {
        scope.cancel()
        camera?.stopPreview()
        // Release camera resource
        camera?.release()
        camera = null
    }

    override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    override fun onAutoFocus(success: Boolean, camera: Camera?) {}

    override fun surfaceCreated(holder: SurfaceHolder) {
        // HACK: since we want to open the camera handle only when actually turning on
        // the light (to avoid occupying the camera handle when the light is off) we
        // need to surround this try/catch block with an "if guard" to avoid calling
        // setPreviewDisplay() with a still empty camera handle (which would crash)
        camera?.run {
            try {
                setPreviewDisplay(surfaceHolder)
            } catch (e: Exception) {
                log(e.message ?: "surface Created error")
            }
        }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    companion object {

        private const val DEBUG_TAG = "[TORCH]"
        const val TORCH_FRAGMENT_TAG = "torchFragmentTag"
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment BlankFragment.
         */
        @JvmStatic
        fun newInstance() = TorchFragment()

        @JvmStatic
        fun log(message: String) = Log.e(DEBUG_TAG, message)
    }
}



/*
private fun switchOn() {
    camera?.let {
        val parameters = it.parameters
        // Hacked torch switch on logic for devices which do not support FLASH_MODE_TOCH (e.g. Galaxy Ace)
        if (!parameters.supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            parameters.flashMode = Camera.Parameters.FLASH_MODE_ON
            it.parameters = parameters
            it.autoFocus(this)
            it.startPreview()
            parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
            it.parameters = parameters
        } else {
            // Standard logic for device with support for FLASH_MODE_TORCH
            // Some phones don't preserve parameters state after stopPreview(),
            // we gotta set them each time here!
            // parameters.focusMode = Camera.Parameters.FOCUS_MODE_INFINITY
            parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            it.parameters = parameters
            it.startPreview()
        }
    }
}
*/


/*
private fun switchOff() {
    camera?.run {
        val parameters = parameters
        // Some phones don't turn off flash upon stopPreview(), we gotta do
        // it manually here!
        parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
        setParameters(parameters)
    }
}
 */