package it.unipi.di.sam.overwave

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import it.unipi.di.sam.overwave.transmitters.Transmitter
import it.unipi.di.sam.overwave.transmitters.VibrationTransmitter
import java.util.*

private const val KEY_TEXT = "text_key"
private const val KEY_TRANSMITTER_ID = "transmitter_id_key"
private const val KEY_FREQUENCY = "frequency_key"
private const val KEY_AUTO_PAIR_ENABLED = "auto_pair_enabled_key"

private const val REQUEST_ENABLE_BT = 1

class TransmissionActivity : AppCompatActivity(), View.OnClickListener, DialogInterface.OnClickListener {

    // ====== Bluetooth management
    // "random" unique identifier
    private val BT_MODULE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    // Receiver for ACTION_FOUND
    private lateinit var receiver: BroadcastReceiver
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var btArrayAdapter: ArrayAdapter<String>? = null

    private var transmitter: Transmitter? = null

    private lateinit var textToTransmit: String

    // GUI elements
    private lateinit var autoPairingSwitcher: Switch
    private lateinit var transmitterRadioGroup: RadioGroup
    private lateinit var frequencyEditText: TextInputEditText
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.transmitter_activity)
        autoPairingSwitcher = findViewById(R.id.use_bluetooth_switcher)
        startButton = findViewById(R.id.start_transfer_button)
        transmitterRadioGroup = findViewById(R.id.transmitter_radio_group)
        frequencyEditText = findViewById(R.id.frequency_input_edit_text)

        textToTransmit =
            intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: savedInstanceState?.run { getString(KEY_TEXT) } ?: TODO()
                    // ?: ""
        // Restore the UI state.
        savedInstanceState?.run {
            frequencyEditText.setText(getInt(KEY_FREQUENCY).toString())
            transmitterRadioGroup.check(getInt(KEY_TRANSMITTER_ID))
            autoPairingSwitcher.isChecked = getBoolean(KEY_AUTO_PAIR_ENABLED)
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            // Disable auto pairing
            autoPairingSwitcher.isChecked = false
            autoPairingSwitcher.isEnabled = false
            // TODO make this a textView under the Switcher
            Toast.makeText(
                this,
                "Bluetooth not found, you should manually configure and start the receiver device before pressing START",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // create the receiver
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            // Discovery has found a device. Get the BluetoothDevice
                            // object and its info from the Intent.
                            val device: BluetoothDevice =
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                            btArrayAdapter?.add("${device.name ?: ""}\n${device.address}")
                            btArrayAdapter?.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.start_transfer_button -> {
                setTransmitter()
                // Pair devices if auto-pairing is on
                if (autoPairingSwitcher.isEnabled && autoPairingSwitcher.isChecked) {
                    val discoverableIntent: Intent =
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                        }
                    startActivityForResult(discoverableIntent, REQUEST_ENABLE_BT)
                } else {
                    // Assume the receiver is already listening using the right receiver,
                    // start the connection straight-away
                    transmitter?.transmit(this, textToTransmit.toByteArray().toList(), 60)
                    TODO()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == RESULT_OK) {
                    // Register for broadcasts when a device is discovered.
                    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                    registerReceiver(receiver, filter)
                    // We have bluetooth and we are discoverable
                    bluetoothAdapter!!.startDiscovery()
                    val pairedDevices = bluetoothAdapter?.bondedDevices
                    if (pairedDevices != null && pairedDevices.isNotEmpty()) {
                        btArrayAdapter = ArrayAdapter<String>(
                            this,
                            android.R.layout.select_dialog_singlechoice,
                            pairedDevices.map { "${it.name ?: ""}\n${it.address}" }
                        )
                        val dialogBuilder = AlertDialog.Builder(this)
                        dialogBuilder
                            .setTitle("Pick a receiver")
                            .setNegativeButton("Cancel", this)
                            .setAdapter(btArrayAdapter, this)
                            .show()
                    } else {
                        TODO()
                    }
                } else {
                    // Maybe do something else
                    TODO()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }


    override fun onClick(dialog: DialogInterface?, which: Int) {
        when (which) {
            DialogInterface.BUTTON_NEGATIVE -> {
                Toast.makeText(this, "Unable to connect", Toast.LENGTH_SHORT).show()
                this.finish()
            }
            else -> {
                val address = btArrayAdapter!!.getItem(which)!!.split("")[1]

            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TEXT, textToTransmit)
        outState.putInt(KEY_TRANSMITTER_ID, transmitterRadioGroup.checkedRadioButtonId)
        outState.putInt(KEY_FREQUENCY, getFrequency())
        outState.putBoolean(KEY_AUTO_PAIR_ENABLED, autoPairingSwitcher.isChecked)

    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the ACTION_FOUND receiver.
        if (bluetoothAdapter != null) {
            unregisterReceiver(receiver)
        }
    }

    private fun getFrequency(): Int = try {
        frequencyEditText.text.toString().toInt()
    } catch (e: NumberFormatException) {
        60
    }

    /**
     * Set the transmitter accordingly to the user choice.
     */
    private fun setTransmitter() {
        when (transmitterRadioGroup.checkedRadioButtonId) {
            R.id.vibration_button -> transmitter = VibrationTransmitter
            else -> TODO()
        }
    }
}