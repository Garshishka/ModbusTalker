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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import ru.garshishka.modbustalker.databinding.FragmentDashBinding
import ru.garshishka.modbustalker.di.DependencyContainer
import ru.garshishka.modbustalker.utils.ConnectionStatus
import ru.garshishka.modbustalker.viewmodel.ConnectionViewModel
import ru.garshishka.modbustalker.viewmodel.ViewModelFactory

class DashFragment : Fragment() {
    //Dependency part
    private val container = DependencyContainer.getInstance()

    private val viewModel: ConnectionViewModel by viewModels {
        ViewModelFactory(container.repository)
    }
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
            communicatingStatus.observe(viewLifecycleOwner) {
                changeColorDueToStatusChange(it, binding.communicatingIcon)
            }
            debugText.observe(viewLifecycleOwner) {
                binding.debugText.text = it
            }
            registerResponseError.observe(viewLifecycleOwner){
                showResponseErrorToast(it)
            }
            registerWatchError.observe(viewLifecycleOwner) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.register_watch_error, it),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        return binding.root
    }

    @Composable
    fun SetCompose() {
        val watchedRegisters = viewModel.watchedRegisters.observeAsState().value
        Column {
            Button(
                onClick = {
                    chooseRegisterToWatch()
                }) {
                Text(text = "Add", fontSize = 20.sp)
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 100.dp), content =
            {
                watchedRegisters?.let {
                    items(it.size) { num ->
                        Card(
                            elevation = CardDefaults.cardElevation(),
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { deleteWatchedRegister(num) },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .weight(0.75f)
                                ) {
                                    Icon(
                                        painterResource(id = R.drawable.delete_24),
                                        contentDescription = "Delete"
                                    )
                                }
                                Column(modifier = Modifier.weight(1.25f)) {
                                    Text(
                                        text = it[num].address.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF333333),
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                    Text(
                                        text = it[num].value?.toString() ?: "",
                                        fontSize = 16.sp,
                                        color = Color(0xFF333333),
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    private fun chooseRegisterToWatch() {
        val inputEditTextField = EditText(requireActivity())
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.choose_register)
            .setMessage(R.string.choose_register_msg)
            .setView(inputEditTextField)
            .setPositiveButton(R.string.ok) { _, _ ->
                val editTextInput = inputEditTextField.text.toString().toIntOrNull()
                if (editTextInput == null) {
                    Log.e("UI", "Not numerical address")
                    showToast(R.string.not_numerical)
                } else {
                    if (viewModel.checkRegisterByAddress(editTextInput)) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.register_already_watched, editTextInput.toString()),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        viewModel.addWatchedRegister(editTextInput)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun deleteWatchedRegister(registerNumberInGrid: Int) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_register_title))
            .setMessage(getString(R.string.delete_register_text))
            .setPositiveButton(R.string.ok) { _, _ ->
                        viewModel.deleteWatchedRegister(registerNumberInGrid)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun showToast(stringResource: Int) {
        Toast.makeText(requireContext(), stringResource, Toast.LENGTH_LONG)
            .show()
    }

    private fun showResponseErrorToast(errorCode: Int?) {
        val errorMessage = when(errorCode){
            1 -> getString(R.string.error_response_1, errorCode)
            2 -> getString(R.string.error_response_2, errorCode)
            3 -> getString(R.string.error_response_3, errorCode)
            4 -> getString(R.string.error_response_4, errorCode)
            //TODO this one is not an error, should be taken out
            5 -> getString(R.string.error_response_5, errorCode)
            6 -> getString(R.string.error_response_6, errorCode)
            //TODO this one returns another answer with function codes 14 and 15, probably should be considered
            7 -> getString(R.string.error_response_7, errorCode)
            8 -> getString(R.string.error_response_8, errorCode)
            10 -> getString(R.string.error_response_10, errorCode)
            11 -> getString(R.string.error_response_11, errorCode)

            else -> getString(R.string.error_response_unknown)
        }
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG)
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