package ru.garshishka.modbustalker

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.garshishka.modbustalker.utils.makeByteList

class ConnectionViewModel : ViewModel() {
    private val _connectionStatus = MutableLiveData<ConnectionStatus>()
    val connectionStatus: LiveData<ConnectionStatus>
        get() = _connectionStatus

    private val _readStatus = MutableLiveData<ConnectionStatus>()
    val readStatus: LiveData<ConnectionStatus>
        get() = _readStatus

    private val _writeStatus = MutableLiveData<ConnectionStatus>()
    val writeStatus: LiveData<ConnectionStatus>
        get() = _writeStatus

    private val _debugText = MutableLiveData<String>()
    val debugText: LiveData<String>
        get() = _debugText

    private val selectorManager = SelectorManager(Dispatchers.IO)
    private lateinit var socket: Socket
    private lateinit var receiveChannel: ByteReadChannel
    private lateinit var sendChannel: ByteWriteChannel

    init {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _readStatus.value = ConnectionStatus.DISCONNECTED
        _writeStatus.value = ConnectionStatus.DISCONNECTED
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
            _readStatus.value = ConnectionStatus.DISCONNECTED
            _writeStatus.value = ConnectionStatus.DISCONNECTED
        } catch (e: Exception) {
            logError("Disconnection error $e")
            logError(e.message.toString())
        }
    }

    fun send() = viewModelScope.launch {
        logDebug("Sending packet to socket")
        try {
            _writeStatus.value = ConnectionStatus.WORKING
            /*val packet = byteArrayFromHex(
                listOf(
                    0x00,
                    0x01,
                    0x00,
                    0x00,
                    0x00,
                    0x06,
                    0xff,
                    0x05,
                    0x00,
                    0x00,
                    0xff,
                    0x00
                )
            )*/
            val registryAddress = 1
            val amountToCheck = 3
            logDebug("Checking $amountToCheck registry from $registryAddress")
            val packet = makeByteArrayForAnalogueOut(1,3)
            sendChannel.writeFully(packet, 0, packet.size)
            logDebug("Sent packet ")

            _readStatus.value = ConnectionStatus.WORKING
            val response = ByteArray(9+amountToCheck*2)
            receiveChannel.readAvailable(response)
            var outputString = ""
            response.forEach { outputString += "${it.toUByte()}, " }
            logResponse(outputString)
        } catch (e: Exception) {
            logError("Sending error $e")
            logError(e.message.toString())
        } finally {
            _writeStatus.value = ConnectionStatus.CONNECTED
            _readStatus.value = ConnectionStatus.CONNECTED
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

    private fun makeByteArrayForAnalogueOut(registryAddress: Int, amountToCheck: Int) : ByteArray{
        val identifier = 1
        val messageLength = 0x06
        val deviceAddress = 0x01
        val function = 0x03

        val list = identifier.makeByteList()+
                listOf(0x00,
            0x00,
            0x00,
            messageLength,
            deviceAddress,
            function,
        ) + registryAddress.makeByteList() + amountToCheck.makeByteList()
        return byteArrayFromHex(list)
    }

    private fun byteArrayFromHex(numbers: List<Int>) =//vararg ints: Int) =
        ByteArray(numbers.size) { pos -> numbers[pos].toByte() }
}