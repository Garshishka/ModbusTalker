package ru.garshishka.modbustalker.utils

class NotFoundTransactionNumberErrorException(val transactionNumber: Int) : Exception()
class ResponseErrorException(val errorCode: Int, val registerNumber: Int) : Exception()
class RegisterWatchException(val registerNumber: Int) : Exception()
