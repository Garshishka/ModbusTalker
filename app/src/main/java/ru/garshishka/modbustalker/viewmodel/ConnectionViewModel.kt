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
import ru.garshishka.modbustalker.data.CommandToSend
import ru.garshishka.modbustalker.data.RegisterOutput
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.data.enums.ConnectionStatus
import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.utils.SingleLiveEvent
import ru.garshishka.modbustalker.utils.errors.NotFoundTransactionNumberErrorException
import ru.garshishka.modbustalker.utils.errors.ResponseErrorException
import ru.garshishka.modbustalker.utils.getTransactionAndFunctionNumber
import ru.garshishka.modbustalker.utils.makeByteArrayForAnalogueOut
import ru.garshishka.modbustalker.utils.makeByteArrayForValueChange
import ru.garshishka.modbustalker.utils.read1ByteFromBuffer

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

    private val _transactionNotFoundError = SingleLiveEvent<Int>()
    val transactionNotFoundError: LiveData<Int>
        get() = _transactionNotFoundError
    private val _registerWatchError = SingleLiveEvent<String>()
    val registerWatchError: LiveData<String>
        get() = _registerWatchError
    private val _registerResponseError = SingleLiveEvent<Pair<Int, Int>>()
    val registerResponseError: LiveData<Pair<Int, Int>>
        get() = _registerResponseError

    private val byteArraysToSend: MutableList<CommandToSend> = mutableListOf()
    private var transactionNumber: UShort = 0u
    private var registerCardNumber = 0

    val watchedRegisters: LiveData<List<RegisterOutput>> =
        repository.getAll().map { list -> list.map { it.toDto() } }

    private var sendingAndReading = false
    private var commandArrayBusy = false

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
            //TODO For future probably no need
            clearRegisterTable()

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
            sendingAndReading = false
            waitForCommandArrayToFree()
            _connectionStatus.value = ConnectionStatus.WORKING
            withContext(Dispatchers.IO) {
                socket.close()
            }
            changeStatusOfConnection(
                "Socket disconnected",
                _connectionStatus,
                ConnectionStatus.DISCONNECTED
            )
            _communicatingStatus.value = ConnectionStatus.DISCONNECTED
        } catch (e: Exception) {
            logError("Disconnection error $e")
            logError(e.message.toString())
        }
    }

    fun clearRegisterTable() {
        repository.deleteAll()
    }

    fun addWatchedRegister(registerName: String, registerAddress: Int, outputType: OutputType) =
        viewModelScope.launch {
            repository.save(
                RegisterOutput(
                    registerCardNumber,
                    registerName,
                    registerAddress,
                    transactionNumber = transactionNumber.toInt(),
                    outputType = outputType,
                )
            )
            waitForCommandArrayToFree()
            byteArraysToSend.add(
                CommandToSend(
                    transactionNumber.toInt(),
                    makeByteArrayForAnalogueOut(
                        registerAddress,
                        if (outputType == OutputType.INT16 || outputType == OutputType.UINT16) 1 else 2,
                        transactionNumber
                    ),
                    registerAddress,
                    outputType,
                    0x03 //TODO make 3 or 4 depending on the function
                )
            )
            beginSendingAndReceivingMessages()
            logDebug("added register $registerAddress with transaction $transactionNumber to watch")
            registerCardNumber++
            transactionNumber++
        }

    fun sendNewValueToRegister(registerAddress: Int, outputType: OutputType, newValue: Number) =
        viewModelScope.launch {
            waitForCommandArrayToFree()
            //TODO change according to OutputType
            byteArraysToSend.add(
                CommandToSend(
                    transactionNumber.toInt(),
                    makeByteArrayForValueChange(
                        registerAddress,
                        newValue.toInt(),
                        transactionNumber
                    ),
                    registerAddress,
                    outputType,
                    0x10
                )
            )
            logDebug("send $newValue to register $registerAddress with transaction $transactionNumber to set")
            transactionNumber++
        }

    fun updateRegister(newRegisterOutput: RegisterOutput) = viewModelScope.launch{
        repository.save(newRegisterOutput)
    }

    private fun sendingAndReadingMessages() = viewModelScope.launch {
        while (sendingAndReading) {
            commandArrayBusy = true
            byteArraysToSend.forEach { message ->
                try {
                    _communicatingStatus.value = ConnectionStatus.WORKING
                    sendChannel.writeFully(message.command, 0, message.command.size)

                    waitingForResponse(message)

                    _communicatingStatus.value = ConnectionStatus.CONNECTED
                } //TODO add register number
                //TODO make them paused, not delete outright
                catch (e: NotFoundTransactionNumberErrorException) {
                    byteArraysToSend.remove(message)
                    logError("Error transaction not found in DB $e")
                    logError(e.message.toString())
                    _transactionNotFoundError.postValue(e.transactionNumber)
                } catch (e: ResponseErrorException) {
                    byteArraysToSend.remove(message)
                    logError("Error response $e")
                    logError(e.message.toString())
                    _registerResponseError.postValue(e.errorCode to e.registerNumber)
                } catch (e: Exception) {
                    byteArraysToSend.remove(message)
                    logError("Error sending or receiving $e")
                    logError(e.message.toString())
                    //TODO change error message
                    _registerWatchError.postValue(e.message ?: e.toString())
                }
            }
            commandArrayBusy = false
            //TODO made this delay configurable
            delay(500)
        }
    }

    private suspend fun waitingForResponse(commandToSend: CommandToSend) {
        var waitingForAnswer = 0
        var gotCorrectResponse = false
        //Waiting to receive a respond  with the same transaction number
        //TODO made waitingForAnswer and timeout configurable
        while (waitingForAnswer < 5 && !gotCorrectResponse) {
            val response = ByteArray(
                if (commandToSend.functionNumber == 0x10) 12
                else (9 + if (commandToSend.outputType == OutputType.INT16
                    || commandToSend.outputType == OutputType.UINT16
                ) 2 else 4)
            )
            receiveChannel.readAvailable(response)
            val output = response.getTransactionAndFunctionNumber()
            if (output.first == commandToSend.transactionNumber) {
                gotCorrectResponse = true
                //If transaction № is right
                if (output.second == commandToSend.functionNumber) {
                    var outputString = ""
                    response.forEach { outputString += "${it.toUByte()}, " }
                    logResponse(outputString)
                    when (commandToSend.functionNumber) {
                        0x3 -> receivedResponseForAnalogueOutput(output.first, response)
                        0x10 -> receivedResponseForChangeValue(commandToSend)
                        //TODO probably change to particular error
                        else -> throw Exception("Unknown function number")
                    }
                } else {
                    //If function number NOT RIGHT
                    byteArraysToSend.remove(commandToSend)
                    if (checkIfErrorNumber(commandToSend.functionNumber, output.second)) {
                        throw ResponseErrorException(
                            response.read1ByteFromBuffer(8),
                            commandToSend.registerAddress
                        )
                    } else {
                        throw ResponseErrorException(-1, commandToSend.registerAddress)
                    }
                }
            } else {
                delay(250)
                waitingForAnswer++
            }
        } //If we waited and didn't got the right response
        if (!gotCorrectResponse) {
            logError("skipped transaction $transactionNumber")
        }
    }

    private fun receivedResponseForChangeValue(commandToSend: CommandToSend) {
        byteArraysToSend.remove(commandToSend)
    }

    private suspend fun receivedResponseForAnalogueOutput(
        transactionNumber: Int,
        response: ByteArray
    ) {
        repository.updateValue(transactionNumber, response)
    }

    fun deleteWatchedRegister(registerToDeleteNumber: Int) = viewModelScope.launch {
        watchedRegisters.value?.let { list ->
            val registerToDelete = list[registerToDeleteNumber]
            waitForCommandArrayToFree()
            byteArraysToSend.removeIf { it.command.getTransactionAndFunctionNumber().first == registerToDelete.transactionNumber }
            repository.delete(registerToDelete.address)
        }
    }

    fun checkRegisterByAddress(address: Int): Boolean =
        repository.getRegisterByAddress(address) != null


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
        addToDebugText("$message\n")
    }

    private fun logResponse(message: String) {
        Log.i("ModBus", message)
        addToDebugText("RESPONSE $message\n")
    }

    private fun logError(message: String) {
        Log.e("ModBus", message)
        addToDebugText("!ERROR! $message\n")
    }

    private fun addToDebugText(message: String) {
        _debugText.value?.let {
            _debugText.value = (message + it).lines().takeLast(100).joinToString("\n")
        }
    }

    private fun checkIfErrorNumber(sent: Int, response: Int): Boolean =
        response - sent == 128

    private suspend fun waitForCommandArrayToFree() {
        while (commandArrayBusy)
            delay(100)
    }
}