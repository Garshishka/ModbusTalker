package ru.garshishka.modbustalker.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.garshishka.modbustalker.data.RegisterOutput
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.data.enums.ConnectionStatus
import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.utils.SingleLiveEvent
import ru.garshishka.modbustalker.utils.errors.ResponseErrorException
import ru.garshishka.modbustalker.utils.getOutput
import ru.garshishka.modbustalker.utils.getTransactionAndFunctionNumber
import ru.garshishka.modbustalker.utils.makeByteArrayForAnalogueOut
import ru.garshishka.modbustalker.utils.read1ByteFromBuffer
import ru.garshishka.modbustalker.utils.read2BytesFromBuffer

class ConnectionViewModel(private val repository: RegistryOutputRepository) : ViewModel() {
    private val _connectionStatus = MutableLiveData(ConnectionStatus.DISCONNECTED)
    val connectionStatus: LiveData<ConnectionStatus>
        get() = _connectionStatus

    //TODO probably made them individual to registers
    private val _communicatingStatus = MutableLiveData(ConnectionStatus.DISCONNECTED)
    val communicatingStatus: LiveData<ConnectionStatus>
        get() = _communicatingStatus


    //TODO make debug text specific line length
    private val _debugText = MutableLiveData<String>()
    val debugText: LiveData<String>
        get() = _debugText

    private val _registerWatchError = SingleLiveEvent<String>()
    val registerWatchError: LiveData<String>
        get() = _registerWatchError
    private val _registerResponseError = SingleLiveEvent<Int>()
    val registerResponseError: LiveData<Int>
        get() = _registerResponseError

    private val byteArraysToSend: MutableList<ByteArray> = mutableListOf()
    private var transactionNumber: UShort = 0u

    val watchedRegisters: LiveData<List<RegisterOutput>> =
        repository.getAll().map { list -> list.map { it.toDto() } }

    private var sendingAndReading = false

    private val selectorManager = SelectorManager(Dispatchers.IO)
    private lateinit var socket: Socket
    private lateinit var receiveChannel: ByteReadChannel
    private lateinit var sendChannel: ByteWriteChannel

    init {
        _debugText.value = ""
    }

    fun connect(ip: String, port: String) = viewModelScope.launch {
        logDebug("connecting to socket $ip and ${port.toInt()}")
        try {
            _connectionStatus.value = ConnectionStatus.WORKING
            socket = aSocket(selectorManager).tcp().connect(ip, port.toInt())
            changeStatusOfConnection(
                "Socket connected",
                _connectionStatus,
                ConnectionStatus.CONNECTED
            )
            receiveChannel = socket.openReadChannel()
            sendChannel = socket.openWriteChannel(true)
            _communicatingStatus.value = ConnectionStatus.CONNECTED
        } catch (e: Exception) {
            logError("Connection error $e")
            logError(e.message.toString())
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _communicatingStatus.value = ConnectionStatus.DISCONNECTED
        }
        logDebug("socket ${connectionStatus.value.toString()}")
    }

    fun disconnect() = viewModelScope.launch {
        try {
            logDebug("Closing connection")
            _connectionStatus.value = ConnectionStatus.WORKING
            withContext(Dispatchers.IO) {
                socket.close()
            }
            changeStatusOfConnection(
                "Socket disconnected",
                _connectionStatus,
                ConnectionStatus.DISCONNECTED
            )
            //TODO For future probably no need
            repository.deleteAll()
            sendingAndReading = false
            _communicatingStatus.value = ConnectionStatus.DISCONNECTED
        } catch (e: Exception) {
            logError("Disconnection error $e")
            logError(e.message.toString())
        }
    }

    fun addWatchedRegister(registerAddress: Int, outputType: OutputType) = viewModelScope.launch {
        byteArraysToSend.add(makeByteArrayForAnalogueOut(registerAddress, 1, transactionNumber))
        beginSendingAndReceivingMessages()
        repository.save(
            RegisterOutput(
                registerAddress,
                transactionNumber = transactionNumber.toInt(),
                outputType = outputType,
            )
        )
        logDebug("added register $registerAddress with transaction $transactionNumber to watch")
        transactionNumber++
    }

