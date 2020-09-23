package it.unipi.di.sam.overwave.transmitter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import it.unipi.di.sam.overwave.BaseMenuActivity
import it.unipi.di.sam.overwave.R
import it.unipi.di.sam.overwave.actuators.IActuator
import it.unipi.di.sam.overwave.actuators.TorchActuator
import it.unipi.di.sam.overwave.actuators.VibrationActuator
import it.unipi.di.sam.overwave.bluetooth.*
import it.unipi.di.sam.overwave.databinding.ActivityTransmitBinding
import it.unipi.di.sam.overwave.utils.Preferences
import it.unipi.di.sam.overwave.utils.getDefaultFrequency
import kotlinx.coroutines.*

class TransmitActivity : BaseMenuActivity(), CoroutineScope by MainScope() {

    private lateinit var binding: ActivityTransmitBinding
    private lateinit var actuator: IActuator
    private lateinit var preferences: Preferences

    private var mBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val mBluetoothSyncService: BluetoothSyncService? by lazy {
        if (mBluetoothAdapter != null) BluetoothSyncService(mHandler, mBluetoothAdapter!!) else null
    }
    private var trialsCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransmitBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        preferences = Preferences.getInstance(applicationContext)
        val viewModel: TransmitViewModel by viewModels {
            TransmitViewModelFactory(preferences)
        }
        actuator = when (preferences.wave) {
            getString(R.string.light) -> TorchActuator(
                binding.surfaceView.holder,
                preferences.shouldSaveRawData,
                getExternalFilesDir(null)?.absolutePath,
            )
            getString(R.string.vibration) -> VibrationActuator(
                applicationContext,
                preferences.shouldSaveRawData,
                getExternalFilesDir(null)?.absolutePath
            )
            else -> TODO("implement")
        }
        binding.viewModel = viewModel
        viewModel.isStarted.observe(this, {
            if (!it) {
                try {
                    this.coroutineContext.cancelChildren()
                } catch (e: CancellationException) { }
                actuator.dispose()
                // Reset the progress bar to zero.
                viewModel.publishProgress(0)
                // Reset trials to zero.
                trialsCounter = 0
                mBluetoothSyncService?.stop()
            } else {
                if (preferences.useBluetooth) {
                    // Starts bluetooth process, that will eventually lead to the transmission.
                    ensureBluetoothEnabled()
                } else {
                    startTransmission()
                }
            }
        })
        if (!hasPermissions()) {
            requestPermission()
        } else {
            onPermissionGranted()
        }
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    handleSendText(intent) // Handle text being sent
                }
            }
        }
        setContentView(binding.root)
    }

    private fun startTransmission(frequency: Int = getDefaultFrequency(preferences.wave)) {
        val data = binding.editTextInsertText.text.toString().toByteArray()
        launch(Dispatchers.IO) {
            actuator.initialise()
            actuator.transmit(data, frequency, binding.viewModel!!)
            if (preferences.useBluetooth) {
                mBluetoothSyncService?.write(END_TRANSMISSION.toByteArray())
            } else {
                withContext(Dispatchers.Main) {
                    binding.viewModel!!.stopTransmitter()
                }
            }
        }
    }


    private fun handleSendText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            binding.editTextInsertText.setText(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mBluetoothSyncService?.stop()
        binding.viewModel?.stopTransmitter()
        actuator.dispose()
        cancel()
    }

    private fun hasPermissions(): Boolean {
        return actuator.neededPermissions().all {
            (ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED)
        }
    }
    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            actuator.neededPermissions(),
            PERMISSIONS_REQUEST
        )
    }
    private fun onPermissionGranted() = binding.viewModel?.updatePermission(true)
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    log("Permission granted")
                    onPermissionGranted()
                } else {
                    log("Permission denied")
                    Toast.makeText(this, "Without the camera, you will not be able to use this transmitter!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(
                        this,
                        R.string.bluetooth_not_available,
                        Toast.LENGTH_LONG
                    ).show()
                    binding.viewModel?.stopTransmitter()
                } else {
                    showDeviceSelectionDialog()
                }
            }
            REQUEST_SELECT_BT_DEVICE -> {
                if (resultCode == RESULT_OK && data != null) {
                    // The user have chosen a device to connect with.
                    connectDevice(data)
                } else {
                    Toast.makeText(
                        this,
                        "You can switch to manual configuration if the device is not available through bluetooth",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.viewModel?.stopTransmitter()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }
    /**
     * Explicitly starts [DeviceListDialogActivity].
     */
    private fun showDeviceSelectionDialog() {
        val selectDeviceIntent = Intent(this, DeviceListDialogActivity::class.java)
        startActivityForResult(selectDeviceIntent, REQUEST_SELECT_BT_DEVICE)
    }
    /**
     * Enables the bluetooth when disabled, do nothing otherwise.
     */
    private fun ensureBluetoothEnabled() {
        if (mBluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            showDeviceSelectionDialog()
        }
    }
    /**
     * Establish connection with another device.
     * @param data  An [Intent] with [EXTRA_DEVICE_ADDRESS] extra.
     * @see [DeviceListDialogActivity]
     */
    private fun connectDevice(data: Intent) {
        // Get the device MAC address
        val extras = data.extras ?: return
        val address = extras.getString(EXTRA_DEVICE_ADDRESS)
        // Get the BluetoothDevice object
        val device: BluetoothDevice = mBluetoothAdapter!!.getRemoteDevice(address)
        // Attempt to connect to the device
        mBluetoothSyncService!!.connect(device)
    }
    /**
     * Parses a [Message] from the `server` application.
     */
    private fun parseResponse(message: Message) {
        val readBuf = message.obj as ByteArray
        // construct a string from the valid bytes in the buffer
        val readMessage = String(readBuf, 0, message.arg1)
        val chunks = readMessage.lines()
        val receiverStatus = chunks[0]
        val command = chunks[1]
        if (receiverStatus == ACK) {
            when (command) {
                START_TRANSMISSION -> {
                    // When the other device is ready start the transmission.
                    startTransmission(preferences.frequency)
                }
                END_TRANSMISSION -> {
                    if (trialsCounter == 0) {
                        Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show()
                        mBluetoothSyncService!!.write(END_TRIALS.toByteArray())
                        binding.viewModel?.stopTransmitter()
                    } else {
                        Toast.makeText(this, "Next", Toast.LENGTH_SHORT).show()
                        sendTransmissionInfoBT()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Receiver device experienced an error", Toast.LENGTH_SHORT).show()
            binding.viewModel!!.stopTransmitter()
        }
    }
    private fun sendTransmissionInfoBT() {
        mBluetoothSyncService!!.write(composeStartTransmissionMessage(
            preferences.wave,
            preferences.frequency,
            --trialsCounter,
            binding.editTextInsertText.text.toString()
        ).toByteArray())
    }
    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_READ -> {
                    parseResponse(msg)
                }
                MESSAGE_DEVICE_NAME -> {
                    // save the connected device's name
                    val name = msg.data.getString(DEVICE_NAME)
                    Toast.makeText(
                        this@TransmitActivity,
                        "Connected to $name",
                        Toast.LENGTH_SHORT
                    ).show()
                    trialsCounter = preferences.trials
                    sendTransmissionInfoBT()
                }
                MESSAGE_TOAST -> Toast.makeText(
                    this@TransmitActivity,
                    msg.data.getString(TOAST),
                    Toast.LENGTH_SHORT
                ).show()
                MESSAGE_DISCONNECTED -> {
                    binding.viewModel?.stopTransmitter()
                    Toast.makeText(
                        this@TransmitActivity,
                        msg.data.getString(TOAST),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val DEBUG_TAG = "[TransmitActivity]"
        const val PERMISSIONS_REQUEST = 1
        /**
         * @see BluetoothAdapter.ACTION_REQUEST_ENABLE
         */
        private const val REQUEST_ENABLE_BT: Int = 2
        /**
         * @see DeviceListDialogActivity
         */
        private const val REQUEST_SELECT_BT_DEVICE: Int = 3
        @JvmStatic
        fun log(message: String) = Log.e(DEBUG_TAG, message)
    }
}