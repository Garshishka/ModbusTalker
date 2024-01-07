package ru.garshishka.modbustalker.data

data class RegisterOutput(
    val address: Int,
    val value: Int? = null,
    val transactionNumber: Int,
)
