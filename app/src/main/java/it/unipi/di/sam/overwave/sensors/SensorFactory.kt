package it.unipi.di.sam.overwave.sensors

import android.hardware.SensorManager
import it.unipi.di.sam.overwave.utils.Preferences

@Suppress("UNCHECKED_CAST")
class SensorFactory(
    private val preferences: Preferences,
    private val sensorManager: SensorManager
) {
    fun get(): ISensor {
        return when(preferences.wave) {
            LuminositySensor.NAME -> LuminositySensor(sensorManager)
            else                  -> TODO("implement")
        }
    }
}