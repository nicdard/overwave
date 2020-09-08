package it.unipi.di.sam.overwave.transmissions.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.Exception

/**
 * Message types sent from the BluetoothSyncService Handler
 */
/*
const val MESSAGE_STATE_CHANGE = 1
const val MESSAGE_READ = 2
const val MESSAGE_WRITE = 3
const val MESSAGE_DEVICE_NAME = 4
const val MESSAGE_TOAST = 5
*/

/**
 * Key names received from the BluetoothSyncService Handler
 */
/*
const val DEVICE_NAME = "device_name"
const val TOAST = "toast"
*/

/**
 * Name for the SDP record when creating server socket
 */
private const val SDP_RECORD_NAME = "BluetoothService"
/**
 * Unique UUID for this application
 */
private val MY_UUID = UUID.fromString("7628e084-5a2e-4907-8cb3-07d6d1561f96")

data class BluetoothContext(
    val adapter: BluetoothAdapter,
    var serverSocket: BluetoothServerSocket?,
    var socket: BluetoothSocket?
)

suspend fun CoroutineScope.server(
    context: BluetoothContext
) = launch {
    try {
        accept(context)
    } finally {
        try {
            context.socket?.close()
        } catch (e: Exception) {}

    }
}






suspend fun accept(context: BluetoothContext) = withContext(Dispatchers.IO) {
    val serverSocket = context.adapter.listenUsingRfcommWithServiceRecord(SDP_RECORD_NAME, MY_UUID)
    context.serverSocket = serverSocket
    val socket = serverSocket.accept()
    socket
}

/**
 * Constants that indicate the current connection (lifecycle) state
 * of the component.
 */
/*
const val STATE_NONE = 0 // we're doing nothing
const val STATE_LISTEN = 1 // now listening for incoming connections
const val STATE_CONNECTING = 2 // now initiating an outgoing connection
const val STATE_CONNECTED = 3 // now connected to a remote device
*/

data class BluetoothMessage(val operation: Int, val data: String?)

private val CONNECTION_LOST_MESSAGE = BluetoothMessage(MESSAGE_TOAST, "Device connection was lost")

typealias OnBluetoothMessage = (BluetoothMessage) -> Unit
/**
 * Sobstitute the handler
 */
class BluetoothService (
    private val adapter: BluetoothAdapter,
    onMessage: OnBluetoothMessage
) {

    private var onMessage: OnBluetoothMessage? = onMessage
    /**
     * A thread to accept new connections.
     * Used "server-side".
     * As advised in https://developer.android.com/guide/topics/connectivity/bluetooth#kotlin
     * we always prepare each device as a server so that so that each device has a server socket
     * open and listening for connections. In this way either device can initiate a connection
     * with the other and become the client.
     */
    private var acceptThread: AcceptThread? = null
    /**
     * A thread to connect as a client.
     */
    private var connectThread: ConnectThread? = null
    /**
     * A thread to exchange messages.
     */
    private var connectedThread: ConnectedThread? = null

    /**
     * Return the current connection state.
     * Use an Int with synchronized getter.
     */
    var mState: Int = STATE_NONE
        get() = synchronized(this@BluetoothService) {
            field
        }
        private set(state) = synchronized(this@BluetoothService) {
            field = state
        }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
    fun start() {
        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread!!.start()
        }
    }

    /**
     * [BluetoothService] is not started (start has not been yet called)
     * only in the [STATE_NONE] state.
     */
    @Synchronized
    fun isStarted() = mState != STATE_NONE

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    @Synchronized
    fun connect(device: BluetoothDevice) {
        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread!!.cancel()
                connectThread = null
            }
        }
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        // Start the thread to connect with the given device
        connectThread = ConnectThread(device)
        connectThread!!.start()
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection.
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }
        // Cancel the accept thread because we only want to connect to one device
        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = ConnectedThread(socket)
        connectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg = BluetoothMessage(MESSAGE_DEVICE_NAME, device.name)
        onMessage!!(msg)
    }

    /**
     * Stop all threads.
     */
    @Synchronized
    fun stop() {
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }
        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }
        mState = STATE_NONE
    }

    /**
     * Write to the ConnectedThread asynchronously.
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray) {
        // Create temporary object
        var r: ConnectedThread?
        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState != STATE_CONNECTED) return
            r = connectedThread
        }
        // Perform the write asynchronously
        r?.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     * Update [mState] to [STATE_NONE]
     */
    private fun connectionFailed(error: Exception) = onError(BluetoothMessage(MESSAGE_TOAST, "Unable to connect device, ${error.message}"))


    /**
     * Indicate that the connection was lost and notify the UI Activity.
     * Update [mState] to [STATE_NONE]
     */
    private fun connectionLost() = onError(CONNECTION_LOST_MESSAGE)

    /**
     * Update [mState] to [STATE_NONE].
     * Restart the service and notifies the Listener.
     */
    private fun onError(message: BluetoothMessage) {
        onMessage!!(message)
        mState = STATE_NONE
        // Start the service over to restart listening mode
        start()
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            adapter.listenUsingRfcommWithServiceRecord(SDP_RECORD_NAME, MY_UUID)
        }

        override fun run() {
            this@BluetoothService.mState = STATE_LISTEN
            var socket: BluetoothSocket?

            // Listen to the server socket if we're not connected
            while (this@BluetoothService.mState != STATE_CONNECTED) {
                socket = try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    break
                }

                // If a connection was accepted
                socket?.also {
                    synchronized(this@BluetoothService) {
                        when (this@BluetoothService.mState) {
                            STATE_LISTEN, STATE_CONNECTING ->
                                // Situation normal. Start the connected thread.
                                connected(it, it.remoteDevice)
                            STATE_NONE, STATE_CONNECTED ->
                                // Either not ready or already connected. Terminate new socket.
                                try { it.close() } catch (e: IOException) { }
                        }
                    }
                }
            }
        }

        fun cancel() {
            try { mmServerSocket!!.close() }
            catch (e: IOException) { }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            mmDevice.createRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            this@BluetoothService.mState = STATE_CONNECTING
            // Cancel discovery because it otherwise slows down the connection.
            adapter.cancelDiscovery()
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket?.connect()
                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                // Reset the ConnectThread because we're done.
                synchronized(this@BluetoothService) { connectThread = null }
                // Start the connected thread
                connected(mmSocket!!, mmDevice)
            } catch (e: Exception) {
                // Close it and ignores any error.
                cancel()
                connectionFailed(e)
            }
        }

        fun cancel() {
            try { mmSocket?.close() }
            catch (e: IOException) { }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        override fun run() {
            this@BluetoothService.mState = STATE_CONNECTED
            var numBytes: Int
            // Keep listening to the InputStream while connected
            while (this@BluetoothService.mState == STATE_CONNECTED) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    connectionLost()
                    break
                }
                // Send the obtained bytes to the UI activity.
                onMessage?.invoke(BluetoothMessage(MESSAGE_READ, String(mmBuffer, 0, numBytes)))
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        fun write(buffer: ByteArray) {
            try {
                mmOutStream.write(buffer)
            } catch (e: IOException) { }
        }

        fun cancel() {
            try { mmSocket.close() }
            catch (e: IOException) { }
        }
    }
}