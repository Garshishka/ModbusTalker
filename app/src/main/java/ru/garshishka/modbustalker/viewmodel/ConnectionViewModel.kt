package ru.garshishka.modbustalker.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ru.garshishka.modbustalker.data.CommandToSend
import ru.garshishka.modbustalker.data.RegisterOutput
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.data.enums.ConnectionStatus
import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.data.enums.RegisterConnection
import ru.garshishka.modbustalker.utils.NotFoundTransactionNumberErrorException
import ru.garshishka.modbustalker.utils.RegisterWatchException
import ru.garshishka.modbustalker.utils.ResponseErrorException
import ru.garshishka.modbustalker.utils.getTransactionAndFunctionNumber
import ru.garshishka.modbustalker.utils.makeByteArrayForAnalogueOut
import ru.garshishka.modbustalker.utils.makeByteArrayForValueChange
import ru.garshishka.modbustalker.utils.read1ByteFromBuffer
import ru.garshishka.modbustalker.utils.setUpEmptyResponse
import ru.garshishka.modbustalker.viewmodel.interactor.ConnectionInteractor
import ru.garshishka.modbustalker.viewmodel.interactor.DebugInteractor
import ru.garshishka.modbustalker.viewmodel.interactor.LogType
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: RegistryOutputRepository,
    private val debugInteractor: DebugInteractor,
    private val connectionInteractor: ConnectionInteractor,
) : ViewModel() {
    val connectionStatus: LiveData<ConnectionStatus> = connectionInteractor.connectionStatus

    val communicatingStatus: LiveData<ConnectionStatus> = connectionInteractor.communicatingStatus

    //TODO make debug text specific line length
    val debugText: LiveData<String> = debugInteractor.debugText

    val transactionNotFoundError: LiveData<Int> = debugInteractor.transactionNotFoundError
    val registerWatchError: LiveData<Pair<String, Int>> = debugInteractor.registerWatchError
    val registerResponseError: LiveData<Pair<Int, Int>> = debugInteractor.registerResponseError

    private val byteArraysToSend: MutableList<CommandToSend> = mutableListOf()
    private val byteArraysPaused: MutableList<CommandToSend> = mutableListOf()
    private var transactionNumber: UShort = 1u
    private var registerCardNumber = 0

    val watchedRegisters: LiveData<List<RegisterOutput>> =
        repository.getAll().map { list -> list.map { it.toDto() } }

    private var sendingAndReading = false
    private var commandArrayBusy = false

    //TODO Take them from the settings
    private val timeoutStep = 250L

    fun connect(ip: String, port: String) = viewModelScope.launch {
        connectionInteractor.connect(ip, port)
    }

    fun disconnect() = viewModelScope.launch {
        sendingAndReading = false
        waitForCommandArrayToFree()
        connectionInteractor.disconnect()
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
            debugInteractor.addToLog("added register $registerAddress with transaction $transactionNumber to watch")
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
            debugInteractor.addToLog("send $newValue to register $registerAddress with transaction $transactionNumber to set")
            transactionNumber++
        }

    fun updateRegister(newRegisterOutput: RegisterOutput) = viewModelScope.launch {
        repository.save(newRegisterOutput)
    }

    private fun sendingAndReadingMessages() = viewModelScope.launch {
        while (sendingAndReading) {
            commandArrayBusy = true
            byteArraysToSend.forEach { message ->
                try {
                    connectionInteractor.changeCommunicatingStatus(ConnectionStatus.WORKING)
                    connectionInteractor.sendChannel.writeFully(message.command, 0, message.command.size)

                    waitingForResponse(message)

                    connectionInteractor.changeCommunicatingStatus(ConnectionStatus.CONNECTED)
                } catch (e: NotFoundTransactionNumberErrorException) {
                    pauseOrUnpauseWatchedRegister(message.registerAddress, true)
                    debugInteractor.getException(e, "Error: transaction not found in DB")
                } catch (e: ResponseErrorException) {
                    pauseOrUnpauseWatchedRegister(message.registerAddress, true)
                    debugInteractor.getException(e, "Error response")
                } catch (e: Exception) {
                    pauseOrUnpauseWatchedRegister(message.registerAddress, true)
                    debugInteractor.getException(
                        RegisterWatchException(message.registerAddress),
                        "Error sending or receiving"
                    )
                }
            }
            commandArrayBusy = false
            //TODO made this delay configurable
            delay(500)
        }
    }

    fun pauseOrUnpauseWatchedRegister(registerAddress: Int, isError: Boolean = false) =
        viewModelScope.launch {
            waitForCommandArrayToFree()
            val register = repository.getRegisterByAddress(registerAddress)
            register?.let { reg ->
                if (reg.status == RegisterConnection.WORKING || reg.status == RegisterConnection.LONG_WAIT) {
                    val command = byteArraysToSend.first { it.registerAddress == registerAddress }
                    byteArraysToSend.remove(command)
                    byteArraysPaused.add(command)
                    repository.save(reg.copy(status = if (isError) RegisterConnection.ERROR else RegisterConnection.PAUSE))
                } else {
                    val command = byteArraysPaused.first { it.registerAddress == registerAddress }
                    byteArraysToSend.add(command)
                    byteArraysPaused.remove(command)
                    repository.save(reg.copy(status = RegisterConnection.WORKING))
                }
            }
        }

    private suspend fun waitingForResponse(commandToSend: CommandToSend) {
        var waitingForAnswer = 0
        var gotCorrectResponse = false
        //Waiting to receive a respond  with the same transaction number
        //TODO made waitingForAnswer and timeout configurable
        while (waitingForAnswer < 5 && !gotCorrectResponse) {
            val response = commandToSend.setUpEmptyResponse()
            try {
                withTimeout(timeoutStep) {
                    connectionInteractor.receiveChannel.readAvailable(response)
                }
            } catch (e: TimeoutCancellationException) {
                waitingForAnswer++.checkForLongWait(commandToSend)
                continue
            }
            val output = response.getTransactionAndFunctionNumber()
            if (output.first == commandToSend.transactionNumber) {
                gotCorrectResponse = true
                //If transaction № is right
                if (output.second == commandToSend.functionNumber) {
                    var outputString = ""
                    response.forEach { outputString += "${it.toUByte()}, " }
                    debugInteractor.addToLog(outputString, LogType.RESPONSE)
                    when (commandToSend.functionNumber) {
                        0x3 -> receivedResponseForAnalogueOutput(output.first, response)
                        0x10 -> receivedResponseForChangeValue(commandToSend)
                        //TODO probably change to particular error
                        else -> throw Exception("Unknown function number")
                    }
                } else {
                    //If function number NOT RIGHT
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
                delay(timeoutStep)
                waitingForAnswer++.checkForLongWait(commandToSend)
            }
        } //If we waited and didn't got the right response
        if (!gotCorrectResponse) {
            debugInteractor.addToLog("skipped transaction $transactionNumber", LogType.ERROR)
        }
    }

    private suspend fun Int.checkForLongWait(
        commandToSend: CommandToSend
    ) {
        //TODO Make changeable threshold for long wait
        if (this > 1) {
            repository.getRegisterByAddress(commandToSend.registerAddress)
                ?.copy(status = RegisterConnection.LONG_WAIT)
                ?.let { updateRegister(it) }
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

    suspend fun checkRegisterByAddress(address: Int): Boolean =
        repository.getRegisterByAddress(address) != null

    private fun beginSendingAndReceivingMessages() {
        if (!sendingAndReading) {
            sendingAndReading = true
            sendingAndReadingMessages()
        }
    }

    private fun checkIfErrorNumber(sent: Int, response: Int): Boolean =
        response - sent == 128

    private suspend fun waitForCommandArrayToFree() {
        while (commandArrayBusy)
            delay(100)
    }
}