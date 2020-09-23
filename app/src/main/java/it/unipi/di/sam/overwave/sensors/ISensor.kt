package it.unipi.di.sam.overwave.sensors

/**
 * Abstract the sensor so we can use it in the viewModel if we want without importing android classes.
 * It also encapsulate a sensor facility in order to switch between multiple receivers inside the same architecture.
 */
interface ISensor {
    /**
     * Starts reading the sensor.
     * Clear any previous internal status.
     */
    fun activate()

    /**
     * Writes the raw samples to a CSV file only if some data has been gathered by the sensor.
     */
    suspend fun writeRawData(path: String?)

    /**
     * Decodes the signal. Consumes all data collected up to this function call.
     */
    suspend fun decodeSignal(transmitterFrequency: Int): String

    /**
     * Stops reading the sensor.
     */
    fun stop()

    /**
     * Stops reading the sensor.
     * Clear any internal status.
     *
     * Prepares also the object for deactivation.
     */
    fun dispose()
}
