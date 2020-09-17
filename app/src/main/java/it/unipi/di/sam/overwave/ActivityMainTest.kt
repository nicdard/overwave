package it.unipi.di.sam.overwave

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import it.unipi.di.sam.overwave.transmissions.receivers.LightReceiverFragment
import it.unipi.di.sam.overwave.transmissions.transmitters.TorchFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class ActivityMainTest : AppCompatActivity(),
    CompoundButton.OnCheckedChangeListener,
    View.OnClickListener,
    CoroutineScope by MainScope()
{

    private lateinit var torchFragment: TorchFragment
    private lateinit var torchButton: ToggleButton
    private var lastInvocation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_test)
        torchButton = findViewById(R.id.torch_button)
        torchButton.setOnCheckedChangeListener(this)
        val receiverButton = findViewById<Button>(R.id.to_receiver)
        receiverButton.setOnClickListener(this)
        val fragmentManager: FragmentManager = supportFragmentManager
        if (savedInstanceState == null) {
            torchFragment = TorchFragment.newInstance()
            fragmentManager.beginTransaction().apply {
                replace(
                    R.id.frameLayout,
                    torchFragment,
                    TorchFragment.TORCH_FRAGMENT_TAG
                )
                addToBackStack(null)
            }.commit()
        } else {
            torchFragment = fragmentManager.findFragmentByTag(TorchFragment.TORCH_FRAGMENT_TAG) as TorchFragment
        }

    }

    private fun torch(enable: Boolean) {
        lastInvocation = enable
        if (hasCameraPermission()) {
            if (enable) {
                launch {
                    torchFragment.transmit("Ciao".toByteArray(), 100)
                }
            } else {
                // torchFragment.off()
            }
        } else {
            requestCameraPermission()
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        when(buttonView?.id) {
            R.id.torch_button -> torch(isChecked)
        }
    }

    private fun requestCameraPermission() = ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.CAMERA),
        PERMISSIONS_REQUEST_CAMERA
    )

    private fun hasCameraPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)
    }

    private fun onCameraPermissionGranted()  = torch(lastInvocation)

    private fun onCameraPermissionDenied() {
        requestCameraPermission()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    log("Permission granted")
                    onCameraPermissionGranted()
                } else {
                    log("Permission denied")
                    onCameraPermissionDenied()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        torch(false)
        cancel()
    }

    companion object {
        const val PERMISSIONS_REQUEST_CAMERA = 243
        private const val DEBUG_TAG = "[ActivityMainTest]"

        fun log(message: String) = Log.i(DEBUG_TAG, message)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.to_receiver -> {
                val fragmentManager: FragmentManager = supportFragmentManager
                val receiver = LightReceiverFragment.newInstance()
                fragmentManager.beginTransaction().apply {
                    replace(
                        R.id.frameLayout,
                        receiver,
                        TorchFragment.TORCH_FRAGMENT_TAG
                    )
                    addToBackStack(null)
                }.commit()
            }
        }
    }
}