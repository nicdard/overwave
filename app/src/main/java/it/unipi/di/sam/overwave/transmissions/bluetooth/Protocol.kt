package it.unipi.di.sam.overwave.transmissions.bluetooth

import it.unipi.di.sam.overwave.KEY_RATE
import it.unipi.di.sam.overwave.KEY_WAVE
import it.unipi.di.sam.overwave.R
import it.unipi.di.sam.overwave.transmissions.transmitters.Transmitter
import it.unipi.di.sam.overwave.transmissions.transmitters.VibrationTransmitter
import java.lang.IllegalArgumentException

/**
 * Constants communication between devices.
 */
const val START_TRANSMISSION = "START\n"
const val END_TRANSMISSION = "END\n"
const val ACK = "ACK\n"
const val NACK = "NACK\n"

/**
 * Converts a wave's string resource id into its string representation.
 */
fun getWaveAsString(id: Int) = when (id) {
    R.id.radio_button_light     -> "light"
    R.id.radio_button_sound     -> "sound"
    R.id.radio_button_vibration -> "vibration"
    else                        -> throw IllegalArgumentException("Unknown wave type.")
}

internal fun getTransmitter(id: Int): Transmitter = when (id) {
    R.id.radio_button_vibration -> VibrationTransmitter
    else -> TODO()
}

/**
 * Returns the [START_TRANSMISSION] message.
 */
fun composeStartTransmissionMessage(waveId: Int, sampleRate: String): String {
    return listOf(
        "$KEY_WAVE:${getWaveAsString(waveId)}",
        "$KEY_RATE:$sampleRate"
    ).joinToString(prefix = START_TRANSMISSION, separator = "\n")
}