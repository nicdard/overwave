package it.unipi.di.sam.overwave.receiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.unipi.di.sam.overwave.database.TransmissionDatabaseDao

/**
 * This is pretty much boiler plate code for a ViewModel Factory.
 *
 * Provides the and context to the ViewModel.
 */
class ReceiveViewModelFactory(private val dataSource: TransmissionDatabaseDao) : ViewModelProvider.Factory {
    @Suppress("unchecked_cast")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReceiveViewModel::class.java)) {
            return ReceiveViewModel(dataSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

