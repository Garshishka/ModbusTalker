package ru.garshishka.modbustalker.utils.errors

class ResponseErrorException(val errorCode: Int, val registerNumber: Int) : Exception()