package it.unipi.di.sam.overwave.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import it.unipi.di.sam.overwave.utils.RunningStats
import it.unipi.di.sam.overwave.utils.decode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileWriter

data class VibrationData(val timestamp: Long, val z: Float)

class VibrationSensor(
    sensorManager: SensorManager?
) : BaseSensor<VibrationData>(sensorManager, Sensor.TYPE_ACCELEROMETER, SAMPLING_PERIOD) {

    override suspend fun decodeSignal(transmitterFrequency: Int): String {
        var decoded = ""
        if (samples.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                val stats = RunningStats()
                val delayedFrequency = transmitterFrequency * 1000000
                val stddevs = mutableMapOf<Long, Double>()
                while (isActive && samples.isNotEmpty()) {
                    val intervalStartTimestamp = samples[0].timestamp
                    stats.clear()
                    // Compute the interval
                    var data: VibrationData
                    do {
                        data = samples[0]
                        stats.push(data.z.toBigDecimal())
                        samples.removeAt(0)
                    } while (isActive && (data.timestamp - intervalStartTimestamp) <= delayedFrequency && samples.isNotEmpty())
                    stddevs[intervalStartTimestamp] = stats.standardDeviation()
                }
                /*for (event in samples) {
                    if (!isActive) break
                    if (firstTimestamp == null) {
                        firstTimestamp = event.timestamp
                    }
                    val decSeconds = (event.timestamp - firstTimestamp) / (transmitterFrequency * 1000000)
                    standardDevBySec
                        .getOrPut(decSeconds) { RunningStats() }
                        .push(event.z.toBigDecimal())
                }*/
                decoded = decodeTransitionTimeDistanceKeying(transmitterFrequency, stddevs)
            }
        }
        samples.clear()
        return decoded
    }

    private fun CoroutineScope.decodeTransitionTimeDistanceKeying(transmitterFrequency: Int, stddevs: Map<Long, Double>): String {
        val minMaxStats = RunningStats()
        minMaxStats.push(stddevs.values.minOrNull()!!.toBigDecimal())
        minMaxStats.push(stddevs.values.maxOrNull()!!.toBigDecimal())
        val threshold = minMaxStats.mean().toDouble()
        val raw = stddevs
            .mapValues { it.value >= threshold }
            .entries.sortedBy { it.key }
            .dropWhile { !it.value }
            .toMutableList()
        var i = 1
        // Drop consecutive equal values
        while (isActive && i < raw.size) {
            val entry = raw[i]
            val previous = raw[i - 1]
            if (previous.value == entry.value) {
                raw.removeAt(i)
            } else ++i
        }
        // Decode the signal based on timing
        var lastTimestamp = raw[0].key
        val middleTime = 6 * (transmitterFrequency * 1000000)
        val decodedBinary = mutableListOf<Char>()
        i = 1
        while (isActive && i < raw.size) {
            if (raw[i].value) {
                if ((raw[i].key - lastTimestamp)  > middleTime) decodedBinary.add('1')
                else decodedBinary.add('0')
                lastTimestamp = raw[i].key
            }
            ++i
        }
        return decodedBinary.joinToString(separator = "")
            .chunked(8)
            .map { it.toInt(2).toChar() }
            .joinToString("")
    }

    private fun decodeOnOffKeying(standardDevBySec: Map<Float, RunningStats>): String {
        val mean = standardDevBySec.values.map { it.standardDeviation() }.average()
        val signal = standardDevBySec.entries.sortedBy { it.key }.joinToString(separator = "") {
            if (it.value.standardDeviation() > mean) "1"
            else "0"
        }
        return decode(signal)
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
    }
}