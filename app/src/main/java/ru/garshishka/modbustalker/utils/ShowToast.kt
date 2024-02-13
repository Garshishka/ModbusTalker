package ru.garshishka.modbustalker.utils

import android.content.Context
import android.widget.Toast

fun Context.showToast(stringResource: Int, vararg formatArgs: Any) {
    val message = getString(stringResource, *formatArgs)
    Toast.makeText(this, message, Toast.LENGTH_LONG)
        .show()
}