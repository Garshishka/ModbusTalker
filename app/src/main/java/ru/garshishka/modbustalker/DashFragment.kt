package ru.garshishka.modbustalker

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
    private val viewModel: ConnectionViewModel by activityViewModels()
    private val binding: FragmentDashBinding by viewBinding(createMethod = CreateMethod.INFLATE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding.apply {
            connectButton.setOnClickListener {
                Log.d("connection", "trying to connect")
                viewModel.connect(ipInput.text.toString(),portInput.text.toString())
            }
        }
        viewModel.apply {
            connectionStatus.observe(viewLifecycleOwner){
                when(it){
                    ConnectionStatus.DISCONNECTED -> binding.connectionIcon.backgroundTintList = requireContext().getColorStateList(R.color.red)
                    ConnectionStatus.CONNECTED -> binding.connectionIcon.backgroundTintList = requireContext().getColorStateList(R.color.green)
                    ConnectionStatus.CONNECTING -> binding.connectionIcon.backgroundTintList = requireContext().getColorStateList(R.color.yellow)
                }
            }
        }
        return binding.root
    }


}