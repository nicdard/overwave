package it.unipi.di.sam.overwave.transmissions.bluetooth

import android.os.Message
import it.unipi.di.sam.overwave.KEY_RATE
import it.unipi.di.sam.overwave.KEY_TRIALS
import it.unipi.di.sam.overwave.KEY_WAVE
import it.unipi.di.sam.overwave.R
import it.unipi.di.sam.overwave.transmissions.receivers.Receiver
import it.unipi.di.sam.overwave.transmissions.receivers.VibrationReceiver
import it.unipi.di.sam.overwave.transmissions.transmitters.Transmitter
import it.unipi.di.sam.overwave.transmissions.transmitters.VibrationTransmitter
import java.lang.IllegalArgumentException

/**
 * Constants communication between devices.
 */
const val START_TRANSMISSION = "START"
const val END_TRANSMISSION = "END"
const val ACK = "ACK"
const val NACK = "NACK"

/**
 * Converts a wave's string resource id into its string representation.
 */
fun getWaveAsString(id: Int) = when (id) {
    R.id.radio_button_light, R.id.receiver_radio_button_light           -> "light"
    R.id.radio_button_sound, R.id.receiver_radio_button_sound           -> "sound"
    R.id.radio_button_vibration, R.id.receiver_radio_button_vibration   -> "vibration"
    else                                                                -> throw IllegalArgumentException("Unknown wave type.")
}

fun getTransmitter(id: Int): Transmitter = when (id) {
    R.id.radio_button_vibration -> VibrationTransmitter
    else -> TODO()
}

fun getReceiver(id: Int): Receiver = getReceiver(getWaveAsString(id))
fun getReceiver(wave: String): Receiver = when(wave) {
    "vibration" -> VibrationReceiver
    else -> TODO()
}

/**
 * Returns the [START_TRANSMISSION] message.
 */
fun composeStartTransmissionMessage(waveId: Int, sampleRate: String, trials: String): String {
    return listOf(
        "$KEY_WAVE:${getWaveAsString(waveId)}",
        "$KEY_RATE:$sampleRate",
        "$KEY_TRIALS:${trials}"
    ).joinToString(prefix = START_TRANSMISSION + "\n", separator = "\n")
}

fun composeResponse(status: String, command: String) = (status + '\n' + command).toByteArray()