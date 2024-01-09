package ru.garshishka.modbustalker.utils

class ResponseErrorException(message: String) : Exception(message) {
}

fun throwErrorFromErrorResponse(message: ByteArray){
    //TODO make string resources and move them out to fragment
    val errorMessage = when(val error = message.read1ByteFromBuffer(8)){
        1 -> "Error $error: Illegal function code"
        2 -> "Error $error: Illegal data address"
        3 -> "Error $error: Illegal data value"
        4 -> "Error $error: Slave device failure"
        //TODO this one is not an error, should be taken out
        5 -> "Error $error: Request accepted and processing, but it will take a long time"
        6 -> "Error $error: The slave is busy processing. Repeat later"
        //TODO this one returns another answer with function codes 14 and 15, probably should be considered
        7 -> "Error $error: The slave can not execute the program function specified"
        8 -> "Error $error: The slave detected a parity error when reading the extended memory"
        10 -> "Error $error: Gateway Path Unavailable. Gateway is misconfigured or overloaded"
        11 -> "Error $error: Gateway target device failed to respond"

        else -> "Error $error: Unknown error"
    }
    throw ResponseErrorException(errorMessage)
}