package it.unipi.di.sam.overwave.receivers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import it.unipi.di.sam.overwave.transmitters.BYTES_PER_TRANSMISSION
import it.unipi.di.sam.overwave.transmitters.INITIAL_SEQUENCE

internal class VibrationReceiver(
    context: Context,
    onEnd: (data: List<Byte>, isSuccessful: Boolean) -> Unit,
    frequency: Int = 60
) : Receiver(context, onEnd, frequency), SensorEventListener {

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


    override fun start() {
        sensorManager.registerListener(this, accelerometer, frequency)
    }

    override fun stop() {
        // Stop receiving data from sensor.
        sensorManager.unregisterListener(this, accelerometer)
    }

    /**
     * Gets accelerometer data, process each byte received
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
            // Stop receiving data from sensor.
            sensorManager.unregisterListener(this, accelerometer)
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