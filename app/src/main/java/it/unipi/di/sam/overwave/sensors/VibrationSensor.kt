package it.unipi.di.sam.overwave.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import it.unipi.di.sam.overwave.utils.RunningStats
import it.unipi.di.sam.overwave.utils.decode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileWriter
import java.math.BigDecimal
import java.math.RoundingMode

data class VibrationData(val timestamp: Long, val z: Float)

class VibrationSensor(
    sensorManager: SensorManager?
) : BaseSensor<VibrationData>(sensorManager, Sensor.TYPE_ACCELEROMETER, SAMPLING_PERIOD) {

    override suspend fun decodeSignal(transmitterFrequency: Int): String {
        var decoded = ""
        if (samples.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                var firstTimestamp: Long? = null
                val standardDevBySec = mutableMapOf<Float, RunningStats>()
                for (event in samples) {
                    if (!isActive) break
                    if (firstTimestamp == null) {
                        firstTimestamp = event.timestamp
                    }
                    val decSeconds = ((event.timestamp - firstTimestamp) / 1000000000F)
                        .toBigDecimal()
                        .setScale(1, RoundingMode.FLOOR)
                        .toFloat()
                    standardDevBySec
                        .getOrPut(decSeconds) { RunningStats() }
                        .push(event.z.toBigDecimal())
                }
                val mean = standardDevBySec.values.map { it.standardDeviation() }.average()
                val signal = standardDevBySec.entries.sortedBy { it.key }.joinToString(separator = "") {
                    if (it.value.standardDeviation() > mean && it.value.standardDeviation() > THRESHOLD) "1"
                    else "0"
                }
                decoded = decode(signal)
            }
        }
        samples.clear()
        return decoded
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                samples.add(VibrationData(event.timestamp, event.values[2]))
            }
        }
    }

    override fun performWriting(writer: FileWriter, sample: VibrationData) {
        writer.write(String.format("%d; %f\n", sample.timestamp, sample.z))
    }
    override fun getRawFilename(): String = "vibration" + System.currentTimeMillis() + ".csv"

    companion object {
        private const val SAMPLING_PERIOD = 10

        private const val ERROR_THRESHOLD = 0.005

        private const val VIBRATOR_LATENCY = 3
        @JvmStatic
        private val THRESHOLD = BigDecimal(0.095).toDouble()
    }
}