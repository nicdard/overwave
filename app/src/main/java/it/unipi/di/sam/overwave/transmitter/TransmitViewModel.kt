package it.unipi.di.sam.overwave.transmitter

import androidx.lifecycle.*

class TransmitViewModel  : ViewModel() {

    private val _progressStatus = MutableLiveData(0)
    val progressStatus: LiveData<Int>
        get() = _progressStatus

    private val _isStarted = MutableLiveData(false)
    val isStarted: LiveData<Boolean>
        get() = _isStarted

    val hasPermissions = MutableLiveData(false)

    val startButtonVisible = MediatorLiveData<Boolean>().apply {
        val andObserver = Observer<Boolean> {
            this.value = isStarted.value == false && hasPermissions.value == true
        }
        addSource(isStarted, andObserver)
        addSource(hasPermissions, andObserver)
    }

    val stopButtonVisible: LiveData<Boolean> = Transformations.map(startButtonVisible) { !it }

    fun updatePermission(value: Boolean) {
        hasPermissions.value = value
    }

    fun startTransmitter() {
        _isStarted.value = true
    }

    fun stopTransmitter() {
        _isStarted.value = false
    }

    fun publishProgress(progressStatus: Int) {
        this._progressStatus.postValue(progressStatus)
    }
}