package it.unipi.di.sam.overwave.actuators

import it.unipi.di.sam.overwave.transmitter.TransmitViewModel

/**
 * Abstract the actuator so we can use it in the viewModel if we want without importing android classes.
 * It also encapsulate an actuator facility in order to switch between multiple transmitters inside the same architecture.
 */
interface IActuator {

    /**
     * Prepare the actuator. Should be called before transmitting.
     */
    fun initialise()

    /**
     * Transmits the data, using the given frequency and emits state updates to the given viewModel.
     */
    suspend fun transmit(data: ByteArray, frequency: Int, viewModel: TransmitViewModel)

    /**
     * Frees resources and prepare the object for deactivation.
     */
    fun dispose()

    /**
     * Returns an array of permission needed by this actuator to be used.
     */
    fun neededPermissions(): Array<String>
}