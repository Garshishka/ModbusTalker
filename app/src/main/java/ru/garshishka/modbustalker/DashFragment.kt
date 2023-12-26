package ru.garshishka.modbustalker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.garshishka.modbustalker.databinding.FragmentDashBinding
import kotlin.system.exitProcess

class DashFragment : Fragment() {
    private val binding: FragmentDashBinding by viewBinding(createMethod = CreateMethod.INFLATE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding.apply {
            testButton.setOnClickListener {
                Log.d("test", "send by ktor")
                testSend()
            }
        }
        return binding.root
    }


    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }

    private fun testSend() {
        Log.d("ModBus", "Beginning function")
        runBlocking {
            val selectorManager = SelectorManager(Dispatchers.IO)
            try {
                val socket = aSocket(selectorManager).tcp().connect("10.0.2.2", 502)
                Log.d("ModBus", "connected")
                val receiveChannel = socket.openReadChannel()
                val sendChannel  = socket.openWriteChannel(autoFlush = true)

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
                        val greeting = receiveChannel.readFully(ar,0,9)
                        if (greeting != null) {
                            println(greeting)
                            var outputString = ""
                            ar.forEach { outputString+="${it.toUByte()}, " }
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
}