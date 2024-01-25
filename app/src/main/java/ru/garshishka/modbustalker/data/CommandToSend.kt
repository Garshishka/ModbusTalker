package ru.garshishka.modbustalker.data

import ru.garshishka.modbustalker.data.enums.OutputType

data class CommandToSend (
    val transactionNumber: Int,
    val command: ByteArray,
    val registerAddress: Int,
    val outputType: OutputType,
    val functionNumber: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommandToSend

        if (transactionNumber != other.transactionNumber) return false
        if (!command.contentEquals(other.command)) return false
        if (registerAddress != other.registerAddress) return false
        if (outputType != other.outputType) return false
        if (functionNumber != other.functionNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionNumber
        result = 31 * result + command.contentHashCode()
        result = 31 * result + registerAddress
        result = 31 * result + outputType.hashCode()
        result = 31 * result + functionNumber
        return result
    }

}