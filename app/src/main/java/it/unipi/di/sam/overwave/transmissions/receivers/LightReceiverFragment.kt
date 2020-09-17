package it.unipi.di.sam.overwave.transmissions.receivers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import it.unipi.di.sam.overwave.R
import it.unipi.di.sam.overwave.transmissions.transmitters.INITIAL_SEQUENCE

class LightReceiverFragment : Fragment(), SensorEventListener {

    private lateinit var messageView: TextView

    private lateinit var sensorManager: SensorManager

    private var backgroundIntensity = -1F
    private var startTime = System.currentTimeMillis()

    private var hasStarted = false
    private var lastTime = System.currentTimeMillis()

    private val intensityValues = mutableListOf<Float>()
    private val rawBitReading = mutableListOf<Char>()
    private val records = mutableMapOf<Long, Float>()

    private var startBitDetected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = this.context?.getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.light_receiver_fragment, container, false)
        messageView = root.findViewById(R.id.light_message)
        return root
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_LIGHT -> {
                if (backgroundIntensity == -1F) {
                    startTime = System.currentTimeMillis()
                    Log.d("Start timestamp: ", startTime.toString())
                    backgroundIntensity = event.values[0]
                }
                val currentLightIntensity = event.values[0]
                if (currentLightIntensity > 1000 && !hasStarted) {
                    lastTime = System.currentTimeMillis()
                    hasStarted = true
                }
                val bit = if (currentLightIntensity > backgroundIntensity) '1' else '0'
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTime > 499 && hasStarted) {
                    Log.d("1 second.", "passed.")
                    lastTime = currentTime
                    records[currentTime - startTime] = currentLightIntensity
                    Log.d("Bit:", bit.toString())
                    rawBitReading.add(bit)
                }
                intensityValues.add(currentLightIntensity)
                if (rawBitReading.size >= 2) {
                    if (!startBitDetected) {
                        if (rawBitReading.subList(0, -2).joinToString("") == INITIAL_SEQUENCE) {
                            println("Start bit detected.")
                            messageView.text = "Start bit detected."
                            startBitDetected = true
                        }
                    } else {
                        if (rawBitReading.subList(0, -17).joinToString("") == "00000000000000000") {
                            messageView.text = rawBitReading
                                .dropWhile { it == '0' }
                                .drop(1)
                                .dropLastWhile { it == '0' }
                                .dropLast(1)
                                .joinToString("")
                            sensorManager.unregisterListener(this)
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        @JvmStatic
        fun newInstance(): LightReceiverFragment = LightReceiverFragment()
    }
}