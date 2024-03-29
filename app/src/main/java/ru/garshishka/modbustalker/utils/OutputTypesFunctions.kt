package ru.garshishka.modbustalker.utils

import ru.garshishka.modbustalker.R
import ru.garshishka.modbustalker.data.enums.OutputType

fun getOutputTypeFromButtonId(buttonId: Int): OutputType =
    when (buttonId) {
        R.id.radio_uint16 -> OutputType.UINT16
        R.id.radio_int16 -> OutputType.INT16
        R.id.radio_int32 -> OutputType.INT32
        R.id.radio_real32 -> OutputType.REAL32
        else -> OutputType.INT16
    }

fun getButtonIdFromOutputType(outputType: OutputType): Int =
    when (outputType) {
        OutputType.UINT16 -> R.id.radio_uint16
        OutputType.INT16 -> R.id.radio_int16
        OutputType.INT32 -> R.id.radio_int32
        OutputType.REAL32 -> R.id.radio_real32
    }