package it.unipi.di.sam.overwave.actuators

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

abstract class BaseActuator(
    protected val shouldSaveRawData: Boolean = false,
    private val storageDir: String?
) : IActuator {

    private var writer: FileWriter? = null
    private val timestamps = mutableListOf<Long>()

    override fun initialise() {
        if (shouldSaveRawData && storageDir != null && writer == null) {
            writer = try {
                FileWriter(File(storageDir, getRawFilename()))
            } catch (e: Exception) {
                null
            }
        }
    }

    protected abstract fun getRawFilename(): String
    protected fun storeTimestamp() = timestamps.add(System.currentTimeMillis())
    protected fun cleanTimestamps() = timestamps.clear()
    protected suspend fun writeRawData() {
        withContext(Dispatchers.IO) {
            writer?.run {
                timestamps.forEach {
                    write(String.format("%d \n", it))
                }
                cleanTimestamps()
            }
        }
    }

    /**
     * Provides a base implementation for all actuators which do not need runtime permissions:
     * in those cases we can return a vacuous true value.
     */
    override fun neededPermissions(): Array<String> = arrayOf()

    override fun dispose() {
        try {
            writer?.close()
        } catch (e: Exception) {}
        writer = null
    }
}