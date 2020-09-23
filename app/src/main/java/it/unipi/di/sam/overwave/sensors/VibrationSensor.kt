package it.unipi.di.sam.overwave.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import it.unipi.di.sam.overwave.utils.RunningStats
import it.unipi.di.sam.overwave.utils.decode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.math.BigDecimal

data class VibrationData(val timestamp: Long, val z: Float)

class VibrationSensor(
    sensorManager: SensorManager?
) : BaseSensor<VibrationData>(sensorManager, SAMPLING_PERIOD) {

    override suspend fun decodeSignal(transmitterFrequency: Int): String {
        var decoded = ""
        if (samples.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                // Calibrate.
                val stats = RunningStats()
                val intensities = samples.map { it.z }
                val firstEnvironmentNoise = intensities[0] + ERROR_THRESHOLD
                stats.push(intensities.minOrNull()!!.toBigDecimal())
                stats.push(intensities.maxOrNull()!!.toBigDecimal())
                val minMaxMean = stats.mean().toLong()
                val _samples: MutableList<VibrationData> =
                    samples.dropWhile { it.z < firstEnvironmentNoise }.toMutableList()
                // Group by frequency(with error considerations) and calculate mean.
                // TODO should be done with a sequence or told by the transmitter via bluetooth.
                val delayedFrequency = (transmitterFrequency + VIBRATOR_LATENCY) * 1000000
                val sampleMeanByFrequency = mutableMapOf<String, BigDecimal>()
                while (isActive && _samples.isNotEmpty()) {
                    val intervalStartTimestamp = _samples[0].timestamp
                    stats.clear()
                    // Compute the interval
                    var data: VibrationData
                    do {
                        data = _samples[0]
                        stats.push(data.z.toBigDecimal())
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
            Sensor.TYPE_ACCELEROMETER -> {
                samples.add(VibrationData(event.timestamp, event.values[0]))
            }
        }
    }

    override fun performWriting(writer: FileWriter, sample: VibrationData) {
        writer.write(String.format("%d; %d\n", sample.timestamp, sample.z))
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
/*
class Accelerometer(
    context: Context,
    private val isRecorder: Boolean = false,
    private val samplingRate: Int = 40,
    private val frequency: Int = 200
) : Receiver, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * The dir where to save data recording.
     */
    private val storageDir: String = context.getExternalFilesDir(null)!!.absolutePath
    private var writer: FileWriter?
    init {
        writer = if (isRecorder) {
            try {
                FileWriter(File(storageDir, "accelerometer_" + System.currentTimeMillis() + ".csv"))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }


    private val THRESHOLD = BigDecimal(0.095).toDouble()

    private val events: MutableList<SensorEvent> = mutableListOf()

    override fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, samplingRate)
    }

    override fun stop() {
        try {
            writer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        writer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            sensorManager.flush(this)
        }
        sensorManager.unregisterListener(this)
    }

    override fun consume(): String {
        var firstTimestamp: Long? = null
        val standardDevBySec = mutableMapOf<Float, RunningStats>()

        for (event in events) {
            if (firstTimestamp == null) {
                firstTimestamp = event.timestamp
            }
            val decSeconds = ((event.timestamp - firstTimestamp) / 1000000000F)
                .toBigDecimal()
                .setScale(1, RoundingMode.FLOOR)
                .toFloat()
            standardDevBySec
                .getOrPut(decSeconds) { RunningStats() }
                .push(event.values[2].toBigDecimal())
        }
        val signal = processSignal(standardDevBySec)
        return parseSignal(signal)
    }

    private fun processSignal(standardDevBySec: Map<Float, RunningStats>): String {
        val mean = standardDevBySec.values.map { it.standardDeviation() }.average()
        return standardDevBySec.entries.sortedBy { it.key }.joinToString(separator = "") {
            if (it.value.standardDeviation() > mean && it.value.standardDeviation() > THRESHOLD) "1"
            else "0"
        }
    }

    private fun parseSignal(signal: String) = try {
        val decodedBitsForAnOriginalBit = if (frequency / 100 > 0) 2 * frequency / 100 else 1
        String(signal
            .reversed()
            .dropWhile { it == '0' }.drop(decodedBitsForAnOriginalBit)
            .reversed()
            .dropWhile { it == '0' }
            .drop(decodedBitsForAnOriginalBit)
            .chunked(2 * decodedBitsForAnOriginalBit)
            .joinToString(separator = "") { chunk ->
                val sum = chunk.count { it == '1' }
                when {
                    sum >= 2 * decodedBitsForAnOriginalBit -> "1"
                    else -> "0"
                }
            }
            .chunked(8)
            .map {
                it.toInt(2).toByte()
            }.toByteArray()
        )
    } catch (e: IllegalArgumentException) {
        "Error"
    }

    private suspend fun onPostExecute(message: String) {
        withContext(Dispatchers.Main) {
            this@Accelerometer.stop()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Produce the next element.
                events.add(event)
                if (isRecorder) {
                    writer?.write(
                        String.format(
                            "%d; %f; %f; %f\n",
                            event.timestamp,
                            event.values[0],
                            event.values[1],
                            event.values[2]
                        )
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /*
    private inner class StaticAnaliseThread(val standardDevBySec: Map<Float, RunningStats>) : Thread() {
        override fun run() {
            val firstTimestamp = standardDevBySec.keys.sortedBy { it }[0]
            val signal = this@Accelerometer.processSignal(standardDevBySec)
            this@Accelerometer.onReceivedRef.get()?.invoke(signal)
        }
    }
    */

}

/*
package it.unipi.di.sam.overwave.transmissions.receivers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import it.unipi.di.sam.overwave.transmissions.statistics.RunningStats
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.File
import java.io.FileWriter
import java.lang.ref.WeakReference
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.Exception

private const val NOISE_16_BIT = "0000000000000000"
private const val TAG = "[Accelerometer]"

class Accelerometer(
    context: Context,
    parentJob: Job,
    onReceivedListener: OnReceived,
    private val isRecorder: Boolean = false,
    private val samplingRate: Int = 40,
    private val frequency: Int = 200
) : Receiver, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val onReceivedRef = WeakReference(onReceivedListener)

    // Child job so we have cancellation for free.
    private val accelerometerJob = Job(parentJob)
    private val scope = CoroutineScope(Dispatchers.IO + accelerometerJob)

    /**
     * The dir where to save data recording.
     */
    private val storageDir: String = context.getExternalFilesDir(null)!!.absolutePath
    private val writer: FileWriter?
    init {
        writer = if (isRecorder) {
            try {
                FileWriter(File(storageDir, "accelerometer_" + System.currentTimeMillis() + ".csv"))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private val THRESHOLD = BigDecimal(0.095).toDouble()

    private val eventsChannel: Channel<SensorEvent> = Channel(10)

    override fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, samplingRate)
        // Start the consumer.
        scope.launch {
            consumer(eventsChannel)
        }
    }

    override fun cancel() {
        try {
            writer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            sensorManager.flush(this)
        }
        eventsChannel.cancel()
        sensorManager.unregisterListener(this)
        accelerometerJob.cancel()
    }

    private suspend fun consumer(
        channel: ReceiveChannel<SensorEvent>
    ) {
        val standardDevBySec = mutableMapOf<Float, RunningStats>()
        var firstTimestamp: Long? = null
        withContext(Dispatchers.Default) {
            for (event in channel) {
                if (firstTimestamp == null) {
                    firstTimestamp = event.timestamp
                }
                val signal = consume(firstTimestamp!!, standardDevBySec, event)
                val message = parseSignal(signal)
                // decodedChannel.send(message)
                val hasCompleted = signal.endsWith(NOISE_16_BIT) && signal.contains("1100")
                if (hasCompleted) {
                    // Stop receiving from this channel.
                    channel.cancel()
                    onPostExecute(message)
                }
            }
        }
    }

    private fun consume(
        firstTimestamp: Long,
        standardDevBySec: MutableMap<Float, RunningStats>,
        event: SensorEvent
    ): String {
        val decSeconds = ((event.timestamp - firstTimestamp) / 1000000000F)
            .toBigDecimal()
            .setScale(1, RoundingMode.FLOOR)
            .toFloat()
        standardDevBySec
            .getOrPut(decSeconds) { RunningStats() }
            .push(event.values[2].toBigDecimal())
        return processSignal(standardDevBySec)
    }

    private fun processSignal(standardDevBySec: Map<Float, RunningStats>): String {
        val mean = standardDevBySec.values.map { it.standardDeviation() }.average()
        return standardDevBySec.entries.sortedBy { it.key }.joinToString(separator = "") {
            if (it.value.standardDeviation() > mean && it.value.standardDeviation() > THRESHOLD) "1"
            else "0"
        }
    }

    private fun parseSignal(signal: String) = try {
        val decodedBitsForAnOriginalBit = if (frequency / 100 > 0) 2 * frequency / 100 else 1
        String(signal
            .reversed()
            .dropWhile { it == '0' }.drop(decodedBitsForAnOriginalBit)
            .reversed()
            .dropWhile { it == '0' }
            .drop(decodedBitsForAnOriginalBit)
            .chunked(2 * decodedBitsForAnOriginalBit)
            .joinToString(separator = "") { chunk ->
                val sum = chunk.count { it == '1' }
                when {
                    sum >= 2 * decodedBitsForAnOriginalBit -> "1"
                    else -> "0"
                }
            }
            .chunked(8)
            .map {
                it.toInt(2).toByte()
            }.toByteArray()
        )
    } catch (e: IllegalArgumentException) {
        "Error"
    }

    private suspend fun onPostExecute(message: String) {
        withContext(Dispatchers.Main) {
            onReceivedRef.get()?.invoke(message)
            this@Accelerometer.cancel()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                scope.launch {
                    if (isRecorder) {
                        writer!!.write(String.format(
                            "%d; %f; %f; %f\n",
                            event.timestamp,
                            event.values[0],
                            event.values[1],
                            event.values[2]
                        ))
                    } else {
                        // Produce the next element.
                        eventsChannel.send(event)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /*
    private inner class StaticAnaliseThread(val standardDevBySec: Map<Float, RunningStats>) : Thread() {
        override fun run() {
            val firstTimestamp = standardDevBySec.keys.sortedBy { it }[0]
            val signal = this@Accelerometer.processSignal(standardDevBySec)
            this@Accelerometer.onReceivedRef.get()?.invoke(signal)
        }
    }
    */

}


 */
 */
