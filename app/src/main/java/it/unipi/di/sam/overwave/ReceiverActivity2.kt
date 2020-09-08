package it.unipi.di.sam.overwave

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.*
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import it.unipi.di.sam.overwave.databinding.LdReceiverActivityBinding
import it.unipi.di.sam.overwave.transmissions.bluetooth.*
import it.unipi.di.sam.overwave.transmissions.receivers.Receiver
import java.lang.NumberFormatException

/**
 * @see BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
 */
private const val REQUEST_ENABLE_DISCOVERABLE_BT: Int = 1

class ReceiverActivity2 : AppCompatActivity() {

    /**
     * The bluetooth service.
     */
    private val mBluetoothSyncService: BluetoothSyncService? by lazy {
        val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) BluetoothSyncService(mHandler, adapter) else null
    }

    /**
     * Hold a reference to all the views
     */
    private val viewModel: ReceiverViewModel by viewModels<ReceiverViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: LdReceiverActivityBinding = DataBindingUtil.setContentView(this, R.layout.ld_receiver_activity)
        binding.receiverViewModel = viewModel
        binding.lifecycleOwner = this

        viewModel.isSwitchBluetoothChecked.observe(this, Observer {
            if (it == true) {
                ensureDiscoverable()
            }
        })
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
                    viewModel.switchToManualConfiguration()
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
     * True if the device is already discoverable.
     */
    private fun isDiscoverable(): Boolean = mBluetoothSyncService?.mAdapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     * Note: This request automatically enables the Bluetooth,
     *       so we can skip the enable request.
     */
    private fun ensureDiscoverable() {
        if (mBluetoothSyncService?.mAdapter != null
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
     * The Handler that gets information back from the [BluetoothService].
     *
     */
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)
                    Toast.makeText(this@ReceiverActivity2, readMessage, Toast.LENGTH_SHORT).show()
                    try {
                        val lines = readMessage.lineSequence()
                        when (lines.elementAt(0)) {
                            START_TRANSMISSION -> {
                                val configMap = lines
                                    .drop(1)
                                    .map { it.split(":") }
                                    .associate { it[0] to it[1] }
                                // Start receiver.
                                val wave = configMap[KEY_WAVE]
                                val rate = try { configMap[KEY_RATE]?.toInt() } catch (e: NumberFormatException) { null }
                                if (wave != null && rate != null) {
                                    viewModel.onWaveCheckedChanged(wave)
                                    viewModel.startReceiver(true)
                                    mBluetoothSyncService!!.write(composeResponse(ACK, START_TRANSMISSION))
                                } else {
                                    mBluetoothSyncService!!.write(composeResponse(NACK, START_TRANSMISSION))
                                }
                            }
                            END_TRANSMISSION -> {
                                viewModel.stopReceiver()
                                mBluetoothSyncService!!.write(composeResponse(ACK, END_TRANSMISSION))
                            }
                            else -> mBluetoothSyncService!!.write(composeResponse(NACK, lines.elementAt(0)))
                        }
                    } catch (e: Exception) {
                        mBluetoothSyncService!!.write((NACK + '\n' + e.message).toByteArray())
                    }
                }
                MESSAGE_DEVICE_NAME -> Toast.makeText(
                    this@ReceiverActivity2,
                    "Connected to ${msg.data.getString(DEVICE_NAME)}",
                    Toast.LENGTH_SHORT).show()
                MESSAGE_TOAST -> Toast.makeText(this@ReceiverActivity2, msg.data.getString(TOAST), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Frees resources.
     */
    override fun onDestroy() {
        super.onDestroy()
        mBluetoothSyncService?.stop()
        viewModel.stopReceiver()
    }
}