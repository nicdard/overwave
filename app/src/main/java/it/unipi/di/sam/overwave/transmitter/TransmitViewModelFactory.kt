package it.unipi.di.sam.overwave.transmitter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.unipi.di.sam.overwave.utils.Preferences


/**
 * This is pretty much boiler plate code for a ViewModel Factory.
 *
 * Provides the and context to the ViewModel.
 */
class TransmitViewModelFactory(
    private val preferences: Preferences
) : ViewModelProvider.Factory {
    @Suppress("unchecked_cast")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransmitViewModel::class.java)) {
            return TransmitViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

