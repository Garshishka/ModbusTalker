package ru.garshishka.modbustalker.data

import ru.garshishka.modbustalker.data.enums.OutputType

data class RegisterOutput(
    val address: Int,
    val value: Int? = null,
    val transactionNumber: Int,
    val outputType: OutputType,
)
