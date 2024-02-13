package ru.garshishka.modbustalker.view.dialog

import android.app.AlertDialog
import android.content.Context
import ru.garshishka.modbustalker.R
import ru.garshishka.modbustalker.viewmodel.ConnectionViewModel

fun Context.deleteWatchedRegister(
    registerNumberInGrid: Int,
    viewModel: ConnectionViewModel
) {
    val dialog = AlertDialog.Builder(this)
        .setTitle(getString(R.string.delete_register_title))
        .setMessage(getString(R.string.delete_register_text))
        .setPositiveButton(R.string.ok) { _, _ ->
            viewModel.deleteWatchedRegister(registerNumberInGrid)
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
    dialog.show()
}