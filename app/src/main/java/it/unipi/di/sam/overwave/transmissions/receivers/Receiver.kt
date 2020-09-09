package it.unipi.di.sam.overwave.transmissions.receivers

/**
 * A [Receiver] uses one or more sensors to retrieve data
 * emitted through a physical system by another device.
 */
interface Receiver {

    /**
     * Listens for data transmission and retrieves them.
     */
    fun start()

    /**
     * Stops listening for data.
     */
    fun stop()

    fun consume(): String
}

typealias OnReceived = (message: String) -> Unit
