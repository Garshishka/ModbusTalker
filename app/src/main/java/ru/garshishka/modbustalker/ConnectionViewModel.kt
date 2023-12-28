package ru.garshishka.modbustalker

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

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

    private val selectorManager = SelectorManager(Dispatchers.IO)
    private lateinit var socket: Socket

    //suspend fun connect(ip: String, port: String){
    fun connect(ip: String, port: String) {
        Log.d("ModBus", "Beginning connection")
        runBlocking {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            Log.d("Modbus","$ip and ${port.toInt()}")
            try {
                socket = aSocket(selectorManager).tcp().connect(ip, port.toInt())
                Log.d("ModBus", "Connected")
                _connectionStatus.value = ConnectionStatus.CONNECTED
            } catch (e: Exception) {
                Log.d("ModBus", "Not connected")
                Log.e("ModBus", e.toString())
                Log.e("ModBus", e.message.toString())
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
            Log.d("ModBus", connectionStatus.value.toString())
        }
    }

    private fun testSend() {
        Log.d("ModBus", "Beginning function")
        runBlocking {

            try {
                socket = aSocket(selectorManager).tcp().connect("10.0.2.2", 502)
                Log.d("ModBus", "connected")
                val receiveChannel = socket.openReadChannel()
                val sendChannel = socket.openWriteChannel(autoFlush = true)

                Log.d("ModBus", "sending to socket")
                val packet = byteArrayOfInts(
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
                sendChannel.writeFully(packet, 0, 12)

                launch(Dispatchers.IO) {
                    while (true) {
                        val ar = ByteArray(9)
                        val greeting = receiveChannel.readFully(ar, 0, 9)
                        if (greeting != null) {
                            println(greeting)
                            var outputString = ""
                            ar.forEach { outputString += "${it.toUByte()}, " }
                            println(outputString)
                        } else {
                            println("Server closed a connection")
                            socket.close()
                            selectorManager.close()
                            exitProcess(0)
                        }
                    }
                }

                //socket.close()
            } catch (e: Exception) {
                Log.e("ModBus", e.toString())
                Log.e("ModBus", e.message.toString())
            }
        }
    }

    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }
}