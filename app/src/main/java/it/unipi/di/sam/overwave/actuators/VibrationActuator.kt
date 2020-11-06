package it.unipi.di.sam.overwave.actuators

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.content.ContextCompat
import it.unipi.di.sam.overwave.R
import it.unipi.di.sam.overwave.transmitter.TransmitViewModel
import it.unipi.di.sam.overwave.utils.dataToBinaryString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

/**
 * Uses the vibrator to transmit the message encoding it with an On-off keying technique.
 */
class VibrationActuator(
    private val context: Context,
    shouldSaveRawData: Boolean = false,
    storageDir: String?,
) : BaseActuator(
    shouldSaveRawData,
    storageDir
) {

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
                val payload = dataToBinaryString(data)
                // Transform into a vibration pattern.
                val timings = createTimings(payload, frequency)
                // Use the vibrator to transmit the data.
                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (shouldSaveRawData) {
                            // TODO log data
                        } else {
                            val amplitudes = timings.mapIndexed { index, _ ->
                                if (index % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
                            }.toIntArray()
                            finalVibrator.vibrate(
                                VibrationEffect.createWaveform(
                                    timings,
                                    amplitudes,
                                    -1
                                )
                            )
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        finalVibrator.vibrate(timings, -1)
                    }
                    // Wait for an amount of time equal to the length of the pattern,
                    // this way the coroutine returns only after the transmission did complete.
                    val length = payload.length
                    for (i in 0..length step 3) {
                        if (!isActive) break
                        delay(frequency * 3L)
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

    private fun createTimings(payload: String, frequency: Int): LongArray {
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

    companion object {
        const val DEFAULT_FREQUENCY = 100
    }
}
