package it.unipi.di.sam.overwave

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.Exception

/**
 * Message types sent from the BluetoothSyncService Handler
 */
const val MESSAGE_STATE_CHANGE = 1
const val MESSAGE_READ = 2
const val MESSAGE_WRITE = 3
const val MESSAGE_DEVICE_NAME = 4
const val MESSAGE_TOAST = 5

/**
 * Key names received from the BluetoothSyncService Handler
 */
const val DEVICE_NAME = "device_name"
const val TOAST = "toast"
/**
 * Name for the SDP record when creating server socket
 */
private const val SDP_RECORD_NAME = "BluetoothSyncService"
/**
 * Unique UUID for this application
 */
private val MY_UUID = UUID.fromString("7628e084-5a2e-4907-8cb3-07d6d1561f96")

/**
 * Constants that indicate the current connection (lifecycle) state
 * of the component.
 */
const val STATE_NONE = 0 // we're doing nothing
const val STATE_LISTEN = 1 // now listening for incoming connections
const val STATE_CONNECTING = 2 // now initiating an outgoing connection
const val STATE_CONNECTED = 3 // now connected to a remote device

/**
 * A Bluetooth connection manager. It uses
 *
 * @param handler used to send messages back to the UI.
 * @param adapter to be passed in the constructor to ensure that the caller has performed null check on it.
 */
class BluetoothSyncService(handler: Handler, adapter: BluetoothAdapter) {

    private val mAdapter: BluetoothAdapter = adapter
    /**
     * An Handler to send messages back to the UI Activity
     */
    private val mHandler: Handler = handler
    /**
     * A thread to accept new connections.
     * Used "server-side".
     * As advised in https://developer.android.com/guide/topics/connectivity/bluetooth#kotlin
     * we always prepare each device as a server so that so that each device has a server socket
     * open and listening for connections. In this way either device can initiate a connection
     * with the other and become the client.
     */
    private var mAcceptThread: AcceptThread? = null
    /**
     * A thread to connect as a client.
     */
    private var mConnectThread: ConnectThread? = null
    /**
     * A thread to exchange messages.
     */
    private var mConnectedThread: ConnectedThread? = null

    /**
     * Return the current connection state.
     * Use an Int with synchronized getter.
     */
    private var mState: Int = STATE_NONE
        get() = synchronized(this@BluetoothSyncService) {
            field
        }
        set(state) = synchronized(this@BluetoothSyncService) {
            field = state
        }

    /**
     * Update UI title according to the current state of the chat connection
     */
    @Synchronized
    private fun updateUserInterfaceTitle() {
        // Give the new state to the Handler so the UI Activity can update
        val stateMessage = mHandler.obtainMessage(MESSAGE_STATE_CHANGE)
        stateMessage.arg1 = mState
        stateMessage.sendToTarget()
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
    fun start() {
         // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (mAcceptThread == null) {
            mAcceptThread = AcceptThread()
            mAcceptThread!!.start()
        }

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * [BluetoothSyncService] is not started (start has not been yet called)
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
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device)
        mConnectThread!!.start()
        // Update UI title
        updateUserInterfaceTitle()
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
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {
            mAcceptThread!!.cancel()
            mAcceptThread = null
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket)
        mConnectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(DEVICE_NAME, device.name)
        msg.data = bundle
        mHandler.sendMessage(msg)
        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Stop all threads.
     */
    @Synchronized
    fun stop() {
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        if (mAcceptThread != null) {
            mAcceptThread!!.cancel()
            mAcceptThread = null
        }
        mState = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()
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
            r = mConnectedThread
        }
        // Perform the write asynchronously
        r?.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     * Update [mState] to [STATE_NONE]
     */
    private fun connectionFailed(error: Exception) {
        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Unable to connect device, ${error.message}")
        msg.data = bundle
        mHandler.sendMessage(msg)
        mState = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()
        // Start the service over to restart listening mode
        start()
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     * Update [mState] to [STATE_NONE]
     */
    private fun connectionLost() {
        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Device connection was lost")
        msg.data = bundle
        mHandler.sendMessage(msg)
        mState = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()
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
            mAdapter.listenUsingRfcommWithServiceRecord(SDP_RECORD_NAME, MY_UUID)
        }

        override fun run() {
            this@BluetoothSyncService.mState = STATE_LISTEN
            var socket: BluetoothSocket?

            // Listen to the server socket if we're not connected
            while (this@BluetoothSyncService.mState != STATE_CONNECTED) {
                socket = try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    break
                }

                // If a connection was accepted
                socket?.also {
                    synchronized(this@BluetoothSyncService) {
                        when (this@BluetoothSyncService.mState) {
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
            this@BluetoothSyncService.mState = STATE_CONNECTING
            // Cancel discovery because it otherwise slows down the connection.
            mAdapter.cancelDiscovery()
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket?.connect()
                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                // Reset the ConnectThread because we're done.
                synchronized(this@BluetoothSyncService) { mConnectThread = null }
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
            this@BluetoothSyncService.mState = STATE_CONNECTED
            var numBytes: Int
            // Keep listening to the InputStream while connected
            while (this@BluetoothSyncService.mState == STATE_CONNECTED) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    connectionLost()
                    break
                }
                // Send the obtained bytes to the UI activity.
                val readMsg = mHandler.obtainMessage(
                    MESSAGE_READ,
                    numBytes,
                    -1,
                    mmBuffer
                )
                readMsg.sendToTarget()
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
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget()
            } catch (e: IOException) {
                // Send a failure message back to the activity.
                val writeErrorMsg = mHandler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                mHandler.sendMessage(writeErrorMsg)
                return
            }
        }

        fun cancel() {
            try { mmSocket.close() }
            catch (e: IOException) { }
        }
    }
}