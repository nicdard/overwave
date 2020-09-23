package it.unipi.di.sam.overwave.sensors

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

abstract class BaseSensor<T>(
    private var sensorManager: SensorManager?,
    private val SAMPLING_PERIOD: Int
) : ISensor, SensorEventListener {

    protected val samples = mutableListOf<T>()

    override fun activate() {
        samples.clear()
        sensorManager?.registerListener(
            this,
            sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT),
            SAMPLING_PERIOD
        )
    }

    override fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun dispose() {
        stop()
        // Just to be sure.
        sensorManager = null
        samples.clear()
    }

    protected abstract fun performWriting(writer: FileWriter, sample: T)
    protected abstract fun getRawFilename(): String
    override suspend fun writeRawData(path: String?) {
        if (path != null && samples.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    FileWriter(File(path, getRawFilename())).use {
                        for (sample in samples) {
                            performWriting(it, sample)
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}