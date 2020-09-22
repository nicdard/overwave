package it.unipi.di.sam.overwave.bluetooth

/**
 * Constants communication between devices.
 */
const val START_TRANSMISSION = "START"
const val END_TRANSMISSION = "END"
const val END_TRIALS = "END_TRIALS"

const val ACK = "ACK"
const val NACK = "NACK"

const val KEY_WAVE          = "wave_key"
const val KEY_FREQUENCY     = "frequency_key"
const val KEY_TEXT          = "text_key"
const val KEY_TRIALS        = "trials_key"

/**
 * Returns the [START_TRANSMISSION] message.
 */
fun composeStartTransmissionMessage(wave: String, frequency: Int, trials: Int, text: String): String {
    return listOf(
        "$KEY_WAVE:${wave}",
        "$KEY_FREQUENCY:$frequency",
        "$KEY_TRIALS:${trials}",
        "$KEY_TEXT:${text}"
    ).joinToString(prefix = START_TRANSMISSION + "\n", separator = "\n")
}

/**
 * @param status: one of [ACK] [NACK]
 * @param command: one of [START_TRANSMISSION] [END_TRANSMISSION]
 */
fun composeResponse(status: String, command: String) = (status + '\n' + command).toByteArray()
