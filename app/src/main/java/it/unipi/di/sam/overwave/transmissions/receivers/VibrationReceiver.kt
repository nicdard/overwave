package it.unipi.di.sam.overwave.transmissions.receivers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import it.unipi.di.sam.overwave.transmissions.statistics.RunningStats
import it.unipi.di.sam.overwave.transmissions.transmitters.VibrationTransmitter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.math.RoundingMode
import kotlin.coroutines.CoroutineContext

const val TAG = "[VibrationReceiver]"

private val NOISE_16_BIT = "00000000000000000".repeat(VibrationTransmitter.getDefaultFrequency() / 100)

object VibrationReceiver : Receiver, SensorEventListener, CoroutineScope {

    private var sensorManager: SensorManager? = null

    private var coroutineScope: CoroutineScope? = null
    private val eventsBuffer: Channel<SensorEvent> = Channel()

    private var firstTimestamp: Long? = null
    private val stdevBySec = mutableMapOf<Float, RunningStats>()
    private var decoded: String? = null

    private var shouldRecord = false
    private var writer: FileWriter? = null

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                coroutineScope?.launch {
                    eventsBuffer.send(event)
                }
                if (shouldRecord) {
                    writer!!.write(String.format(
                        "%d; ACC; %f; %f; %f\n",
                        event.timestamp,
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    ))
                    /*
                    if (firstTimestamp == null) {
                        firstTimestamp = event.timestamp
                    }
                    val decSeconds = ((event.timestamp - firstTimestamp!!) / 1000000000F)
                        .toBigDecimal()
                        .setScale(1, RoundingMode.FLOOR)
                        .toFloat()
                    stdevBySec
                        .getOrPut(decSeconds) { RunningStats() }
                        .push(event.values[2].toBigDecimal())
                    val mean = stdevBySec.values.map {  it.standardDeviation() }.average()
                    decoded = stdevBySec.entries.sortedBy { it.key }.joinToString(separator = "") {
                        if (it.value.standardDeviation() > mean) "1"
                        else "0"
                    }*/

                }
            }
        }
    }

    suspend fun processBatch() {
        withContext(Dispatchers.IO) {
            if (firstTimestamp == null) {
                firstTimestamp = event.timestamp
            }
            val decSeconds = ((event.timestamp - firstTimestamp!!) / 1000000000F)
                .toBigDecimal()
                .setScale(1, RoundingMode.FLOOR)
                .toFloat()
            stdevBySec
                .getOrPut(decSeconds) { RunningStats() }
                .push(event.values[2].toBigDecimal())
            val mean = stdevBySec.values.map { it.standardDeviation() }.average()
            decoded = stdevBySec.entries.sortedBy { it.key }.joinToString(separator = "") {
                if (it.value.standardDeviation() > mean) "1"
                else "0"
            }
        }
    }

    /**
     * Transforms a decoded binaryString in a ByteArray of data.
     */
    private fun postProcess(received: String): ByteArray {
        try {
            return received
                .reversed()
                .dropWhile { it == '0' }.drop(2)
                .reversed()
                .dropWhile { it == '0' }
                .drop(2)
                .chunked(4)
                .joinToString(separator = "") { chunk ->
                    val sum = chunk.count { it == '1' }
                    when {
                        sum >= 2 -> "1"
                        else -> "0"
                    }
                }
                .chunked(8)
                .map {
                    it.toInt(2).toByte()
                }.toByteArray()
        } catch (e: IllegalArgumentException) {
            return "".toByteArray()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Register a listener in the [SensorManager] retrieved using [context].
     * Also, saves a reference of [sensorManager] to unregister this listener later.
     */
    override fun start(context: Context, coroutineScope: CoroutineScope, frequency: Int) {
        if (sensorManager != null) this.stop()
        this.sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = this.sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, sensor, frequency)
        this.coroutineScope
    }

    override fun stop() {
        if (shouldRecord) {
            try {
                /* stdevBySec.entries.forEach {
                    writer?.write(String.format(
                        "%f; %f\n",
                        it.key,
                        it.value.standardDeviation()
                    ))
                }
                */
                writer?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            sensorManager?.flush(this)
        }
        sensorManager?.unregisterListener(this)
        sensorManager = null
        shouldRecord = false
        stdevBySec.clear()
        decoded = null
        firstTimestamp = null
        coroutineScope = null
    }

    override fun record(context: Context, coroutineScope: CoroutineScope, path: String, frequency: Int) {
        try {
            writer = FileWriter(File(path, "sensors_" + System.currentTimeMillis() + ".csv"))
        } catch (e: IOException) {
            e.printStackTrace()
        }
        start(context, coroutineScope, frequency)
        shouldRecord = true
    }
}