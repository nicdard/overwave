package it.unipi.di.sam.overwave.actuators

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.content.ContextCompat
import it.unipi.di.sam.overwave.transmitter.TransmitViewModel
import it.unipi.di.sam.overwave.utils.dataToBinaryString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

class VibrationActuator(
    private val context: Context? = null,
    private val shouldSaveRawData: Boolean = false,
    private val storageDir: String?,
) : IActuator {

    private var writer: FileWriter? = null
    private var vibrator: Vibrator? = null
        get() = synchronized(this@VibrationActuator) { field }
        set(value) = synchronized(this@VibrationActuator) { field = value}

    override fun initialise() {
        if (context == null) return
        if (shouldSaveRawData && storageDir != null && writer == null) {
            writer = try {
                FileWriter(File(storageDir, "vibrator" + System.currentTimeMillis() + ".csv"))
            } catch (e: Exception) {
                null
            }
        }
        vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
    }

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
                    writer?.run {
                        write(String.format("expectedTime; %d \n", frequency * payload.length))
                        write(String.format("start; %d \n", System.currentTimeMillis()))
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitudes = timings.mapIndexed {
                                index, _ -> if (index % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
                        }.toIntArray()
                        finalVibrator.vibrate(VibrationEffect.createWaveform(
                            timings,
                            amplitudes,
                            -1
                        ))
                    } else {
                        @Suppress("DEPRECATION")
                        finalVibrator.vibrate(timings, -1)
                    }
                    // Wait for an amount of time equal to the length of the pattern,
                    // this way the coroutine returns only after the transmission did complete.
                    for (i in 0..payload.length step 3) {
                        if (!isActive) break
                        delay(frequency * 3L)
                        // In the meantime, update the UI so the user knows that something is going on.
                        viewModel.publishProgress(i)
                    }
                    writer?.run {
                        write(String.format("end; %d \n", System.currentTimeMillis()))
                    }
                }
            }
        } else {
            Toast.makeText(context, "Couldn't find a Vibrator to transmit the data", Toast.LENGTH_SHORT).show()
        }
    }

    override fun dispose() {
        try {
            writer?.close()
        } catch (e: Exception) {}
        writer = null
        vibrator?.cancel()
    }

    // It doesn't requires a runtime permission, we can go therefore with vacuous truth
    override fun neededPermissions(): Array<String> = arrayOf()

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
        const val DEFAULT_FREQUENCY = 200
    }
}
