package ru.garshishka.modbustalker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import ru.garshishka.modbustalker.databinding.FragmentDashBinding

class DashFragment : Fragment() {
    private val viewModel: ConnectionViewModel by activityViewModels()
    private val binding: FragmentDashBinding by viewBinding(createMethod = CreateMethod.INFLATE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding.apply {
            connectButton.setOnClickListener {
                when(viewModel.connectionStatus.value){
                    ConnectionStatus.DISCONNECTED -> viewModel.connect(ipInput.text.toString(), portInput.text.toString())
                    ConnectionStatus.CONNECTED -> viewModel.disconnect()
                    else ->{}
                }
            }
        }
        viewModel.apply {
            connectionStatus.observe(viewLifecycleOwner) {
                changeColorDueToStatusChange(it, binding.connectionIcon)
                if (it == ConnectionStatus.DISCONNECTED) {
                    binding.connectionIcon.setImageResource(R.drawable.signal_connected_24)
                    binding.connectButton.setText(R.string.connect)
                } else {
                    binding.connectionIcon.setImageResource(R.drawable.signal_disconnected_24)
                }
                if(it == ConnectionStatus.CONNECTED){
                    binding.connectButton.setText(R.string.disconnect)
                }
            }
            readStatus.observe(viewLifecycleOwner) {
                changeColorDueToStatusChange(it, binding.readIcon)
            }
            writeStatus.observe(viewLifecycleOwner) {
                changeColorDueToStatusChange(it, binding.writeIcon)
            }
        }
        return binding.root
    }


    private fun changeColorDueToStatusChange(newStatus: ConnectionStatus, icon: ImageView) {
        when (newStatus) {
            ConnectionStatus.DISCONNECTED -> icon.backgroundTintList =
                requireContext().getColorStateList(R.color.red)

            ConnectionStatus.CONNECTED -> icon.backgroundTintList =
                requireContext().getColorStateList(R.color.green)

            ConnectionStatus.WORKING -> icon.backgroundTintList =
                requireContext().getColorStateList(R.color.yellow)
        }
    }

}