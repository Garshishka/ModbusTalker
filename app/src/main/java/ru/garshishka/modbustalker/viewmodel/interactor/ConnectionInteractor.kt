package ru.garshishka.modbustalker.viewmodel.interactor

import androidx.lifecycle.LiveData
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import ru.garshishka.modbustalker.data.enums.ConnectionStatus

interface ConnectionInteractor {
    val connectionStatus: LiveData<ConnectionStatus>
    val communicatingStatus: LiveData<ConnectionStatus>
    var sendChannel: ByteWriteChannel
    var receiveChannel: ByteReadChannel
    suspend fun connect(ip: String, port: String)
    suspend fun disconnect()
    fun changeCommunicatingStatus(status: ConnectionStatus)
}