package it.unipi.di.sam.overwave

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
import it.unipi.di.sam.overwave.transmissions.bluetooth.*
import kotlinx.coroutines.*

/**
 * @see BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
 */
private const val REQUEST_ENABLE_DISCOVERABLE_BT: Int = 1

class ReceiverActivity : AppCompatActivity(), CoroutineScope by MainScope(),
    CompoundButton.OnCheckedChangeListener, View.OnClickListener
{

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
     * The receiver instance to use for getting and decoding the signal.
     */

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
    private lateinit var mButtonReceive: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.receiver_activity)
        // Get UI elements.
        mSwitchEnableBluetooth = findViewById(R.id.switch_enable_bluetooth)
        mLabelRadioGroupWaves = findViewById(R.id.label_radio_group_waves)
        mRadioGroupWaves = findViewById(R.id.radio_group_waves)
        mRadioButtonVibration = findViewById(R.id.radio_button_vibration)
        mRadioButtonSound = findViewById(R.id.radio_button_sound)
        mRadioButtonLight = findViewById(R.id.radio_button_light)
        mLabelEditTextSamplingRate = findViewById(R.id.label_edit_text_sampling_rate)
        mEditTextSamplingRate = findViewById(R.id.edit_text_sampling_rate)
        mButtonReceive = findViewById(R.id.button_receive)
        // Set listeners.
        mSwitchEnableBluetooth.setOnCheckedChangeListener(this)
        mButtonReceive.setOnClickListener(this)

        savedInstanceState?.run {
            mEditTextSamplingRate.setText(getInt(KEY_RATE).toString())
            mSwitchEnableBluetooth.isEnabled = getBoolean(KEY_BT_SUPPORT)
            mSwitchEnableBluetooth.isChecked = getBoolean(KEY_BLUETOOTH)
            mRadioGroupWaves.check(getInt(KEY_WAVE))
        }

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
            // Initialise the UI with accordingly to the predefined bluetooth state.
            setSwitchDependentUIState()
            onCheckedBluetoothSwitch()
        }
    }

    /**
     * On bluetooth discoverable state enabled starts [mBluetoothSyncService] so we are ready
     * to accept incoming requests.
     * If the user did not granted bluetooth permission, automatically switch to manual
     * configuration interface.
     * @see [REQUEST_ENABLE_DISCOVERABLE_BT].
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_DISCOVERABLE_BT -> {
                if (resultCode == RESULT_CANCELED) {
                    Toast
                        .makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_LONG)
                        .show()
                    // We fallback to manual configuration because the user didn't give us the permission to use bluetooth
                    // (i.e. we assume he wants to manually configure the receiver).
                    mSwitchEnableBluetooth.isChecked = false
                } else {
                    if (!mBluetoothSyncService!!.isStarted()) {
                        // Start the BluetoothServer.
                        mBluetoothSyncService!!.start()
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     * Note: This request automatically enables the Bluetooth,
     *       so we can skip the enable request.
     */
    private fun ensureDiscoverable() {
        if (mBluetoothAdapter != null
            // We don't want to bother the user more than needed.
            && !isDiscoverable()
        ) {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            startActivityForResult(discoverableIntent, REQUEST_ENABLE_DISCOVERABLE_BT)
        } else {
            // The device is already discoverable, must be sure that the bluetooth server is listening for connections!
            mBluetoothSyncService?.start()
        }
    }

    /**
     * True if the device is already discoverable.
     */
    private fun isDiscoverable(): Boolean = mBluetoothAdapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE

    /**
     * @return true if [mSwitchEnableBluetooth] is enabled and checked.
     */
    private fun isCheckedBluetoothSwitch() = mSwitchEnableBluetooth.isEnabled && mSwitchEnableBluetooth.isChecked

    /**
     * Starts the Bluetooth management, if the device supports it and the user
     * checked [mSwitchEnableBluetooth].
     */
    private fun onCheckedBluetoothSwitch(isChecked: Boolean = isCheckedBluetoothSwitch()) {
        if (isChecked) {
            // We want also to be discoverable in order to be found by nearby devices.
            ensureDiscoverable()
        }
    }

    /**
     * Enable/disable dependent ui elements according to the current status of [mSwitchEnableBluetooth].
     */
    private fun setSwitchDependentUIState() {
        val isManual = !isCheckedBluetoothSwitch()
        mLabelRadioGroupWaves.isEnabled = isManual
        mRadioButtonLight.isEnabled = isManual
        mRadioButtonVibration.isEnabled = isManual
        mRadioButtonSound.isEnabled = isManual
        mLabelEditTextSamplingRate.isEnabled = isManual
        mEditTextSamplingRate.isEnabled = isManual
        mButtonReceive.isEnabled = isManual
    }

    /**
     * The Handler that gets information back from the [BluetoothSyncService].
     *
     */
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    // TODO
                    STATE_CONNECTED -> {
                        actionBar?.subtitle = getString(
                            R.string.title_connected_to,
                            mConnectedDeviceName
                        )
                    }
                    STATE_CONNECTING -> actionBar?.setSubtitle(R.string.title_connecting)
                    STATE_LISTEN, STATE_NONE -> actionBar?.setSubtitle(R.string.title_not_connected)
                }
                MESSAGE_WRITE -> {
                    val writeBuf = msg.obj as ByteArray
                    // construct a string from the buffer
                    val writeMessage = String(writeBuf)
                    Toast
                        .makeText(this@ReceiverActivity, writeMessage, Toast.LENGTH_SHORT)
                        .show()
                }
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)
                    Toast
                        .makeText(this@ReceiverActivity, readMessage, Toast.LENGTH_SHORT)
                        .show()
                    try {
                        val lines = readMessage.lineSequence()
                        when (lines.elementAt(0)) {
                            START_TRANSMISSION -> {
                                val configMap = lines
                                    .drop(1)
                                    .map { it.split(":") }
                                    .associate { it[0] to it[1] }
                                // Start receiver.
                            }
                        }
                        // mBluetoothSyncService!!.write(ACK.toByteArray())
                    } catch (e: Exception) {
                        // mBluetoothSyncService!!.write((NACK + e.message).toByteArray())
                    }
                    // TODO start receiving asyncTask
                    // mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage)
                }
                MESSAGE_DEVICE_NAME -> {
                    // save the connected device's name
                    mConnectedDeviceName = msg.data.getString(DEVICE_NAME)
                    Toast.makeText(
                        this@ReceiverActivity,
                        "Connected to $mConnectedDeviceName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MESSAGE_TOAST -> {
                    Toast.makeText(this@ReceiverActivity, msg.data.getString(TOAST), Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    /**
     * Handles [mButtonReceive] click to start manually the receiver.
     */
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.button_receive -> {
                Toast.makeText(this, "TODO, clicked received", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Frees resources.
     */
    override fun onDestroy() {
        super.onDestroy()
        mBluetoothSyncService?.stop()
        // Cancel MainScope().
        cancel()
    }

    /**
     * Manages [mSwitchEnableBluetooth] state changes.
     */
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        when (buttonView?.id) {
            R.id.switch_enable_bluetooth -> {
                setSwitchDependentUIState()
                onCheckedBluetoothSwitch(isChecked)
            }
        }
    }

    /**
     * Save the current configuration.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_WAVE, mRadioGroupWaves.checkedRadioButtonId)
        outState.putBoolean(KEY_BLUETOOTH, mSwitchEnableBluetooth.isChecked)
        outState.putBoolean(KEY_BT_SUPPORT, mSwitchEnableBluetooth.isEnabled)
        outState.putInt(KEY_RATE, mEditTextSamplingRate.text?.toString()?.toInt() ?: 0)
    }
}