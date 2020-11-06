package it.unipi.di.sam.overwave.actuators

import android.app.Application
import android.content.Context.SENSOR_SERVICE
import android.hardware.SensorManager
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import it.unipi.di.sam.overwave.sensors.ScreenBrightnessSensor
import it.unipi.di.sam.overwave.transmitter.TransmitActivity
import it.unipi.di.sam.overwave.transmitter.TransmitViewModel
import it.unipi.di.sam.overwave.utils.dataToBinaryString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Modulates the screen brightness to transmit a message.
 */
class ScreenBrightnessActuator(
    private val window: Window,
    shouldSaveRawData: Boolean = false,
    private val storageDir: String?,
    private val application: Application?, // Used to simulate intra-device communication.
) : BaseActuator(
    shouldSaveRawData,
    storageDir
) {
    override fun getRawFilename(): String = "brightness${System.currentTimeMillis()}.csv"

    override suspend fun transmit(data: ByteArray, frequency: Int, viewModel: TransmitViewModel) {
        withContext(Dispatchers.Main) {
            val originalBrightness = window.attributes.screenBrightness
            zeroPadding()
            val screenSensor = if (application != null)
                ScreenBrightnessSensor(application.getSystemService(SENSOR_SERVICE) as SensorManager, window).apply { activate() }
            else null
            val milliseconds = frequency.toLong()
            val toEmit = dataToBinaryString(data)
            val length = toEmit.length
            var i = 0;
            // Make the coroutine cancellable
            while (isActive && i < length) {
                val bit = toEmit[i]
                when (bit) {
                    '1' -> window.attributes.screenBrightness = 1f
                    else -> window.attributes.screenBrightness = 0.01f
                }
                window.addFlags(WindowManager.LayoutParams.FLAGS_CHANGED);
                i++
                withContext(Dispatchers.IO) {
                    if (i % 3 == 0) {
                        viewModel.publishProgress(100 * i / length)
                    }
                    delay(milliseconds)
                    storeTimestamp()
                }
            }
            screenSensor?.run {
                stop()
                if (shouldSaveRawData) writeRawData(storageDir)
                val retrieved = decodeSignal(frequency)
                Toast.makeText(application!!.applicationContext, retrieved, Toast.LENGTH_LONG).show()
                TransmitActivity.log("Screen Brightness intra-device simulation, decoded $retrieved")

                dispose()
            }
            zeroPadding()
            window.attributes.screenBrightness = originalBrightness
            window.clearFlags(WindowManager.LayoutParams.FLAGS_CHANGED)
            // Turn off the light after transmission.
            withContext(Dispatchers.IO) {
                writeRawData()
            }

        }
    }

    private suspend fun zeroPadding() {
        // Encode 0 bits as 1
        window.attributes.screenBrightness = 0.01f
        window.addFlags(WindowManager.LayoutParams.FLAGS_CHANGED)
        // Not necessary, it's just to have time to put the phone in the correct place during tests.
        withContext(Dispatchers.IO) {
            delay(4000)
        }
    }

    companion object {
        const val DEFAULT_FREQUENCY = 200
    }
}