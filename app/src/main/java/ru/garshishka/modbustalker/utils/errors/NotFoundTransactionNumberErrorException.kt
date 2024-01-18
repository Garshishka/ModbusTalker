package ru.garshishka.modbustalker.utils.errors

class NotFoundTransactionNumberErrorException(val transactionNumber: Int) : Exception()