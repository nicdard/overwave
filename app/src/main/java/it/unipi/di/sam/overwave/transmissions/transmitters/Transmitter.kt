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
internal interface Transmitter {

    /**
     * Transmits [data] using [context] to retrieve the actuator.
     * Use [frequency] to suggest a desired transmission-frequency.
     *
     * Throws an IllegalStateException if the required actuator is not
     * available through [context].
     */
    suspend fun transmit(context: Context, data: ByteArray, frequency: Int)

    /**
     * Returns true if and only if [context] provides the minimum hardware support to
     * use this [Transmitter].
     */
    fun hasHardwareSupport(context: Context): Boolean
}