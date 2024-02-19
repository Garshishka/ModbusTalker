package ru.garshishka.modbustalker.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.garshishka.modbustalker.data.CommandToSend
import ru.garshishka.modbustalker.data.RegisterOutput
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.data.enums.ConnectionStatus
import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.utils.makeByteArrayForAnalogueOut
import ru.garshishka.modbustalker.utils.makeByteArrayForValueChange
import ru.garshishka.modbustalker.viewmodel.interactor.CommunicationInteractor
import ru.garshishka.modbustalker.viewmodel.interactor.ConnectionInteractor
import ru.garshishka.modbustalker.viewmodel.interactor.DebugInteractor
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: RegistryOutputRepository,
    private val debugInteractor: DebugInteractor,
    private val connectionInteractor: ConnectionInteractor,
    private val communicationInteractor: CommunicationInteractor,
) : ViewModel() {
    val connectionStatus: LiveData<ConnectionStatus> = connectionInteractor.connectionStatus

    val communicatingStatus: LiveData<ConnectionStatus> = connectionInteractor.communicatingStatus

    //TODO make debug text specific line length
    val debugText: LiveData<String> = debugInteractor.debugText

    val transactionNotFoundError: LiveData<Int> = debugInteractor.transactionNotFoundError
    val registerWatchError: LiveData<Pair<String, Int>> = debugInteractor.registerWatchError
    val registerResponseError: LiveData<Pair<Int, Int>> = debugInteractor.registerResponseError


    private var transactionNumber: UShort = 1u
    private var registerCardNumber = 0

    val watchedRegisters: LiveData<List<RegisterOutput>> =
        repository.getAll().map { list -> list.map { it.toDto() } }

    fun connect(ip: String, port: String) = viewModelScope.launch {
        connectionInteractor.connect(ip, port)
    }

    fun disconnect() = viewModelScope.launch {
        communicationInteractor.stopCommunication()
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
            communicationInteractor.addNewCommandToSend(
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
            //TODO change according to OutputType
            communicationInteractor.addNewCommandToSend(
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

    fun pauseOrUnpauseWatchedRegister(registerAddress: Int, isError: Boolean = false) =
        viewModelScope.launch {
            communicationInteractor.pauseOrUnpauseWatchedRegister(registerAddress, isError)
        }


    fun deleteWatchedRegister(registerAddress: Int) = viewModelScope.launch {
        communicationInteractor.removeCommandToSend(registerAddress)
        repository.delete(registerAddress)
    }

    suspend fun checkRegisterByAddress(address: Int): Boolean =
        repository.getRegisterByAddress(address) != null

    private fun beginSendingAndReceivingMessages() {
        communicationInteractor.beginSendingAndReadingMessages(viewModelScope)
    }
}