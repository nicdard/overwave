package it.unipi.di.sam.overwave

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.view.View
import android.widget.Toast
import androidx.lifecycle.*
import it.unipi.di.sam.overwave.transmissions.bluetooth.*
import it.unipi.di.sam.overwave.transmissions.receivers.OnReceivedListener
import kotlinx.coroutines.*
import java.lang.IllegalArgumentException
import java.lang.NumberFormatException
import kotlin.Exception

/**
 * Use an application aware view model provides us with a context to use with our singletons.
 */
class ReceiverViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The dir where to save data recording.
     */
    val storageDir: String
        get() = (getApplication() as Application).getExternalFilesDir(null)!!.absolutePath

    private val _isSwitchBluetoothChecked = MutableLiveData<Boolean>(false)
    val isSwitchBluetoothChecked: LiveData<Boolean>
        get() = _isSwitchBluetoothChecked

    private val hasBluetoothSupport = BluetoothAdapter.getDefaultAdapter() != null
    val bluetoothSwitchVisibility = if (hasBluetoothSupport) View.VISIBLE else View.GONE
    val isBluetoothEnabled: LiveData<Boolean> = Transformations.map(isSwitchBluetoothChecked) { it && hasBluetoothSupport}

    private val _checkedRadioButtonWave = MutableLiveData<Int>(R.id.receiver_radio_button_vibration)
    val checkedRadioButtonWave: LiveData<Int>
        get() = _checkedRadioButtonWave

    val frequency = MutableLiveData<String>("40")

    private val receiver = Transformations.map(checkedRadioButtonWave) { getReceiver(it) }
    private val _decoded = MutableLiveData<String?>()
    val decoded: LiveData<String?>
        get() = _decoded
    val showReceivedMessage = Transformations.map(decoded) {
        if (it != null) View.VISIBLE else View.GONE
    }

    fun onWaveCheckedChanged(wave: String) = when(wave) {
        "vibration" -> R.id.receiver_radio_button_vibration
        "light" -> R.id.receiver_radio_button_light
        "sound" -> R.id.receiver_radio_button_sound
        else -> throw IllegalArgumentException("Unknown")
    }
    fun onWaveCheckedChanged(id: Int) {
        _checkedRadioButtonWave.value = id
    }
    fun onWaveCheckedChanged(view: View?, isChecked: Boolean) {
        if (isChecked && view != null) {
            _checkedRadioButtonWave.value = view.id
        }
    }

    fun onChangeSwitchBluetooth(isChecked: Boolean) {
        _isSwitchBluetoothChecked.value = isChecked
    }

    fun switchToManualConfiguration() {
        _isSwitchBluetoothChecked.value = false
    }

    private fun getFrequency() = try { frequency.value!!.toInt() } catch (e: Exception) { 40 }

    fun startReceiver(shouldRecord: Boolean) {
        viewModelScope.launch {
            if (shouldRecord) {
                receiver.value?.record(getApplication(), storageDir, getFrequency())
            } else {
                receiver.value?.start(getApplication(), getFrequency())
            }
            processData()
        }
    }

    suspend fun processData() {
        withContext(Dispatchers.IO) {
            while (receiver.value != null && isActive) {
                delay(1000)
                receiver.value
            }
        }
    }

    fun stopReceiver() {
        receiver.value?.stop()
    }

     fun onReceived(data: ByteArray) {
        // Update UI TODO
        _decoded.value = String(data)
    }
}