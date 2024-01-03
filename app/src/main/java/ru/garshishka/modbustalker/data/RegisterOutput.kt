package ru.garshishka.modbustalker.data

data class RegisterOutput(
    val address: Int,
    var value: Int,
    val transactionNumber: Int,
)
