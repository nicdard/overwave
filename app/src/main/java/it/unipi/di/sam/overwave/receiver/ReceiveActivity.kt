package it.unipi.di.sam.overwave.receiver

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import it.unipi.di.sam.overwave.BaseMenuActivity
import it.unipi.di.sam.overwave.R
import it.unipi.di.sam.overwave.bluetooth.*
import it.unipi.di.sam.overwave.database.TransmissionDatabase
import it.unipi.di.sam.overwave.databinding.ActivityReceiveBinding
import it.unipi.di.sam.overwave.sensors.ISensor
import it.unipi.di.sam.overwave.sensors.LuminositySensor
import it.unipi.di.sam.overwave.utils.DEFAULT_FREQUENCY
import it.unipi.di.sam.overwave.utils.Preferences
import kotlinx.coroutines.*

class ReceiveActivity : BaseMenuActivity(), CoroutineScope by MainScope() {

    private lateinit var binding: ActivityReceiveBinding
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothSyncService: BluetoothSyncService? by lazy {
        if (bluetoothAdapter != null) BluetoothSyncService(mHandler, bluetoothAdapter) else null
    }
    private lateinit var preferences: Preferences
    private var storageDir: String? = null

    private lateinit var sensor: ISensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        preferences = Preferences(applicationContext)
        val database = TransmissionDatabase.getInstance(application).transmissionDatabaseDao
        val viewModel: ReceiveViewModel by viewModels {
            ReceiveViewModelFactory(database)
        }
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        sensor = when (preferences.wave) {
            "light" -> LuminositySensor(application.getSystemService(SENSOR_SERVICE) as SensorManager)
            else -> TODO("implement ${preferences.wave}")
        }
        storageDir = getExternalFilesDir(null)?.absolutePath
        viewModel.isReceiving.observe(this, {
            if (it) {
                if (preferences.useBluetooth) {
                    ensureDiscoverable()
                } else {
                    processStartedTransmission()
                }
            } else {
                if (preferences.useBluetooth) {
                    bluetoothSyncService?.stop()
                } else {
                    launch { processEndedTransmission() }
                }
            }
        })
        val adapter = TransmissionDataAdapter()
        binding.recyclerView.let {
            it.setHasFixedSize(true)
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
        }
        viewModel.transmissions.observe(this, {
            it?.let {
                adapter.submitList(it)
            }
        })
        setContentView(binding.root)
    }

    private fun processStartedTransmission(
        wave: String = preferences.wave,
        frequency: Int = preferences.frequency,
        sentText: String? = null
    ) {
        sensor.activate()
        binding.viewModel!!.startReceive(wave, frequency)
    }

    private suspend fun processEndedTransmission(frequency: Int = preferences.frequency) {
        sensor.stop()
        if (preferences.shouldSaveRawData) {
            sensor.writeRawData(application.getExternalFilesDir(null)!!.absolutePath)
        }
        val text = sensor.decodeSignal(frequency)
        binding.viewModel!!.stopReceive(text)
    }

    override fun onDestroy() {
        cancel()
        bluetoothSyncService?.stop()
        // binding.viewModel?.stopReceive()
        super.onDestroy()
    }

    /**
     * True if the device is already discoverable.
     */
    private fun isDiscoverable(): Boolean =
        bluetoothAdapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     * Note: This request automatically enables the Bluetooth,
     *       so we can skip the enable request.
     */
    private fun ensureDiscoverable() {
        if (/* binding.viewModel!!. */preferences.useBluetooth
            // We don't want to bother the user more than needed.
            && !isDiscoverable()
        ) {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            startActivityForResult(discoverableIntent, REQUEST_ENABLE_DISCOVERABLE_BT)
        } else {
            // The device is already discoverable, must be sure that the bluetooth server is listening for connections!
            if (/* binding.viewModel!!.*/preferences.useBluetooth && !bluetoothSyncService!!.isStarted()) {
                // Start the BluetoothServer.
                bluetoothSyncService!!.start()
            }
        }
    }

    /**
     * On bluetooth discoverable state enabled starts [bluetoothSyncService] so we are ready
     * to accept incoming requests.
     * If the user did not granted bluetooth permission, automatically switch to manual
     * configuration interface.
     * @see [REQUEST_ENABLE_DISCOVERABLE_BT].
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        when (requestCode) {
            REQUEST_ENABLE_DISCOVERABLE_BT -> {
                if (resultCode == RESULT_CANCELED) {
                    Toast
                        .makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_LONG)
                        .show()
                    // binding.viewModel!!.stopReceive()
                } else {
                    if (!bluetoothSyncService!!.isStarted()) {
                        // Start the BluetoothServer.
                        bluetoothSyncService!!.start()
                    }
                    Toast.makeText(
                        this,
                        "Bluetooth enabled!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }

    /**
     * The Handler that gets information back from the [BluetoothSyncService].
     */
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        var frequency: Int = DEFAULT_FREQUENCY
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)
                    try {
                        log("Received $readMessage")
                        val lines = readMessage.lineSequence()
                        when (lines.elementAt(0)) {
                            START_TRANSMISSION -> {
                                val configMap = lines
                                    .drop(1)
                                    .map { it.split(":") }
                                    .associate { it[0] to it[1] }
                                // Start receiver.
                                val wave = configMap[KEY_WAVE]
                                // Used for data gathering purposes.
                                val text = configMap[KEY_TEXT]
                                frequency = try {
                                    configMap[KEY_FREQUENCY]!!.toInt()
                                } catch (e: Exception) {
                                    DEFAULT_FREQUENCY
                                }
                                if (wave != null) {
                                    processStartedTransmission(wave, frequency)
                                    bluetoothSyncService!!.write(composeResponse(ACK, START_TRANSMISSION))
                                } else {
                                    bluetoothSyncService!!.write(composeResponse(NACK, START_TRANSMISSION))
                                }
                            }
                            END_TRANSMISSION -> {
                                launch {
                                    processEndedTransmission(frequency)
                                    bluetoothSyncService!!.write(composeResponse(ACK, END_TRANSMISSION))
                                }
                            }
                            END_TRIALS -> {
                                // binding.viewModel?.stopReceive()
                                launch {
                                    // processEndedTransmission(frequency)
                                    bluetoothSyncService!!.stop()
                                }
                            }
                            else -> bluetoothSyncService!!.write(composeResponse(NACK, lines.elementAt(0)))
                        }
                    } catch (e: Exception) {
                        bluetoothSyncService!!.write((NACK + '\n' + e.message).toByteArray())
                        bluetoothSyncService?.stop()
                    }
                }
                MESSAGE_DEVICE_NAME -> {
                    log("Received ${msg.data.getString(DEVICE_NAME)}")
                    // save the connected device's name
                    Toast.makeText(
                        this@ReceiveActivity,
                        "Connected to ${msg.data.getString(DEVICE_NAME)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MESSAGE_TOAST -> {
                    log("Received ${msg.data.getString(TOAST)}")
                    Toast.makeText(
                        this@ReceiveActivity,
                        msg.data.getString(TOAST),
                        Toast.LENGTH_LONG
                    ).show()
                }
                MESSAGE_DISCONNECTED -> {
                    log("Received disconnected")
                    // binding.viewModel?.stopReceive()
                    Toast.makeText(
                        this@ReceiveActivity,
                        msg.data.getString(TOAST),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val DEBUG_TAG = "[ReceiveActivity]"

        /**
         * @see BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
         */
        const val REQUEST_ENABLE_DISCOVERABLE_BT: Int = 1

        @JvmStatic
        fun log(message: String) = Log.e(DEBUG_TAG, message)
    }

    /*
    in onCreate
     // binding.viewModel = viewModel
        // binding.lifecycleOwner = this
        /* viewModel.isStopButtonVisible.observe(this, {
            if (it) {
                try {
                    this.coroutineContext.cancelChildren()
                } catch (e: CancellationException) {
                }
                if (preferences.useBluetooth) {
                    // The sensor will be started by the transmitter.
                    log("ensure discoverable called, device ${isDiscoverable()}")
                    ensureDiscoverable()
                } else {
                    sensor.activate()
                }
            } else {
                // launch { stop() }
                // mBluetoothSyncService?.stop()
                // bluetoothAdapter?.disable()
                if (!bluetoothOngoing) {
                    mBluetoothSyncService?.stop()
                    bluetoothAdapter?.disable()
                }
            }
        })
        */
     */

    /*
  private suspend fun stop(frequency: Int = /*binding.viewModel!!.*/preferences.frequency) {
      sensor.stop()
      if (/*binding.viewModel!!.*/preferences.shouldSaveRawData) {
          withContext(Dispatchers.IO) {
              sensor.writeRawData(application.getExternalFilesDir(null)!!.absolutePath)
          }
      }
      val text = sensor.decodeSignal(frequency)
      withContext(Dispatchers.Main) {
          Toast.makeText(this@ReceiveActivity, text, Toast.LENGTH_SHORT).show()
      }
      // binding.viewModel!!.receivedText.value = text
      if (/*binding.viewModel!!.*/preferences.useBluetooth) {
          bluetoothSyncService?.write(
              composeResponse(
                  ACK,
                  END_TRANSMISSION
              )
          )
      }
  }
  */
}