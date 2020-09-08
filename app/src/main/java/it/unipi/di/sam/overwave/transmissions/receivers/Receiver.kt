package it.unipi.di.sam.overwave.transmissions.receivers

import android.content.Context
import android.hardware.SensorManager

/**
 * A [Receiver] uses one or more sensors to retrieve data
 * emitted through a physical system by another device.
 */
interface Receiver {

    /**
     * Listens for data transmission and retrieves them.
     */
    fun start(context: Context, frequency: Int = SensorManager.SENSOR_DELAY_FASTEST)

    /**
     * Stops listening for data.
     */
    fun stop()

    /**
     * Records a raw signal in a csv file. Used for offload data analysis and filtering construction.
     */
    fun record(context: Context, path: String, frequency: Int = 0)

    processPartial() {}
}