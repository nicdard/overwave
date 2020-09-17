package it.unipi.di.sam.overwave.transmissions.transmitters

import android.content.Context

/**
 * A [Transmitter] uses an actuator to transmit some bytes of data
 * to another device through a physical system, thus creating
 * a one-way connection.
 *
 * Depending on the physical properties exploited by the transmitter,
 * the message can be received only by one device at a time or
 * by multiple listeners.
 *
 * It is useful to exchange little piece of information without
 * an internet connection, like in a multi-factor authentication
 * process where the physical nearness is one of the factor to be
 * considered.
 */
interface Transmitter {

    /**
     * Transmits [data] using [context] to retrieve the actuator.
     * Use [frequency] to suggest a desired transmission-frequency.
     *
     * Throws an IllegalStateException if the required actuator is not
     * available through [context].
     */
    suspend fun transmit(context: Context, data: ByteArray, frequency: Int)

    /**
     * Returns the suggested frequency to be used to send messages through
     * this [Transmitter] in millisecond.
     */
    fun getDefaultFrequency(): Int

    /**
     * Returns true if and only if [context] provides the minimum hardware support to
     * use this [Transmitter].
     */
    fun hasHardwareSupport(context: Context): Boolean
}


const val INITIAL_SEQUENCE     = "01"
const val FINAL_SEQUENCE       = "10"

fun dataToBinaryString(data: ByteArray) = INITIAL_SEQUENCE + data.joinToString(separator = "") {
    // Get the binary string representation of the byte
    Integer.toBinaryString(it.toInt())
        // 8-bit 0-padded string.
        .padStart(8, '0')
        // 16-bit: encode each bit in a sequence of 2 equal bits
        .replace("0", "00").replace("1", "11")
} + FINAL_SEQUENCE