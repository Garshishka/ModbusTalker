package ru.garshishka.modbustalker.view.dialog

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.widget.EditText
import android.widget.RadioGroup
import androidx.fragment.app.FragmentActivity
import ru.garshishka.modbustalker.R
import ru.garshishka.modbustalker.utils.getOutputTypeFromButtonId
import ru.garshishka.modbustalker.utils.showToast
import ru.garshishka.modbustalker.viewmodel.ConnectionViewModel

fun FragmentActivity.chooseRegisterToWatch(context: Context, viewModel: ConnectionViewModel) {
    val dialogView = this.layoutInflater.inflate(
        R.layout.dialog_register_to_watch, null
    )
    val radioGroup = dialogView.findViewById<RadioGroup>(R.id.type_radio_group)
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.choose_register)
        .setView(dialogView)
        .setPositiveButton(R.string.ok) { _, _ ->
            val registerName =
                dialogView.findViewById<EditText>(R.id.register_name).text.toString()
            if (registerName.isNotBlank()) {
                val registerAddress =
                    dialogView.findViewById<EditText>(R.id.choose_register).text.toString()
                        .toIntOrNull()
                if (registerAddress == null) {
                    Log.e("UI", "Not numerical address")
                    showToast(R.string.input_error_not_numerical)
                } else {
                    if (viewModel.checkRegisterByAddress(registerAddress)) {
                        showToast(R.string.input_error_register_already_watched, registerAddress)
                    } else {
                        viewModel.addWatchedRegister(
                            registerName,
                            registerAddress,
                            getOutputTypeFromButtonId(radioGroup.checkedRadioButtonId)
                        )
                    }
                }
            } else {
                showToast(R.string.input_error_name, context)
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
    dialog.show()
}