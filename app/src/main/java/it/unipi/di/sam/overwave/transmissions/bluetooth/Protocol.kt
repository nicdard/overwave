package it.unipi.di.sam.overwave.transmissions.bluetooth

import android.content.Context
import it.unipi.di.sam.overwave.*
import it.unipi.di.sam.overwave.transmissions.receivers.Accelerometer
import it.unipi.di.sam.overwave.transmissions.receivers.OnReceived
import it.unipi.di.sam.overwave.transmissions.receivers.Receiver
import it.unipi.di.sam.overwave.transmissions.transmitters.LightTransmitter
import it.unipi.di.sam.overwave.transmissions.transmitters.Transmitter
import it.unipi.di.sam.overwave.transmissions.transmitters.VibrationTransmitter
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
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
    R.id.radio_button_light     -> "light"
    R.id.radio_button_sound     -> "sound"
    R.id.radio_button_vibration -> "vibration"
    else                        -> throw IllegalArgumentException("Unknown wave type.")
}

fun getTransmitter(id: Int): Transmitter = when (id) {
    R.id.radio_button_vibration -> VibrationTransmitter
    R.id.radio_button_light     -> LightTransmitter
    else -> TODO()
}

/*
fun getReceiver(context: Context, job: Job, onReceivedListener: OnReceived, wave: String, isRecorder: Boolean, rate: Int = 45, frequency: Int = 200): Receiver = when(wave) {
    "vibration" -> Accelerometer(context, job, onReceivedListener, isRecorder, frequency)
    else -> TODO()
}
 */
fun getReceiver(context: Context, wave: String, isRecorder: Boolean, rate: Int = 45, frequency: Int = 200): Receiver = when(wave) {
    "vibration" -> Accelerometer(context, isRecorder, frequency)
    else -> TODO()
}



/**
 * Returns the [START_TRANSMISSION] message.
 */
fun composeStartTransmissionMessage(waveId: Int, sampleRate: String, frequency: String, trials: String): String {
    return listOf(
        "$KEY_WAVE:${getWaveAsString(waveId)}",
        "$KEY_RATE:$sampleRate",
        "$KEY_FREQUENCY:$frequency",
        "$KEY_TRIALS:${trials}"
    ).joinToString(prefix = START_TRANSMISSION + "\n", separator = "\n")
}

fun composeResponse(status: String, command: String) = (status + '\n' + command).toByteArray()