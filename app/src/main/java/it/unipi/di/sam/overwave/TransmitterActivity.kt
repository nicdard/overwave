package it.unipi.di.sam.overwave

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat

/**
 * @see BluetoothAdapter.ACTION_REQUEST_ENABLE
 */
private const val REQUEST_ENABLE_BT: Int = 1

/**
 * @see DeviceListDialogActivity
 */
private const val REQUEST_SELECT_BT_DEVICE: Int = 2

class TransmitterActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private var mBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    /**
     * The bluetooth service.
     */
    private val mBluetoothSyncService: BluetoothSyncService? by lazy {
        if (mBluetoothAdapter != null) BluetoothSyncService(mHandler, mBluetoothAdapter!!) else null
    }
    /**
     * Name of the connected device
     */
    private var mConnectedDeviceName: String? = null

    /**
     * UI elements.
     */
    private lateinit var mSwitchEnableBluetooth: SwitchCompat
    /** Those elements are enabled only when [mSwitchEnableBluetooth] isn't checked. */
    private lateinit var mLabelRadioGroupWaves: TextView
    private lateinit var mRadioGroupWaves: RadioGroup
    private lateinit var mRadioButtonVibration: AppCompatRadioButton
    private lateinit var mRadioButtonLight: AppCompatRadioButton
    private lateinit var mRadioButtonSound: AppCompatRadioButton
    private lateinit var mLabelEditTextSamplingRate: TextView
    private lateinit var mEditTextSamplingRate: EditText
    private lateinit var mLabelEditTextInsert: TextView
    private lateinit var mEditTextInsert: EditText
    private lateinit var mButtonSend: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.transmitter_activity)
        // Get UI elements.
        mSwitchEnableBluetooth = findViewById(R.id.switch_enable_bluetooth)
        mLabelRadioGroupWaves = findViewById(R.id.label_radio_group_waves)
        mRadioGroupWaves = findViewById(R.id.radio_group_waves)
        mRadioButtonVibration = findViewById(R.id.radio_button_vibration)
        mRadioButtonSound = findViewById(R.id.radio_button_sound)
        mRadioButtonLight = findViewById(R.id.radio_button_light)
        mLabelEditTextSamplingRate = findViewById(R.id.label_edit_text_sampling_rate)
        mEditTextSamplingRate = findViewById(R.id.edit_text_sampling_rate)
        mLabelEditTextInsert = findViewById(R.id.label_edit_text_insert_text)
        mEditTextInsert = findViewById(R.id.edit_text_insert_text)
        mButtonSend = findViewById(R.id.button_send)
        // Set listeners.
        mSwitchEnableBluetooth.setOnCheckedChangeListener(this)
        mButtonSend.setOnClickListener(this)

        if (mBluetoothAdapter == null) {
            // The device doesn't have bluetooth, notify the user.
            Toast
                .makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_LONG)
                .show()
            // Fallback to manual configuration.
            mSwitchEnableBluetooth.isChecked = false
            // Make sure the user will not try to use Bluetooth.
            mSwitchEnableBluetooth.isEnabled = false
        } else {
            onCheckedBluetoothSwitch()
        }
    }

    /**
     * @return true if [mSwitchEnableBluetooth] is enabled and checked.
     */
    private fun isCheckedBluetoothSwitch() = mSwitchEnableBluetooth.isEnabled && mSwitchEnableBluetooth.isChecked

    /**
     * Starts the Bluetooth management, if the device supports it and the user
     * checked [mSwitchEnableBluetooth].
     */
    private fun onCheckedBluetoothSwitch(isChecked: Boolean = isCheckedBluetoothSwitch()) {
        if (isChecked) { ensureBluetoothEnabled() }
    }

    /**
     * Enables the bluetooth when disabled, do nothing otherwise.
     */
    private fun ensureBluetoothEnabled() {
        if (mBluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    /**
     * Explicitly starts [DeviceListDialogActivity].
     */
    private fun showDeviceSelectionDialog() {
        val selectDeviceIntent = Intent(this, DeviceListDialogActivity::class.java)
        startActivityForResult(selectDeviceIntent, REQUEST_SELECT_BT_DEVICE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) = when (requestCode) {
        REQUEST_ENABLE_BT -> {
            if (resultCode == RESULT_CANCELED) {
                Toast
                    .makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_LONG)
                    .show()
                // We fallback to manual configuration because the user didn't give us the permission to use bluetooth
                // (i.e. we assume he wants to manually configure the receiver).
                mSwitchEnableBluetooth.isChecked = false
            } else {
                Toast.makeText(this, "Discoverable", Toast.LENGTH_LONG).show()
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
            }
        }
        else -> super.onActivityResult(requestCode, resultCode, data)
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
     * The Handler that gets information back from the BluetoothChatService
     */
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    STATE_CONNECTED -> {
                        actionBar?.subtitle = getString(
                            R.string.title_connected_to,
                            mConnectedDeviceName
                        )
                        // mConversationArrayAdapter.clear()
                    }
                    STATE_CONNECTING -> actionBar?.setSubtitle(R.string.title_connecting)
                    STATE_LISTEN, STATE_NONE -> actionBar?.setSubtitle(R.string.title_not_connected)
                }
                MESSAGE_WRITE -> {
                    val writeBuf = msg.obj as ByteArray
                    // construct a string from the buffer
                    val writeMessage = String(writeBuf)
                    Toast
                        .makeText(this@TransmitterActivity, writeMessage, Toast.LENGTH_SHORT)
                        .show()
                }
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)
                    Toast
                        .makeText(this@TransmitterActivity, readMessage, Toast.LENGTH_LONG)
                        .show()
                }
                MESSAGE_DEVICE_NAME -> {
                    // save the connected device's name
                    mConnectedDeviceName = msg.data.getString(DEVICE_NAME)
                    Toast.makeText(
                        this@TransmitterActivity,
                        "Connected to $mConnectedDeviceName",
                        Toast.LENGTH_SHORT
                    ).show()
                    // TODO send configurations.
                    mBluetoothSyncService!!.write("CIAO".toByteArray())
                }
                MESSAGE_TOAST -> {
                    Toast.makeText(this@TransmitterActivity, msg.data.getString(TOAST), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    /**
     * For performance.
     */
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        when (buttonView?.id) {
            R.id.switch_enable_bluetooth -> {
                onCheckedBluetoothSwitch(isChecked)
            }
        }
    }

    /**
     * Handles [mButtonSend] click, starts the transmission straight-away when manually configured,
     * ask the user to select a device when using bluetooth.
     */
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.button_send -> {
                if (isCheckedBluetoothSwitch()) {
                    showDeviceSelectionDialog()
                } else {
                    // Start straight-forward to transmit, assuming the other device is listening.
                    Toast
                        .makeText(this, "Starting transmission", Toast.LENGTH_LONG)
                        .show()
                    // TODO
                }
            }
        }
    }

    /**
     * Frees resources.
     */
    override fun onDestroy() {
        super.onDestroy()
        mBluetoothSyncService?.stop()
    }

    /**
     * Save the current configuration.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // TODO
    }
}
