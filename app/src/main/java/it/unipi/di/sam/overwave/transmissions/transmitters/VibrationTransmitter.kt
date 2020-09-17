package it.unipi.di.sam.overwave.transmissions.transmitters

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.experimental.and

object VibrationTransmitter : Transmitter {

    override suspend fun transmit(context: Context, data: ByteArray, frequency: Int) {
        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        if (vibrator != null) {
            withContext(Dispatchers.Default) {
                // Add Initial-sequence
                val payload = dataToBinaryString(data)
                // TODO apply EEC.
                // Transform into a vibration pattern.
                val timings = createTimings(payload, frequency)
                // Use the vibrator to transmit the data.
                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitudes = timings.mapIndexed {
                            index, _ -> if (index % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
                        }.toIntArray()
                        vibrator.vibrate(VibrationEffect.createWaveform(
                            timings,
                            amplitudes,
                            -1
                        ))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(timings, -1)
                    }
                    // Wait for an amount of time equal to the length of the pattern,
                    // this way the coroutine returns only after the transmission did complete.
                    delay((frequency * payload.length).toLong())
                }
            }
        } else {
            throw IllegalStateException("Couldn't find a Vibrator to transmit the data")
        }
    }

    override fun getDefaultFrequency(): Int = 200

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

    override fun hasHardwareSupport(context: Context): Boolean {
        return ContextCompat.getSystemService(context, Vibrator::class.java) != null
    }
}