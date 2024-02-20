package ru.garshishka.modbustalker.view

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import ru.garshishka.modbustalker.R
import ru.garshishka.modbustalker.data.enums.ConnectionStatus
import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.data.enums.RegisterConnection
import ru.garshishka.modbustalker.databinding.FragmentDashBinding
import ru.garshishka.modbustalker.ui.theme.cardGray
import ru.garshishka.modbustalker.ui.theme.cardGreen
import ru.garshishka.modbustalker.ui.theme.cardRed
import ru.garshishka.modbustalker.ui.theme.cardYellow
import ru.garshishka.modbustalker.utils.getIpString
import ru.garshishka.modbustalker.utils.setToIpInput
import ru.garshishka.modbustalker.utils.showToast
import ru.garshishka.modbustalker.view.dialog.chooseRegisterToWatch
import ru.garshishka.modbustalker.view.dialog.registerWatchSettings
import ru.garshishka.modbustalker.viewmodel.ConnectionViewModel

@AndroidEntryPoint
class DashFragment : Fragment() {
    private val viewModel: ConnectionViewModel by activityViewModels()
    private val binding: FragmentDashBinding by viewBinding(createMethod = CreateMethod.INFLATE)

    private lateinit var ipViewContainer: List<EditText>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding.apply {
            val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
                ?: throw Exception("No activity found")
            ipViewContainer = listOf(ipInput1, ipInput2, ipInput3, ipInput4)
            setIpAndPortFromSaved(sharedPref)

            ipViewContainer.forEachIndexed { i, editText ->
                editText.addTextChangedListener(IpTextWatcher(i))
            }
            debugText.movementMethod = LinkMovementMethod.getInstance()
            connectButton.setOnClickListener {
                when (viewModel.connectionStatus.value) {
                    ConnectionStatus.DISCONNECTED -> {
                        saveIpAndPort(sharedPref)
                        viewModel.connect(
                            ipViewContainer.getIpString(),
                            portInput.text.toString()
                        )
                    }

                    ConnectionStatus.CONNECTED -> viewModel.disconnect()
                    else -> {}
                }
            }
            newValueSendButton.setOnClickListener {
                val registerToChange = registerToChange.text.toString().toIntOrNull()
                if (registerToChange == null) {
                    Log.e("UI", "Not numerical address")
                    requireContext().showToast(R.string.input_error_not_numerical)
                } else {
                    val outputType = when (outputTypeGroup.checkedRadioButtonId) {
                        R.id.radio_uint16 -> OutputType.UINT16
                        R.id.radio_int16 -> OutputType.INT16
                        R.id.radio_int32 -> OutputType.INT32
                        R.id.radio_real32 -> OutputType.REAL32

                        else -> throw Exception("Unknown Output Type")
                    }
                    val newValueNumber: Number? = when (outputType) {
                        OutputType.UINT16 -> if (newValue.text.toString().contains("-"))
                            null else newValue.text.toString().toUShortOrNull()?.toInt()

                        OutputType.INT16 -> newValue.text.toString().toShortOrNull()
                        OutputType.INT32 -> newValue.text.toString().toIntOrNull()
                        OutputType.REAL32 -> newValue.text.toString().toFloatOrNull()
                    }
                    if (newValueNumber == null) {
                        Log.e("UI", "New value is not right")
                        requireContext().showToast(R.string.input_error_new_value)
                    } else {
                        viewModel.sendNewValueToRegister(
                            registerToChange,
                            outputType,
                            newValueNumber
                        )
                    }
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
                        connectionIcon.setImageResource(R.drawable.icon_signal_connected_24)
                        connectButton.setText(R.string.connect)
                        composeView.isVisible = false
                        changeValuePanel.isVisible = false
                    }
                } else {
                    binding.connectionIcon.setImageResource(R.drawable.icon_signal_disconnected_24)
                }
                if (it == ConnectionStatus.CONNECTED) {
                    binding.apply {
                        connectButton.setText(R.string.disconnect)
                        composeView.isVisible = true
                        changeValuePanel.isVisible = true
                    }
                }
            }
            communicatingStatus.observe(viewLifecycleOwner) {
                changeColorDueToStatusChange(it, binding.communicatingIcon)
            }
            debugText.observe(viewLifecycleOwner) {
                binding.debugText.text = it
            }
            registerResponseError.observe(viewLifecycleOwner) {
                showResponseErrorToast(it.first, it.second)
            }
            registerWatchError.observe(viewLifecycleOwner) {
                requireContext().showToast(R.string.register_watch_error, it.first, it.second)
            }
            transactionNotFoundError.observe(viewLifecycleOwner) {
                requireContext().showToast(R.string.error_transaction_not_found)
            }
        }
        return binding.root
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun SetCompose() {
        val watchedRegisters = viewModel.watchedRegisters.observeAsState().value
        Column {
            Button(
                onClick = {
                    requireActivity().chooseRegisterToWatch(requireContext(), viewModel)
                }) {
                Text(text = "Add", fontSize = 20.sp)
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                verticalItemSpacing = 4.dp,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content =
                {
                    watchedRegisters?.let {
                        items(it.size) { num ->
                            Card(
                                onClick = {
                                    requireActivity().registerWatchSettings(
                                        requireContext(),
                                        viewModel,
                                        it[num]
                                    )
                                },
                                elevation = CardDefaults.cardElevation(),
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (it[num].status) {
                                        RegisterConnection.WORKING -> cardGreen
                                        RegisterConnection.ERROR -> cardRed
                                        RegisterConnection.PAUSE -> cardGray
                                        RegisterConnection.LONG_WAIT -> cardYellow
                                    },
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1.25f)) {
                                        Text(
                                            text = it[num].name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF333333),
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                        Text(
                                            text = if (it[num].outputType != OutputType.REAL32)
                                                it[num].value?.toString() ?: ""
                                            else it[num].valueFloat?.toString() ?: "",
                                            fontSize = 16.sp,
                                            color = Color(0xFF333333),
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.pauseOrUnpauseWatchedRegister(it[num].address)
                                        },
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .weight(0.75f)
                                    ) {
                                        Icon(
                                            painterResource(
                                                id = (if (it[num].status == RegisterConnection.WORKING ||
                                                    it[num].status == RegisterConnection.LONG_WAIT
                                                ) R.drawable.icon_pause_circle_24
                                                else R.drawable.icon_send_circle_24)
                                                //id = R.drawable.icon_pause_circle_24
                                            ),
                                            contentDescription = "Pause"
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    private fun FragmentDashBinding.saveIpAndPort(sharedPref: SharedPreferences) {
        with(sharedPref.edit()) {
            putString(
                getString(R.string.prefs_saved_ip),
                ipViewContainer.getIpString()
            )
            putString(
                getString(R.string.prefs_saved_port),
                portInput.text.toString()
            )
            apply()
        }
    }

    private fun FragmentDashBinding.setIpAndPortFromSaved(sharedPref: SharedPreferences) {
        sharedPref.getString(getString(R.string.prefs_saved_ip), getString(R.string.base_ip))
            ?.setToIpInput(ipViewContainer)
        portInput.setText(
            sharedPref.getString(
                getString(R.string.prefs_saved_port),
                getString(R.string.base_port)
            )
        )
    }

    private fun showResponseErrorToast(errorCode: Int?, registerNumber: Int) {
        val errorMessage = when (errorCode) {
            1 -> getString(R.string.error_response_1, errorCode, registerNumber)
            2 -> getString(R.string.error_response_2, errorCode, registerNumber)
            3 -> getString(R.string.error_response_3, errorCode, registerNumber)
            4 -> getString(R.string.error_response_4, errorCode, registerNumber)
            //TODO this one is not an error, should be taken out
            5 -> getString(R.string.error_response_5, errorCode, registerNumber)
            6 -> getString(R.string.error_response_6, errorCode, registerNumber)
            //TODO this one returns another answer with function codes 14 and 15, probably should be considered
            7 -> getString(R.string.error_response_7, errorCode, registerNumber)
            8 -> getString(R.string.error_response_8, errorCode, registerNumber)
            10 -> getString(R.string.error_response_10, errorCode, registerNumber)
            11 -> getString(R.string.error_response_11, errorCode, registerNumber)

            else -> getString(R.string.error_response_unknown, registerNumber)
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

    override fun onDestroy() {
        super.onDestroy()
    }

    inner class IpTextWatcher(private val index: Int) : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            //ignore
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            //ignore
        }

        override fun afterTextChanged(s: Editable?) {
            if (s != null && s.isEmpty() && index > 0) {
                ipViewContainer[index - 1].requestFocus()
                ipViewContainer[index - 1].setSelection(ipViewContainer[index - 1].text.length)
            } else if (!s.isNullOrBlank() && index < 3 &&
                (s.length == 3 || s[s.length - 1] == '.')
            ) {
                ipViewContainer[index + 1].requestFocus()
            }
            if (ipViewContainer[index].text.length > 3) {
                ipViewContainer[index].setText(
                    ipViewContainer[index].text.toString()
                        .take(3)
                )
            }
            if (ipViewContainer[index].text.contains('.')) {
                ipViewContainer[index].setText(
                    ipViewContainer[index].text.toString()
                        .replace(".", "")
                )
            }
        }
    }
}