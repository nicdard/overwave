package it.unipi.di.sam.overwave.receiver

import androidx.lifecycle.*
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import it.unipi.di.sam.overwave.database.Transmission
import it.unipi.di.sam.overwave.database.TransmissionDatabaseDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReceiveViewModel(
    private val database: TransmissionDatabaseDao,
) : ViewModel() {

    private var currentTransmission = MutableLiveData<Transmission?>()

    val transmissions = LivePagedListBuilder(
        database.getAllPaged(),
        PagedList.Config.Builder()
            .setEnablePlaceholders(false)
            .setPageSize(50)
            .build()
    ).build()

    private val _isReceiving = MutableLiveData(false)
    val isReceiving: LiveData<Boolean>
        get() = _isReceiving
    val isStartButtonEnabled: LiveData<Boolean> = Transformations.map(isReceiving) { !it }
    val isCleanButtonEnabled: LiveData<Boolean> = Transformations.map(transmissions) { it?.isNotEmpty() == true }

    fun onStartButtonClicked() {
        _isReceiving.value = true
    }

    fun onStopButtonClicked() {
        _isReceiving.value = false
    }

    fun startReceive(wave: String, frequency: Int, sentMessage: String? = null) {
        viewModelScope.launch {
            val transmission = Transmission(
                wave = toWaveId(wave),
                frequency = frequency,
                sentMessage = sentMessage
            )
            insert(transmission)
            currentTransmission.value = getCurrentFromDatabase()
        }
    }

    fun stopReceive(decodedMessage: String) {
        viewModelScope.launch {
            val oldTransmission = currentTransmission.value ?: return@launch
            oldTransmission.endTimeMillis = System.currentTimeMillis()
            oldTransmission.decodedMessage = decodedMessage
            update(oldTransmission)
        }
    }

    fun clean() {
        viewModelScope.launch {
            clear()
            currentTransmission.value = null
        }
    }

    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }

    private suspend fun update(transmission: Transmission) {
        withContext(Dispatchers.IO) {
            database.update(transmission)
        }
    }

    private suspend fun insert(transmission: Transmission) {
        withContext(Dispatchers.IO) {
            database.insert(transmission)
        }
    }

    private suspend fun getCurrentFromDatabase(): Transmission? {
        return withContext(Dispatchers.IO) {
            var transmission = database.getCurrent()
            if (transmission?.endTimeMillis != transmission?.endTimeMillis) {
                transmission = null
            }
            transmission
        }
    }

}