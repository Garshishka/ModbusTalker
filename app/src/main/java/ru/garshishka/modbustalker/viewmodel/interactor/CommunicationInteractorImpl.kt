package ru.garshishka.modbustalker.viewmodel.interactor

import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ru.garshishka.modbustalker.data.CommandToSend
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.data.enums.ConnectionStatus
import ru.garshishka.modbustalker.data.enums.RegisterConnection
import ru.garshishka.modbustalker.utils.NotFoundTransactionNumberErrorException
import ru.garshishka.modbustalker.utils.RegisterWatchException
import ru.garshishka.modbustalker.utils.ResponseErrorException
import ru.garshishka.modbustalker.utils.getTransactionAndFunctionNumber
import ru.garshishka.modbustalker.utils.read1ByteFromBuffer
import ru.garshishka.modbustalker.utils.setUpEmptyResponse
import javax.inject.Inject

class CommunicationInteractorImpl @Inject constructor(
    val debugInteractor: DebugInteractor,
    val connectionInteractor: ConnectionInteractor,
    val repository: RegistryOutputRepository,
) : CommunicationInteractor {
    private val byteArraysToSend: MutableList<CommandToSend> = mutableListOf()
    private val byteArraysPaused: MutableList<CommandToSend> = mutableListOf()

    private var sendingAndReading = false
    private var commandArrayBusy = false

    //TODO Take them from the settings
    private val timeoutStep = 250L

    private fun sendingAndReadingMessages(viewModelScope: CoroutineScope) = viewModelScope.launch {
        while (sendingAndReading) {
            commandArrayBusy = true
            byteArraysToSend.forEach { message ->
                try {
                    connectionInteractor.changeCommunicatingStatus(ConnectionStatus.WORKING)
                    connectionInteractor.sendChannel.writeFully(
                        message.command,
                        0,
                        message.command.size
                    )

                    waitingForResponse(message)

                    connectionInteractor.changeCommunicatingStatus(ConnectionStatus.CONNECTED)
                } catch (e: NotFoundTransactionNumberErrorException) {
                    pauseRegisterOnError(message.registerAddress)
                    debugInteractor.getException(e, "Error: transaction not found in DB")
                } catch (e: ResponseErrorException) {
                    pauseRegisterOnError(message.registerAddress)
                    debugInteractor.getException(e, "Error response")
                } catch (e: Exception) {
                    pauseRegisterOnError(message.registerAddress)
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
            debugInteractor.addToLog(
                "skipped transaction ${commandToSend.transactionNumber}",
                LogType.ERROR
            )
        }
    }

    override suspend fun addNewCommandToSend(commandToSend: CommandToSend) {
        waitForCommandArrayToFree()
        byteArraysToSend.add(commandToSend)
    }

    override suspend fun removeCommandToSend(registerAddress: Int) {
        waitForCommandArrayToFree()
        if (!byteArraysToSend.removeIf { it.registerAddress == registerAddress }) {
            byteArraysPaused.removeIf { it.registerAddress == registerAddress }
        }
    }

    override fun beginSendingAndReadingMessages(viewModelScope: CoroutineScope) {
        if (!sendingAndReading) {
            sendingAndReading = true
            sendingAndReadingMessages(viewModelScope)
        }
    }

    override suspend fun stopCommunication() {
        sendingAndReading = false
        waitForCommandArrayToFree()
    }

    override suspend fun pauseOrUnpauseWatchedRegister(registerAddress: Int) {
        waitForCommandArrayToFree()
        val register = repository.getRegisterByAddress(registerAddress)
        register?.let { reg ->
            if (reg.status == RegisterConnection.WORKING || reg.status == RegisterConnection.LONG_WAIT) {
                val command = byteArraysToSend.first { it.registerAddress == registerAddress }
                byteArraysToSend.remove(command)
                byteArraysPaused.add(command)
                repository.save(reg.copy(status = RegisterConnection.PAUSE))
            } else {
                val command = byteArraysPaused.first { it.registerAddress == registerAddress }
                byteArraysToSend.add(command)
                byteArraysPaused.remove(command)
                repository.save(reg.copy(status = RegisterConnection.WORKING))
            }
        }
    }

    private suspend fun pauseRegisterOnError(registerAddress: Int){
        val register = repository.getRegisterByAddress(registerAddress)
        val command = byteArraysToSend.first { it.registerAddress == registerAddress }
        byteArraysToSend.remove(command)
        byteArraysPaused.add(command)
        register?.copy()?.let { repository.save(it.copy(status = RegisterConnection.ERROR)) }
    }

    private fun receivedResponseForChangeValue(commandToSend: CommandToSend) {
        //waitForCommandArrayToFree()
        byteArraysToSend.remove(commandToSend)
    }

    private suspend fun receivedResponseForAnalogueOutput(
        transactionNumber: Int,
        response: ByteArray
    ) {
        repository.updateValue(transactionNumber, response)
    }

    private suspend fun waitForCommandArrayToFree() {
        while (commandArrayBusy)
            delay(100)
    }

    //TODO make repository command to make Long wait or remove it entirely
    private fun Int.checkForLongWait(
        commandToSend: CommandToSend
    ) {
        /*     //TODO Make changeable threshold for long wait
             if (this > 1) {
                 repository.getRegisterByAddress(commandToSend.registerAddress)
                     ?.copy(status = RegisterConnection.LONG_WAIT)
                     ?.let { updateRegister(it) }

             }
         */
    }

    private fun checkIfErrorNumber(sent: Int, response: Int): Boolean =
        response - sent == 128
}