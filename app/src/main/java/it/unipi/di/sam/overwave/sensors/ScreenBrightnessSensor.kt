package it.unipi.di.sam.overwave.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.view.Window
import android.view.WindowManager
import it.unipi.di.sam.overwave.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileWriter
import java.math.BigDecimal

data class BrightnessData(val timestamp: Long, val intensity: Float)

class ScreenBrightnessSensor(
    sensorManager: SensorManager?,
    window: Window
) : BaseSensor<BrightnessData>(sensorManager, Sensor.TYPE_LIGHT, SAMPLING_PERIOD) {

    init {
        // Set automatically the brightness to the lowest value, so it will not interfere with the transmission.
        window.attributes.screenBrightness = 0.0001f
        window.addFlags(WindowManager.LayoutParams.FLAGS_CHANGED)
    }

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
                val _samples: MutableList<BrightnessData> =
                    samples.dropWhile { it.intensity < firstEnvironmentNoise }.toMutableList()
                // Group by frequency(with error considerations) and calculate mean.
                // TODO should be done with a sequence or told by the transmitter via bluetooth.
                val delayedFrequency = (transmitterFrequency + SCREEN_LATENCY) * 1000000
                val sampleMeanByFrequency = mutableMapOf<String, BigDecimal>()
                while (isActive && _samples.isNotEmpty()) {
                    val intervalStartTimestamp = _samples[0].timestamp
                    stats.clear()
                    // Compute the interval
                    var data: BrightnessData
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
                    .map { if (it.value >= threshold) '1' else '0' }
                    .joinToString(separator = "")
                decoded = decode(bits)
            }
        }
        samples.clear()
        return decoded
    }

    /*
    override suspend fun decodeSignal(transmitterFrequency: Int): String {
        var decoded = ""
        if (samples.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                // Calibrate.
                val stats = RunningStats()
                val samplesStats = RunningStats()
                val intensities = samples.map { it.intensity }
                val firstEnvironmentNoise = intensities[0]
                val correctedValue: Float = if (firstEnvironmentNoise <= 0.5) firstEnvironmentNoise + ERROR_THRESHOLD else firstEnvironmentNoise
                val _samples: MutableList<BrightnessData> = samples.dropWhile {
                    it.intensity <= correctedValue
                }.toMutableList()
                // Group by frequency(with error considerations) and calculate mean.
                // TODO should be done with a sequence or told by the transmitter via bluetooth.
                val delayedFrequency = (transmitterFrequency + SCREEN_LATENCY) * 1000000
                val sampleMeanByFrequency = mutableMapOf<String, BigDecimal>()
                while (isActive && _samples.isNotEmpty()) {
                    val intervalStartTimestamp = _samples[0].timestamp
                    stats.clear()
                    // Compute the interval
                    var data: BrightnessData
                    do {
                        data = _samples[0]
                        data.intensity.toBigDecimal().let {
                            stats.push(it)
                            samplesStats.push(it)
                        }
                        _samples.removeAt(0)
                    } while (isActive && (data.timestamp - intervalStartTimestamp) <= delayedFrequency && _samples.isNotEmpty())
                    sampleMeanByFrequency["$intervalStartTimestamp + ${data.timestamp}"] = stats.mean()
                }
                val threshold = samplesStats.mean()
                val bits = sampleMeanByFrequency.entries
                    .sortedBy { it.key }
                    .map {
                        if (it.value < threshold) '1'
                        else '0'
                    }
                    .joinToString(separator = "")
                decoded = decode(bits)
            }
        }
        samples.clear()
        return decoded
    }
    */

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_LIGHT -> {
                samples.add(BrightnessData(event.timestamp, event.values[0]))
            }
        }
    }

    override fun performWriting(writer: FileWriter, sample: BrightnessData) {
        writer.write(String.format("%d; %f\n", sample.timestamp, sample.intensity))
    }
    override fun getRawFilename(): String = "brightnessSensor${System.currentTimeMillis()}.csv"

    companion object {
        private const val SAMPLING_PERIOD = 30
        /**
         * Empirical measured threshold.
         */
        private const val ERROR_THRESHOLD = 1.0 // 0.3f
        /**
         * The empirical measured flash light on/off latency in milliseconds.
         */
        private const val SCREEN_LATENCY = 4
    }
}