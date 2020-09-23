package it.unipi.di.sam.overwave.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Application preferences wrapper.
 */
class Preferences private constructor(applicationContext: Context) {

    companion object {
        @Volatile
        private var INSTANCE: Preferences? = null

        fun getInstance(applicationContext: Context): Preferences {
            var instance = INSTANCE
            if (instance == null) {
                instance = Preferences(applicationContext)
                INSTANCE = instance
            }
            return instance
        }

        private const val KEY_WAVE_LIST = "wave_list"
        private const val KEY_DATA_CSV = "data_CSV"
        private const val KEY_BLUETOOTH = "sync"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_TRIALS = "trials"
    }

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val wave: String
        get() = sharedPreferences.getString(KEY_WAVE_LIST, "light")!!
    val shouldSaveRawData: Boolean
        get() = sharedPreferences.getBoolean(KEY_DATA_CSV, false)
    val useBluetooth: Boolean
        get() = sharedPreferences.getBoolean(KEY_BLUETOOTH, false)
    val frequency: Int
        get() = try {
            sharedPreferences.getString(KEY_FREQUENCY, getDefaultFrequency(this.wave).toString())!!.toInt()
        } catch (e: Exception) { getDefaultFrequency(this.wave) }
    val trials: Int
        get() = try {
            sharedPreferences.getString(KEY_TRIALS, "2")!!.toInt()
        } catch (e: Exception) { 2 }
}
