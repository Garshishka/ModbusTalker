package ru.garshishka.modbustalker.view.dialog

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.fragment.app.FragmentActivity
import ru.garshishka.modbustalker.R
import ru.garshishka.modbustalker.data.RegisterOutput
import ru.garshishka.modbustalker.utils.getButtonIdFromOutputType
import ru.garshishka.modbustalker.utils.getOutputTypeFromButtonId
import ru.garshishka.modbustalker.utils.showToast
import ru.garshishka.modbustalker.viewmodel.ConnectionViewModel

fun FragmentActivity.registerWatchSettings(
    context: Context,
    viewModel: ConnectionViewModel,
    register: RegisterOutput,
) {
    val dialogView = this.layoutInflater.inflate(
        R.layout.dialog_register_settings, null
    )
    val radioGroup = dialogView.findViewById<RadioGroup>(R.id.type_radio_group)
    radioGroup.check(getButtonIdFromOutputType(register.outputType))
    val inputName = dialogView.findViewById<EditText>(R.id.register_name)
    inputName.setText(register.name)
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.register_settings)
        .setView(dialogView)
        .setPositiveButton(R.string.ok) { _, _ ->
            val newRegisterName = inputName.text.toString()
            if (newRegisterName.isNotBlank()) {
                viewModel.updateRegister(
                    register.copy(
                        name = newRegisterName,
                        outputType = getOutputTypeFromButtonId(radioGroup.checkedRadioButtonId)
                    )
                )
            } else {
                showToast(R.string.input_error_name, context)
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
    dialogView.findViewById<Button>(R.id.button_delete_register)
        .setOnClickListener {
            context.deleteWatchedRegister(register.address, viewModel)
            dialog.dismiss()
        }
    dialog.show()
}