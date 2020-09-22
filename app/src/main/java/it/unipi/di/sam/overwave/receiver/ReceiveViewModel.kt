package it.unipi.di.sam.overwave.receiver

import androidx.lifecycle.*
import it.unipi.di.sam.overwave.database.Transmission
import it.unipi.di.sam.overwave.database.TransmissionDatabaseDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReceiveViewModel(
    private val database: TransmissionDatabaseDao,
) : ViewModel() {

    private var currentTransmission = MutableLiveData<Transmission?>()
    val transmissions = database.getAllTransmissions()

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
            var night = database.getCurrent()
            if (night?.endTimeMillis != night?.endTimeMillis) {
                night = null
            }
            night
        }
    }

}

/*
class ReceiveViewModel(
    val preferences: Preferences,
    private val database: TransmissionDatabaseDao,
    private val sensorFactory: SensorFactory,
    private val storageDir: String?
) : ViewModel() {

    private var sensor: ISensor? = null

    private val _isReceiving = MutableLiveData(false)
    val isStopButtonVisible: LiveData<Boolean>
        get() = _isReceiving

    //  val isStartButtonVisible: LiveData<Boolean> = Transformations.map(isStopButtonVisible) { !it }
    val isStartButtonVisible: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        val observer = Observer<Boolean> {
            this.value = (isStopButtonVisible.value == false && preferences.useBluetooth.value == false)
                    || preferences.useBluetooth.value == true
        }
        addSource(isStopButtonVisible, observer)
        addSource(preferences.useBluetooth, observer)
    }

    val receivedText = MutableLiveData("")

    private var currentTransmission = MutableLiveData<Transmission?>()
    val transmissions = database.getAllTransmissions()

    fun startReceive() {
        /*
       OLD
       _doneReceiving.value = true
       receivedText.value = ""
        */
        _isReceiving.value = true
        // Eventually dispose the other sensor.
        sensor?.dispose()
        // Get the current sensor
        sensor = sensorFactory.get()

        // new
        viewModelScope.launch {
            val transmission = Transmission(
                wave = toWaveId(preferences.wave.value!!),
                frequency = preferences.frequency.value!!
            )
            insert(transmission)
            currentTransmission.value = getCurrentFromDatabase()
        }

    }

    fun stopReceive() {
        // receivedText.value = ""
        // _doneReceiving.value = false
        sensor?.let { sensor ->
            sensor.stop()
            viewModelScope.launch {
                val oldTransmission = currentTransmission.value ?: return@launch
                oldTransmission.endTimeMillis = System.currentTimeMillis()
                if (preferences.shouldSaveRawData.value!!) {
                    withContext(Dispatchers.IO) {
                        if (storageDir != null)
                            sensor.writeRawData(storageDir)
                    }
                }
                val decodedMessage = sensor.decodeSignal(oldTransmission.frequency)
                oldTransmission.decodedMessage = decodedMessage
                update(oldTransmission)
                _isReceiving.value = false
            }
        }
        // viewModelScope.coroutineContext.cancelChildren()
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
            var night = database.getCurrent()
            if (night?.endTimeMillis != night?.endTimeMillis) {
                night = null
            }
            night
        }
    }

    /*
    private val receiveHandler = object : Handler(Looper.getMainLooper()) {
        var frequency: Int = DEFAULT_FREQUENCY
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)
                    try {
                        ReceiveActivity.log("Received $readMessage")
                        val lines = readMessage.lineSequence()
                        when (lines.elementAt(0)) {
                            START_TRANSMISSION -> {
                                val configMap = lines
                                    .drop(1)
                                    .map { it.split(":") }
                                    .associate { it[0] to it[1] }
                                // Start receiver.
                                val wave = configMap[KEY_WAVE]
                                // Used for data gathering purposes.
                                val text = configMap[KEY_TEXT]
                                frequency = try {
                                    configMap[KEY_FREQUENCY]!!.toInt()
                                } catch (e: Exception) {
                                    DEFAULT_FREQUENCY
                                }
                                if (wave != null) {
                                    // sensor.activate()
                                    this@BluetoothSyncService.write(composeResponse(ACK, START_TRANSMISSION))
                                } else {
                                    this@BluetoothSyncService.write(composeResponse(NACK, START_TRANSMISSION))
                                }
                            }
                            END_TRANSMISSION -> {
                                // launch { stop(frequency) }
                            }
                            END_TRIALS -> {
                                // binding.viewModel?.stopReceive()
                            }
                            else -> this@BluetoothSyncService.write(
                                composeResponse(
                                    NACK,
                                    lines.elementAt(0)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        this@BluetoothSyncService.write((NACK + '\n' + e.message).toByteArray())
                        this@BluetoothSyncService.stop()
                    }
                }
                MESSAGE_DEVICE_NAME -> {
                    ReceiveActivity.log("Received ${msg.data.getString(DEVICE_NAME)}")
                    // save the connected device's name
                    /* Toast.makeText(
                        this@BluetoothSyncService,
                        "Connected to ${msg.data.getString(DEVICE_NAME)}",
                        Toast.LENGTH_SHORT
                    ).show() */
                }
                MESSAGE_TOAST -> {
                    ReceiveActivity.log("Received ${msg.data.getString(TOAST)}")
                    /* Toast.makeText(
                        this@ReceiveActivity,
                        msg.data.getString(TOAST),
                        Toast.LENGTH_LONG
                    ).show() */
                }
                MESSAGE_DISCONNECTED -> {
                    // ReceiveActivity.log("Received disconnected")
                    // binding.viewModel?.stopReceive()
                    /* Toast.makeText(
                        this@ReceiveActivity,
                        msg.data.getString(TOAST),
                        Toast.LENGTH_SHORT
                    ).show()
                    */
                }
            }
        }
    }

     */
    /*
    fun addSample(sample: LuminosityData) {
        samples.add(sample)
        receivedText.value += sample.intensity
    }

     */

    /*
    private fun a() {
        val firstEnvironmentalLuminosityThreshold = samples[0].intensity + ERROR_THRESHOLD
        val intensities = samples.map { it.intensity }
        val minMaxMean = (intensities.maxOrNull()!! - intensities.minOrNull()!!) / 2
        val stats = RunningStats()
        intensities.forEach {
            stats.push(it.toBigDecimal())
        }
        val mean = stats.mean().toLong()
        var environmentalLuminosityThreshold = firstEnvironmentalLuminosityThreshold
        if (mean - firstEnvironmentalLuminosityThreshold in firstEnvironmentalLuminosityThreshold until minMaxMean) {
            environmentalLuminosityThreshold = mean - firstEnvironmentalLuminosityThreshold
        }

        var informationSamples = samples.sortedBy { it.timestamp }.dropWhile { it.intensity < environmentalLuminosityThreshold }
        // Extract starting sequence.
        // Timestamp of the first value that is part of the initial sequence.
        val leadingOnes = informationSamples.takeWhile { it.intensity >  environmentalLuminosityThreshold }
        informationSamples = informationSamples.dropWhile { it.intensity >  environmentalLuminosityThreshold }
        val firstZero = informationSamples.takeWhile { it.intensity <= environmentalLuminosityThreshold }
        informationSamples = informationSamples.dropWhile { it.intensity <= environmentalLuminosityThreshold }
        val firstOne = informationSamples.takeWhile { it.intensity > environmentalLuminosityThreshold }
        informationSamples = informationSamples.dropWhile { it.intensity > environmentalLuminosityThreshold }
        val secondZero = informationSamples.takeWhile { it.intensity <= environmentalLuminosityThreshold }
        informationSamples = informationSamples.dropWhile { it.intensity <= environmentalLuminosityThreshold }
        val secondOne = informationSamples.takeWhile { it.intensity > environmentalLuminosityThreshold }
        informationSamples = informationSamples.dropWhile { it.intensity > environmentalLuminosityThreshold }
        // Calculate timings.
        val onesTiming = (leadingOnes.first().timestamp - leadingOnes.last().timestamp)
        val firstZeroTiming = (firstZero.first().timestamp - firstZero.last().timestamp)
        val firstOneTiming = (firstOne.first().timestamp - firstOne.last().timestamp)
        val secondZeroTiming = (secondZero.first().timestamp - secondZero.last().timestamp)
        val secondOneTiming = (secondOne.first().timestamp - secondOne.last().timestamp)



        var i = 0
        while (i < informationSamples.size) {

        }
    }
    */

    /*
    private suspend fun decodeSignal() {
        var decoded = ""
        withContext(Dispatchers.Default) {
            if (samples.isNotEmpty()) {
                // Calibrate.
                val stats = RunningStats()
                val intensities = samples.map { it.intensity }
                val firstEnvironmentNoise = intensities[0] + ERROR_THRESHOLD
                stats.push(intensities.minOrNull()!!.toBigDecimal())
                stats.push(intensities.maxOrNull()!!.toBigDecimal())
                val minMaxMean = stats.mean().toLong()
                val _samples: MutableList<LuminosityData> =
                    samples.dropWhile { it.intensity < firstEnvironmentNoise }.toMutableList()
                // Group by frequency(with error considerations) and calculate mean.
                // TODO should be done with a sequence or told by the transmitter via bluetooth.
                val delayedFrequency = (DEFAULT_FREQUENCY + FLASH_LATENCY) * 1000000
                val sampleMeanByFrequency = mutableMapOf<String, BigDecimal>()
                while (isActive && _samples.isNotEmpty()) {
                    val intervalStartTimestamp = _samples[0].timestamp
                    stats.clear()
                    // Compute the interval
                    var data: LuminosityData
                    do {
                        data = _samples[0]
                        stats.push(data.intensity.toBigDecimal())
                        _samples.removeAt(0)
                    } while (isActive && (data.timestamp - intervalStartTimestamp) <= delayedFrequency && _samples.isNotEmpty())
                    sampleMeanByFrequency["$intervalStartTimestamp + ${data.timestamp}"] = stats.mean()
                }
                val threshold = minMaxMean.toBigDecimal()
                val bits = sampleMeanByFrequency.entries
                    .sortedBy { it.key }
                    .map { if (it.value > threshold) '1' else '0' }
                    .joinToString(separator = "")
                decoded = decode(bits)
            }
        }
        withContext(Dispatchers.Main) {
            receivedText.value = decoded
        }
    }

     */

    /*
    private suspend fun decodeSignal() {
        withContext(Dispatchers.Default) {
            if (samples.isNotEmpty()) {
                // Calibrate.
                val stats = RunningStats()
                val intensities = samples.map { it.intensity }
                val firstEnvironmentNoise = intensities[0] + ERROR_THRESHOLD
                stats.push(intensities.minOrNull()!!.toBigDecimal())
                stats.push(intensities.maxOrNull()!!.toBigDecimal())
                val minMaxMean = stats.mean().toLong()
                val _samples: MutableList<LuminosityData> =
                    samples.dropWhile { it.intensity < firstEnvironmentNoise }.toMutableList()
                // Group by frequency(with error considerations) and calculate mean.
                // TODO should be done with a sequence or told by the transmitter via bluetooth.
                val delayedFrequency = (DEFAULT_FREQUENCY + FLASH_LATENCY) * 1000000
                val sampleMeanByFrequency = mutableMapOf<String, BigDecimal>()
                val threshold = minMaxMean.toBigDecimal()
                while (isActive && _samples.isNotEmpty()) {
                    // Go inside the leading 1111 sequence.
                    val timestamp  = _samples[0].timestamp
                    _samples.dropWhile { it.timestamp < timestamp + 300 * 100000 }
                    _samples.dropWhile { it.intensity > firstEnvironmentNoise }
                    val intervalStartTimestamp = _samples[0].timestamp
                    stats.clear()
                    // Compute the interval
                    var data: LuminosityData
                    do {
                        data = _samples[0]
                        stats.push(data.intensity.toBigDecimal())
                        _samples.removeAt(0)
                    } while (isActive && (data.timestamp - intervalStartTimestamp) <= delayedFrequency && _samples.isNotEmpty())
                    sampleMeanByFrequency["$intervalStartTimestamp + ${data.timestamp}"] = stats.mean()
                    if (sampleMeanByFrequency.size == 24) {
                        val bits = sampleMeanByFrequency.entries
                            .sortedBy { it.key }
                            .map { if (it.value > threshold) '1' else '0' }
                            .joinToString(separator = "")
                        val decodedByte = decode(bits)
                        withContext(Dispatchers.Main) {
                            receivedText.value = receivedText.value + decodedByte
                        }
                        sampleMeanByFrequency.clear()
                    }
                }
            }
        }
    }*/

}
*/