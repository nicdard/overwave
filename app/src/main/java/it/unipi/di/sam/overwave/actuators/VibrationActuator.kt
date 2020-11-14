package it.unipi.di.sam.overwave.actuators

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import it.unipi.di.sam.overwave.R
import it.unipi.di.sam.overwave.transmitter.TransmitViewModel
import it.unipi.di.sam.overwave.utils.dataToBinaryString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Uses the vibrator to transmit the message encoding it with an On-off keying technique.
 */
class VibrationActuator(
    private val context: Context,
    private val patternCreator: VibrationPatternCreator = TransitionTimeShiftingKeying()
) : BaseActuator(false, null) {

    private var vibrator: Vibrator? = null
        get() = synchronized(this@VibrationActuator) { field }
        set(value) = synchronized(this@VibrationActuator) { field = value}

    override fun initialise() {
        super.initialise()
        vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
    }

    override fun getRawFilename(): String =  "vibrator${System.currentTimeMillis()}.csv"

    override suspend fun transmit(data: ByteArray, frequency: Int, viewModel: TransmitViewModel) {
        val finalVibrator = vibrator
        if (finalVibrator != null && finalVibrator.hasVibrator()) {
            withContext(Dispatchers.Default) {
                // Add Initial-sequence
                val payload = patternCreator.binaryEncoder(data)
                // Transform into a vibration pattern.
                val timings = patternCreator.timings(payload, frequency)
                // Use the vibrator to transmit the data.
                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitudes = patternCreator.amplitudes(timings)
                        val waveForm =
                            if (amplitudes.isEmpty()) VibrationEffect.createWaveform(timings, -1)
                            else VibrationEffect.createWaveform(timings, amplitudes, -1)
                        finalVibrator.vibrate(waveForm)
                    } else {
                        @Suppress("DEPRECATION")
                        finalVibrator.vibrate(timings, -1)
                    }
                    // Wait for an amount of time equal to the length of the pattern,
                    // this way the coroutine returns only after the transmission did complete.
                    val length = timings.size
                    val totalTime = timings.toList().sum()
                    val delayTime = totalTime / (length / 3)
                    for (i in 0..length step 3) {
                        if (!isActive) break
                        delay(delayTime)
                        // In the meantime, update the UI so the user knows that something is going on.
                        viewModel.publishProgress(100 * i / length)
                    }
                }
            }
        } else {
            Toast.makeText(context, context.getString(R.string.missing_vibrator), Toast.LENGTH_SHORT).show()
        }
    }

    override fun dispose() {
        super.dispose()
        vibrator?.cancel()
    }

    companion object {
        const val DEFAULT_FREQUENCY = 200
    }
}

/**
 * Using a strategy pattern instead of higher order functions to pass one single object including
 * all needed functions to create the vibration pattern to the [VibrationActuator]. I think this
 * way is better organised and will not lead to confusion when adding new encoding strategies.
 */
interface VibrationPatternCreator {

    fun binaryEncoder(data: ByteArray): String
    /**
     * Creates the timings of alternating On/Off states of the [android.os.Vibrator].
     * The sequence of states is assumed to start with 0.
     */
    fun timings(payload: String, frequency: Int): LongArray
    /**
     * Given an array of timings, create an associated array of amplitudes to feed
     * [android.os.VibrationEffect.createWaveform].
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun amplitudes(timings: LongArray): IntArray
}

/**
 * On-Off keying vibration encoder for binary string.
 */
class OnOffKeying : VibrationPatternCreator {

    override fun binaryEncoder(data: ByteArray) = dataToBinaryString(data)
    /**
     * Returns equal vibration timing sequence to obtain an On-Off Keying encoding of the binary
     * string [payload].
     */
    override fun timings(payload: String, frequency: Int): LongArray {
        // Transform into a vibration pattern.
        val timings = mutableListOf<Long>()
        var lastSeen = '0'
        // The very first time we do not want to add an off time for free.
        var multiplier = 0
        for (bit in payload) {
            if (bit == lastSeen) {
                multiplier += 1
            } else {
                // Add values of timing and amplitude for the passed group.
                timings.add(multiplier * frequency.toLong())
                // Reset counters.
                lastSeen = bit
                multiplier = 1
            }
        }
        return timings.toLongArray()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun amplitudes(timings: LongArray): IntArray = timings.mapIndexed { index, _ ->
        if (index % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
    }.toIntArray()
}

class TransitionTimeShiftingKeying : VibrationPatternCreator {

    override fun binaryEncoder(data: ByteArray): String = data.joinToString(prefix = "", separator = "", postfix = "") {
        // Get the binary string representation of the byte
        Integer.toBinaryString(it.toInt())
            // 8-bit 0-padded string.
            .padStart(8, '0')
    }

    override fun timings(payload: String, frequency: Int): LongArray {
        // Transform into a vibration pattern.
        val timings = mutableListOf<Long>(0)
        val longFrequency = frequency.toLong()
        for (bit in payload) {
            if (bit == '0') {
                timings.add(PULSE_DURATION)
                timings.add(longFrequency * 3)
            } else {
                timings.add(PULSE_DURATION)
                timings.add(longFrequency * 8)
            }
        }
        // Signals the end of the last bit, otherwise the receiver will always detect a 1.
        timings.add(PULSE_DURATION)
        return timings.toLongArray()
    }

    override fun amplitudes(timings: LongArray): IntArray {
        return timings.mapIndexed { index, _ ->
            if (index % 2 == 0) 0 else 255
        }.toIntArray()
    }

    companion object {
        /**
         * The time in ms of an on vibration impulse.
         */
        const val PULSE_DURATION: Long = 200
    }
}