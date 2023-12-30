package ru.garshishka.modbustalker

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import ru.garshishka.modbustalker.databinding.FragmentDashBinding
import ru.garshishka.modbustalker.utils.ConnectionStatus

class DashFragment : Fragment() {
    private val viewModel: ConnectionViewModel by activityViewModels()
    private val binding: FragmentDashBinding by viewBinding(createMethod = CreateMethod.INFLATE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding.apply {
            debugText.movementMethod = LinkMovementMethod.getInstance()
            connectButton.setOnClickListener {
                when (viewModel.connectionStatus.value) {
                    ConnectionStatus.DISCONNECTED -> viewModel.connect(
                        ipInput.text.toString(),
                        portInput.text.toString()
                    )

                    ConnectionStatus.CONNECTED -> viewModel.disconnect()
                    else -> {}
                }
            }
            composeView.setContent {
                SetCompose()
            }
        }
        viewModel.apply {
            connectionStatus.observe(viewLifecycleOwner) {
                changeColorDueToStatusChange(it, binding.connectionIcon)
                if (it == ConnectionStatus.DISCONNECTED) {
                    binding.apply {
                        connectionIcon.setImageResource(R.drawable.signal_connected_24)
                        connectButton.setText(R.string.connect)
                        composeView.isVisible = false
                    }
                } else {
                    binding.connectionIcon.setImageResource(R.drawable.signal_disconnected_24)
                }
                if (it == ConnectionStatus.CONNECTED) {
                    binding.apply {
                        connectButton.setText(R.string.disconnect)
                        composeView.isVisible = true
                    }
                }
            }
            readStatus.observe(viewLifecycleOwner) {
                changeColorDueToStatusChange(it, binding.readIcon)
            }
            writeStatus.observe(viewLifecycleOwner) {
                changeColorDueToStatusChange(it, binding.writeIcon)
            }
            debugText.observe(viewLifecycleOwner) {
                binding.debugText.text = it
            }
        }
        return binding.root
    }

    @Composable
    fun SetCompose() {
        val numberOfRegisters = viewModel.numberOfRegisters.observeAsState().value
        Column {
            Button(
                onClick = {
                    chooseRegisterToWatch()
                }) {
                Text(text = "Add", fontSize = 20.sp)
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 60.dp), content =
            {
                items(numberOfRegisters!!) {
                    Card(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = "added",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFFFFFFF),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            })
        }
    }

    private fun chooseRegisterToWatch(){
        val inputEditTextField = EditText(requireActivity())
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.choose_register)
            .setMessage(R.string.choose_register_msg)
            .setView(inputEditTextField)
            .setPositiveButton(R.string.ok) { _, _ ->
                val editTextInput = inputEditTextField.text.toString().toIntOrNull()
                if(editTextInput == null){
                    Log.e("UI","Not numerical address")
                    showToast(R.string.not_numerical)
                } else{
                    viewModel.send(editTextInput)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun showToast(stringResource: Int){
        Toast.makeText(requireContext(),stringResource,Toast.LENGTH_LONG)
            .show()
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