    private fun sendingAndReadingMessages() = viewModelScope.launch {
        while (sendingAndReading) {
            byteArraysToSend.forEach { message ->
                val transactionNumber = message.read2BytesFromBuffer(0)
                val functionNumber = message.read1ByteFromBuffer(7)
                try {
                    _communicatingStatus.value = ConnectionStatus.WORKING
                    sendChannel.writeFully(message, 0, message.size)

                    var waitingForAnswer = 0
                    var gotCorrectResponse = false
                    //Waiting to receive a respond  with the same transaction number
                    //TODO made waitingForAnswer and timeout configurable
                    while (waitingForAnswer < 5 && !gotCorrectResponse) {
                        //TODO check for 4 bytes responses also
                        val response = ByteArray(9 + 2)
                        receiveChannel.readAvailable(response)
                        val output = response.getTransactionAndFunctionNumber()
                        if (output.first == transactionNumber) {
                            gotCorrectResponse = true
                            //Checking if we got a response and not an error
                            if (output.second == functionNumber) {
                                var outputString = ""
                                response.forEach { outputString += "${it.toUByte()}, " }
                                logResponse(outputString)
                                repository.updateValue(output.first, response.getOutput())
                            } else {
                                byteArraysToSend.remove(message)
                                if (checkIfErrorNumber(functionNumber, output.second)) {
                                    throw ResponseErrorException(message.read1ByteFromBuffer(8))
                                } else {
                                    throw ResponseErrorException(-1)
                                }
                            }
                        } else {
                            delay(250)
                            waitingForAnswer++
                        }
                    }
                    if (!gotCorrectResponse) {
                        logError("skipped transaction $transactionNumber")
                    }
                    _communicatingStatus.value = ConnectionStatus.CONNECTED
                } //TODO add register number
                catch (e: ResponseErrorException) {
                    logError("Error response $e")
                    logError(e.message.toString())
                    _registerResponseError.postValue(e.errorCode)
                } catch (e: Exception) {
                    logError("Error sending or receiving $e")
                    logError(e.message.toString())
                    //TODO change error message
                    _registerWatchError.postValue(e.message ?: e.toString())
                }
            }
            //TODO made this delay configurable
            delay(500)
        }
    }

    fun deleteWatchedRegister(registerToDeleteNumber: Int) = viewModelScope.launch {
        watchedRegisters.value?.let { list ->
            val registerToDelete = list[registerToDeleteNumber]
            byteArraysToSend.removeIf { it.getTransactionAndFunctionNumber().first == registerToDelete.transactionNumber }
            repository.delete(registerToDelete.address)
        }
    }

    fun checkRegisterByAddress(address: Int): Boolean =
        repository.checkRegisterByAddress(address)

    private fun changeStatusOfConnection(
        message: String,
        statusVar: MutableLiveData<ConnectionStatus>,
        newStatus: ConnectionStatus
    ) {
        logDebug(message)
        statusVar.value = newStatus
    }

    private fun beginSendingAndReceivingMessages() {
        if (!sendingAndReading) {
            sendingAndReading = true
            sendingAndReadingMessages()
        }
    }

    private fun logDebug(message: String) {
        Log.d("ModBus", message)
        _debugText.value = "$message\n" + _debugText.value
    }

    private fun logResponse(message: String) {
        Log.i("ModBus", message)
        _debugText.value = "RESPONSE $message\n" + _debugText.value
    }

    private fun logError(message: String) {
        Log.e("ModBus", message)
        _debugText.value = "!ERROR! $message\n" + _debugText.value
    }

    private fun checkIfErrorNumber(sent: Int, response: Int): Boolean =
        response - sent == 128
}