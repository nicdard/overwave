package it.unipi.di.sam.overwave.transmitters

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat

internal const val INITIAL_SEQUENCE = "11111111"
internal const val BYTES_PER_TRANSMISSION = 32

internal object VibrationTransmitter : Transmitter {

    override fun transmit(context: Context, data: List<Byte>, frequency: Int) {
        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        if (vibrator != null) {
            // Add Initial-sequence
            val payload = INITIAL_SEQUENCE + data
                // take first 32 byte = 256 bits
                .take(BYTES_PER_TRANSMISSION).joinToString(separator = "") { byte ->
                    // Transforms every byte of data into vibration pattern.
                    // Get the binary string representation of the byte, 8-bit fixed length
                    var bits = Integer.toBinaryString(byte.toInt())
                    if (bits.length < 8) {
                        bits = "0".repeat(8 - bits.length) + bits
                    }
                    bits
                }

            val timings = mutableListOf<Long>()
            val amplitudes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mutableListOf<Int>() else null
            var lastSeen = '0'
            var multiplier = 0
            for (bit in payload) {
                if (bit == lastSeen) {
                    multiplier += 1
                } else {
                    // Add values of timing and amplitude for the passed group.
                    timings.add(multiplier * frequency.toLong())
                    if (lastSeen == '1')
                    // Amplitude for a group of ones.
                        amplitudes?.add(VibrationEffect.DEFAULT_AMPLITUDE)
                    else
                    // Amplitude for a group of zeros.
                        amplitudes?.add(0)
                    // Reset counters.
                    lastSeen = bit
                    multiplier = 1
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amplitudes!!.toIntArray(), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings.toLongArray(), -1)
            }
        } else {
            throw IllegalStateException("Couldn't find a Vibrator to transmit the data")
        }
    }

    override fun hasHardwareSupport(context: Context): Boolean {
        return ContextCompat.getSystemService(context, Vibrator::class.java) != null
    }
}