package ru.garshishka.modbustalker.viewmodel.interactor

import kotlinx.coroutines.CoroutineScope
import ru.garshishka.modbustalker.data.CommandToSend

interface CommunicationInteractor {
    suspend fun addNewCommandToSend(commandToSend: CommandToSend)
    suspend fun removeCommandToSend(registerAddress: Int)
    fun beginSendingAndReadingMessages(viewModelScope: CoroutineScope)
    suspend fun stopCommunication()
    suspend fun pauseOrUnpauseWatchedRegister(registerAddress: Int)
}