package it.unipi.di.sam.overwave.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import it.unipi.di.sam.overwave.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.math.BigDecimal

data class LuminosityData(val timestamp: Long, val intensity: Long)

class LuminositySensor(
    sensorManager: SensorManager?
) : BaseSensor<LuminosityData>(sensorManager, Sensor.TYPE_LIGHT, SAMPLING_PERIOD){

    override suspend fun decodeSignal(transmitterFrequency: Int): String {
        var decoded = ""
        if (samples.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                // Calibrate.
                val stats = RunningStats()
                val intensities = samples.map { it.intensity }
                val firstEnvironmentNoise = intensities[0] + ERROR_THRESHOLD
                stats.push(intensities.minOrNull()!!.toBigDecimal())
                stats.push(intensities.maxOrNull()!!.toBigDecimal())
                val minMaxMean = stats.mean().toLong()
                val _samples: MutableList<LuminosityData> =
                    samples.dropWhile { it.intensity < firstEnvironmentNoise }.toMutableList()
                // Group by frequency(with error considerations) and calculate mean.
                // TODO should be done with a sequence or told by the transmitter via bluetooth.
                val delayedFrequency = (transmitterFrequency + FLASH_LATENCY) * 1000000
                val sampleMeanByFrequency = mutableMapOf<String, BigDecimal>()
                while (isActive && _samples.isNotEmpty()) {
                    val intervalStartTimestamp = _samples[0].timestamp
                    stats.clear()
                    // Compute the interval
                    var data: LuminosityData
                    do {
                        data = _samples[0]
                        stats.push(data.intensity.toBigDecimal())
                        _samples.removeAt(0)
                    } while (isActive && (data.timestamp - intervalStartTimestamp) <= delayedFrequency && _samples.isNotEmpty())
                    sampleMeanByFrequency["$intervalStartTimestamp + ${data.timestamp}"] = stats.mean()
                }
                val threshold = minMaxMean.toBigDecimal()
                val bits = sampleMeanByFrequency.entries
                    .sortedBy { it.key }
                    .map { if (it.value > threshold) '1' else '0' }
                    .joinToString(separator = "")
                decoded = decode(bits)
            }
        }
        samples.clear()
        return decoded
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_LIGHT -> {
                samples.add(LuminosityData(event.timestamp, event.values[0].toLong()))
            }
        }
    }

    override fun performWriting(writer: FileWriter, sample: LuminosityData) {
        writer.write(String.format("%d; %d\n", sample.timestamp, sample.intensity))
    }
    override fun getRawFilename(): String = "light" + System.currentTimeMillis() + ".csv"

    companion object {
        private const val SAMPLING_PERIOD = 30
        /**
         * Empirical measured threshold.
         */
        private const val ERROR_THRESHOLD = 400
        /**
         * The empirical measured flash light on/off latency in milliseconds.
         */
        private const val FLASH_LATENCY = 8
    }
}