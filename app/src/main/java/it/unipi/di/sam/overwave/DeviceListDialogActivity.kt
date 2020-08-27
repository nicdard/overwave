package it.unipi.di.sam.overwave

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


const val EXTRA_DEVICE_ADDRESS = "device_address"

/**
 * This Activity appears as a dialog. It lists any paired devices and
 * devices detected in the area after discovery. When a device is chosen
 * by the user, the MAC address of the device is sent back to the parent
 * Activity in the result Intent.
 */
class DeviceListDialogActivity : AppCompatActivity(), OnItemClickListener, View.OnClickListener {

    private lateinit var mBluetoothAdapter: BluetoothAdapter
    /**
     * Newly discovered devices
     */
    private lateinit var mNewDevicesArrayAdapter: ArrayAdapter<String>

    /**
     * The BroadcastReceiver that listens for discovered devices and changes the title when
     * discovery is finished.
     */
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // When discovery finds a device
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.i("DeviceList", "Started discovery, state ${mBluetoothAdapter.state}")
                }
                BluetoothDevice.ACTION_FOUND -> {
                    // Get the BluetoothDevice object from the Intent
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    // If it's already paired, skip it, because it's been listed already
                    Log.i("DeviceList", device?.name ?: "Null" )
                    if (device != null && device.bondState != BluetoothDevice.BOND_BONDED) {
                        mNewDevicesArrayAdapter.add(
                            deviceToString(device)
                        )
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // When discovery is finished, change the Activity title
                    setTitle(R.string.select_device)
                    if (mNewDevicesArrayAdapter.count == 0) {
                        val noneFoundText = findViewById<TextView>(R.id.none_text_view)
                        noneFoundText.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup the window
        setContentView(R.layout.activity_device_list)

        // Set result CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        // Initialize the button to perform device discovery
        findViewById<Button>(R.id.button_scan).setOnClickListener(this)

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        val pairedDevicesArrayAdapter = ArrayAdapter<String>(this, R.layout.device_name)
        mNewDevicesArrayAdapter = ArrayAdapter(this, R.layout.device_name)

        // Find and set up the ListView for paired devices
        val pairedListView = findViewById<ListView>(R.id.paired_devices)
        pairedListView.adapter = pairedDevicesArrayAdapter
        pairedListView.onItemClickListener = this

        // Find and set up the ListView for newly discovered devices
        val newDevicesListView = findViewById<ListView>(R.id.new_devices)
        newDevicesListView.adapter = mNewDevicesArrayAdapter
        newDevicesListView.onItemClickListener = this

        // Register for broadcasts when a device is discovered
        val actionFoundFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(mReceiver, actionFoundFilter)

        // Register for broadcasts when discovery has finished
        val actionDiscoveryFinishedFilter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(mReceiver, actionDiscoveryFinishedFilter)

        val actionStartDiscovery = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        registerReceiver(mReceiver, actionStartDiscovery)

        // Get the local Bluetooth adapter
        val btAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null) {
            Toast.makeText(this@DeviceListDialogActivity, "Bluetooth not found", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            mBluetoothAdapter = btAdapter
        }

        // Get a set of currently paired devices
        val bondedDevices = mBluetoothAdapter.bondedDevices

        // If there are paired devices, add each one to the ArrayAdapter
        if (bondedDevices.size > 0) {
            findViewById<View>(R.id.title_paired_devices).visibility = View.VISIBLE
            pairedDevicesArrayAdapter.addAll(
                bondedDevices.map(this::deviceToString)
            )
        } else {
            val noDevices = resources.getText(R.string.none_paired).toString()
            pairedDevicesArrayAdapter.add(noDevices)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Make sure we're not doing discovery anymore
        mBluetoothAdapter.cancelDiscovery()
        // Unregister broadcast listeners
        unregisterReceiver(mReceiver)
    }

    /**
     * Converts [bluetoothDevice] to its string representation to be displayed
     * into a listView.
     */
    private fun deviceToString(bluetoothDevice: BluetoothDevice): String = """
        ${bluetoothDevice.name}
        ${bluetoothDevice.address}
        """.trimIndent()

    /**
     * Start device discover with the BluetoothAdapter.
     */
    private fun doDiscovery() {
        // This line of code is very important. In Android >= 6.0 you have to ask for the runtime
        // permission as well in order for the discovery to get the devices ids. If you don't do
        // this, the discovery won't find any device.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                listOf(Manifest.permission.ACCESS_COARSE_LOCATION).toTypedArray(),
                1);
        }

        // Indicate scanning in the title
        setTitle(R.string.scanning)

        // Turn on sub-title for new devices
        findViewById<View>(R.id.title_new_devices).visibility = View.VISIBLE

        // If we're already discovering, stop it
        if (mBluetoothAdapter.isDiscovering) {
            mBluetoothAdapter.cancelDiscovery()
        }

        Log.i("DeviceList", "start")
        // Request discover from BluetoothAdapter
        mBluetoothAdapter.startDiscovery()
    }

    /**
     * The on-click listener for all devices in the ListViews.
     * We put this in the activity for performance reason.
     */
    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        mBluetoothAdapter.cancelDiscovery()

        // Get the device MAC address, which is the last 17 chars in the View
        val info = (view as TextView).text.toString()
        val address = info.substring(info.length - 17)

        // Create the result Intent and include the MAC address
        val intent = Intent()
        intent.putExtra(EXTRA_DEVICE_ADDRESS, address)

        // Set result and finish this Activity
        setResult(RESULT_OK, intent)
        finish()
    }

    /**
     * The onClick listener for the scanButton.
     * We put this in the activity for performance reason.
     */
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.button_scan -> {
                doDiscovery()
                v.visibility = View.GONE
            }
        }
    }
}