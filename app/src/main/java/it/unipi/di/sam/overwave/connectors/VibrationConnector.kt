package it.unipi.di.sam.overwave.connectors

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService

const val INITIAL_SEQUENCE = "11111111"
const val BYTES_PER_TRANSMISSION = 32

/**
 * A [Connector] which uses vibration waves to communicate. Transmits 32 bytes of data
 * through the vibrator. Uses the accelerometer to listen for transmissions.
 *
 * // FIXME can be a lifecycle-aware object
 */
class VibrationConnector(
    override val context: Context,
    override val frequency: Int = 60,
    override val onEnd: (List<Byte>, Boolean) -> Unit
) : Connector, SensorEventListener {

    // private val gravity = FloatArray(3)
    // private val linearAcceleration = FloatArray(3)
    private val data: Map<Char, MutableList<Float>> = mapOf(
        'x' to mutableListOf(),
        'y' to mutableListOf(),
        'z' to mutableListOf()
    )

    private val retrieved: Map<Char, MutableList<Byte>> = mapOf(
        'x' to mutableListOf(),
        'y' to mutableListOf(),
        'z' to mutableListOf()
    )

    private val accelerometer: Sensor
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var hasReceivedInitialSequence: Boolean = false

    init {
        val accelerometers = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER)
        if (accelerometers.isNotEmpty()) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else {
            throw IllegalStateException("Unable to locate an accelerometer, this connector cannot be used therefore to receive data")
        }
    }

    override fun transmit(data: List<Byte>) {
        val vibrator = getSystemService(context, Vibrator::class.java)
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

    /**
     * Registers this connector to gather data from the accelerometer with the
     * given [frequency] rate
     */
    override fun receive() {
        sensorManager.registerListener(this, accelerometer, frequency)
    }

    /**
     * Gets accelerometer data, process the data byte a byte
     */
    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                data.getValue('x').add(event.values[0])
                data.getValue('y').add(event.values[1])
                data.getValue('z').add(event.values[2])
                if (data.getValue('x').size == 8) {
                    if (hasReceivedInitialSequence) {
                        processByte()
                    } else {
                        searchForInitialSequence()
                    }
                }
            }
        }
        if (retrieved.getValue('x').size == BYTES_PER_TRANSMISSION) {
            retrieved.values.forEach { onEnd(it, hasReceivedInitialSequence) }
            finalize()
        }
    }

    /**
     * Process one byte of information and stores it to be retrieved.
     */
    private fun processByte() {
        retrieved.entries.forEach { (key, value) ->
            value.add(toBinaryString(data.getValue(key)).toByte(radix = 2))
        }
    }

    /**
     * Verifies if the byte is the initial sequence and sets [hasReceivedInitialSequence]
     * accordingly.
     */
    private fun searchForInitialSequence() {
        when (INITIAL_SEQUENCE) {
            toBinaryString(data.getValue('x')) -> {
                hasReceivedInitialSequence = true
            }
            toBinaryString(data.getValue('y')) -> {
                hasReceivedInitialSequence = true
            }
            toBinaryString(data.getValue('z')) -> {
                hasReceivedInitialSequence = true
            }
        }
        if (hasReceivedInitialSequence) {
            data.values.forEach { it.clear() }
        } else {
            data.values.forEach { it.removeAt(0) }
        }
    }

    /**
     * Release the resources. More specifically, unregister this Connector
     * from accelerometer notifications.
     */
    fun finalize() {
        // Stop receiving data from sensor.
        sensorManager.unregisterListener(this, accelerometer)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

private fun toBinaryString(data: List<Float>): String {
    val average = data.average()
    return data.map {
        when {
            it > average -> '1'
            else -> '0'
        }
    }.joinToString(separator = "")
}