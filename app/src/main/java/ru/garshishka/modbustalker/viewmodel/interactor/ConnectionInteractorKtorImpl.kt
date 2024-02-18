package ru.garshishka.modbustalker.viewmodel.interactor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.data.enums.ConnectionStatus
import javax.inject.Inject

class ConnectionInteractorKtorImpl @Inject constructor(
    private val debugInteractor: DebugInteractor,
    //TODO DELETE LATER AS ONLY NEEDED FOR CLEAN TABLE
    private val repository: RegistryOutputRepository,
) : ConnectionInteractor {
    private val _connectionStatus = MutableLiveData(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: LiveData<ConnectionStatus>
        get() = _connectionStatus

    private val _communicatingStatus = MutableLiveData(ConnectionStatus.DISCONNECTED)
    override val communicatingStatus: LiveData<ConnectionStatus>
        get() = _communicatingStatus

    private val selectorManager = SelectorManager(Dispatchers.IO)
    private lateinit var socket: Socket
    override lateinit var receiveChannel: ByteReadChannel
    override lateinit var sendChannel: ByteWriteChannel

    override suspend fun connect(ip: String, port: String) {
        debugInteractor.addToLog("connecting to socket $ip and ${port.toInt()}")
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
            repository.deleteAll()

            _communicatingStatus.value = ConnectionStatus.CONNECTED
        } catch (e: Exception) {
            debugInteractor.getException(e, "Connection error")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _communicatingStatus.value = ConnectionStatus.DISCONNECTED
        }
        debugInteractor.addToLog("socket ${connectionStatus.value.toString()}")
    }

    override suspend fun disconnect() {
        try {
            debugInteractor.addToLog("Closing connection")
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
            debugInteractor.getException(e, "Disconnection error")
        }
    }

    override fun changeCommunicatingStatus(status: ConnectionStatus) {
        _communicatingStatus.value = status
    }

    private fun changeStatusOfConnection(
        message: String,
        statusVar: MutableLiveData<ConnectionStatus>,
        newStatus: ConnectionStatus
    ) {
        debugInteractor.addToLog(message)
        statusVar.value = newStatus
    }

}