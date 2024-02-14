package ru.garshishka.modbustalker.data

import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.data.enums.RegisterConnection

data class RegisterOutput(
    val place: Int,
    val name: String,
    val address: Int,
    val value: Int? = null,
    val valueFloat: Float? = null,
    val transactionNumber: Int,
    val outputType: OutputType,
    val status : RegisterConnection = RegisterConnection.WORKING
)
