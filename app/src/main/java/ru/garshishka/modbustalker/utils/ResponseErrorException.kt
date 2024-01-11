package ru.garshishka.modbustalker.utils

class ResponseErrorException(val errorCode: Int) : Exception() {
}