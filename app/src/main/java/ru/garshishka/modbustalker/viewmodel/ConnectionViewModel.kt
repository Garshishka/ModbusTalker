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
import ru.garshishka.modbustalker.utils.ConnectionStatus
import ru.garshishka.modbustalker.utils.makeByteArrayForAnalogueOut
import ru.garshishka.modbustalker.utils.read2BytesOutput

class ConnectionViewModel(private val repository: RegistryOutputRepository) : ViewModel() {
    private val _connectionStatus = MutableLiveData(ConnectionStatus.DISCONNECTED)
    val connectionStatus: LiveData<ConnectionStatus>
        get() = _connectionStatus

    private val _readStatus = MutableLiveData(ConnectionStatus.DISCONNECTED)
    val readStatus: LiveData<ConnectionStatus>
        get() = _readStatus

    private val _writeStatus = MutableLiveData(ConnectionStatus.DISCONNECTED)
    val writeStatus: LiveData<ConnectionStatus>
        get() = _writeStatus

    private val _debugText = MutableLiveData<String>()
    val debugText: LiveData<String>
        get() = _debugText

    val watchedRegisters: LiveData<List<RegisterOutput>> =
        repository.getAll().map { list -> list.map {it.toDto() } }

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
            _readStatus.value = ConnectionStatus.WORKING
            receiveChannel = socket.openReadChannel()
            changeStatusOfConnection(
                "Read channel open",
                _readStatus,
                ConnectionStatus.CONNECTED
            )
            _writeStatus.value = ConnectionStatus.WORKING
            sendChannel = socket.openWriteChannel(true)
            changeStatusOfConnection(
                "Write channel open",
                _writeStatus,
                ConnectionStatus.CONNECTED
            )
        } catch (e: Exception) {
            logError("Connection error $e")
            logError(e.message.toString())
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _readStatus.value = ConnectionStatus.DISCONNECTED
            _writeStatus.value = ConnectionStatus.DISCONNECTED
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
            repository.deleteAll()
            _readStatus.value = ConnectionStatus.DISCONNECTED
            _writeStatus.value = ConnectionStatus.DISCONNECTED
        } catch (e: Exception) {
            logError("Disconnection error $e")
            logError(e.message.toString())
        }
    }

    fun send(registerAddress: Int) = viewModelScope.launch {
        val amountToCheck = 1
        val output = RegisterOutput(registerAddress, -1, 1)
        logDebug("watching $amountToCheck registry from $registerAddress")
        val packet = makeByteArrayForAnalogueOut(registerAddress, 1)
        try {
            //_writeStatus.value = ConnectionStatus.WORKING
            while (true) {
                sendChannel.writeFully(packet, 0, packet.size)
                //logDebug("Sent packet ")
                //_readStatus.value = ConnectionStatus.WORKING
                val response = ByteArray(9 + amountToCheck * 2)
                receiveChannel.readAvailable(response)
                var outputString = ""
                response.forEach { outputString += "${it.toUByte()}, " }
                logResponse(outputString)
                output.value = response.read2BytesOutput()
                repository.save(output)
                delay(1000)
            }
        } catch (e: Exception) {
            logError("Sending error $e")
            logError(e.message.toString())
        } finally {
            //_writeStatus.value = ConnectionStatus.CONNECTED
            //_readStatus.value = ConnectionStatus.CONNECTED
        }
    }

    private fun changeStatusOfConnection(
        message: String,
        statusVar: MutableLiveData<ConnectionStatus>,
        newStatus: ConnectionStatus
    ) {
        logDebug(message)
        statusVar.value = newStatus
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
}