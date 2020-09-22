package it.unipi.di.sam.overwave.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.preference.PreferenceManager

abstract class SharedPreferenceLiveData<T>(
    protected val sharedPrefs: SharedPreferences,
    private val key: String,
    private val defValue: T) : LiveData<T>() {

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == this.key) {
            value = getValueFromPreferences(key, defValue)
        }
    }

    abstract fun getValueFromPreferences(key: String, defValue: T): T

    override fun onActive() {
        super.onActive()
        value = getValueFromPreferences(key, defValue)
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onInactive() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onInactive()
    }
}

class SharedPreferenceIntStringLiveData(sharedPrefs: SharedPreferences, key: String, defValue: Int) :
    SharedPreferenceLiveData<Int>(sharedPrefs, key, defValue) {
    override fun getValueFromPreferences(key: String, defValue: Int): Int = try {
        sharedPrefs.getString(key, defValue.toString())!!.toInt()
    } catch (e: Exception) { defValue }
}

class SharedPreferenceStringLiveData(sharedPrefs: SharedPreferences, key: String, defValue: String) :
    SharedPreferenceLiveData<String>(sharedPrefs, key, defValue) {
    override fun getValueFromPreferences(key: String, defValue: String): String = sharedPrefs.getString(key, defValue) ?: defValue
}

class SharedPreferenceBooleanLiveData(sharedPrefs: SharedPreferences, key: String, defValue: Boolean) :
    SharedPreferenceLiveData<Boolean>(sharedPrefs, key, defValue) {
    override fun getValueFromPreferences(key: String, defValue: Boolean): Boolean = sharedPrefs.getBoolean(key, defValue)
}

fun SharedPreferences.intStringLiveData(key: String, defValue: Int): SharedPreferenceLiveData<Int> {
    return SharedPreferenceIntStringLiveData(this, key, defValue)
}

fun SharedPreferences.stringLiveData(key: String, defValue: String): SharedPreferenceLiveData<String> {
    return SharedPreferenceStringLiveData(this, key, defValue)
}

fun SharedPreferences.booleanLiveData(key: String, defValue: Boolean): SharedPreferenceLiveData<Boolean> {
    return SharedPreferenceBooleanLiveData(this, key, defValue)
}
/*
class Preferences(context: Context) {

    companion object {
        private const val KEY_WAVE_LIST = "wave_list"
        private const val KEY_DATA_CSV = "data_CSV"
        private const val KEY_DATABASE = "database"
        private const val KEY_BLUETOOTH = "sync"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_TRIALS = "trials"
    }

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val wave: LiveData<String> = sharedPreferences.stringLiveData(KEY_WAVE_LIST, "light")
    val shouldSaveRawData: LiveData<Boolean> = sharedPreferences.booleanLiveData(KEY_DATA_CSV, false)
    val storeTransmissions: LiveData<Boolean> = sharedPreferences.booleanLiveData(KEY_DATABASE, false)
    val useBluetooth: LiveData<Boolean> = sharedPreferences.booleanLiveData(KEY_BLUETOOTH, false)
    val frequency: LiveData<Int> = sharedPreferences.intStringLiveData(KEY_FREQUENCY, DEFAULT_FREQUENCY)
    val trials: LiveData<Int> = sharedPreferences.intStringLiveData(KEY_TRIALS, 1)
}
*/

/**
 * Application preferences wrapper.
 */
class Preferences(context: Context) {

    companion object {
        private const val KEY_WAVE_LIST = "wave_list"
        private const val KEY_DATA_CSV = "data_CSV"
        private const val KEY_DATABASE = "database"
        private const val KEY_BLUETOOTH = "sync"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_TRIALS = "trials"
    }

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val wave: String
        get() = sharedPreferences.getString(KEY_WAVE_LIST, "light")!!
    val shouldSaveRawData: Boolean
        get() = sharedPreferences.getBoolean(KEY_DATA_CSV, false)
    val useBluetooth: Boolean
        get() = sharedPreferences.getBoolean(KEY_BLUETOOTH, false)
    val frequency: Int
        get() = try {
            sharedPreferences.getString(KEY_FREQUENCY, DEFAULT_FREQUENCY.toString())!!.toInt()
        } catch (e: Exception) { DEFAULT_FREQUENCY }
    val trials: Int
        get() = try {
            sharedPreferences.getString(KEY_TRIALS, "2")!!.toInt()
        } catch (e: Exception) { 2 }
}
