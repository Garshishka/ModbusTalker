package ru.garshishka.modbustalker.utils

import ru.garshishka.modbustalker.data.CommandToSend
import ru.garshishka.modbustalker.data.enums.OutputType
import java.nio.ByteBuffer

fun byteArrayFromInts(numbers: List<Int>) =
    ByteArray(numbers.size) { pos -> numbers[pos].toByte() }

fun makeByteArrayForAnalogueOut(
    registryAddress: Int,
    amountToCheck: Int,
    transactionNum: UShort
): ByteArray {
    val messageLength = 0x06
    val deviceAddress = 0x01
    val function = 0x03

    val list = transactionNum.toInt().makeByteList() +
            listOf(
                0x00,
                0x00,
                0x00,
                messageLength,
                deviceAddress,
                function,
            ) + registryAddress.makeByteList() + amountToCheck.makeByteList()
    return byteArrayFromInts(list)
}

fun makeByteArrayForValueChange(
    registryAddress: Int,
    newValue: Int,
    transactionNum: UShort
): ByteArray {
    val messageLength = 0x9
    val deviceAddress = 0x01
    val function = 0x10
    val amountToWrite = 0x1

    val list = transactionNum.toInt().makeByteList() +
            listOf(
                0x00,
                0x00,
                0x00,
                messageLength,
                deviceAddress,
                function,
            ) + registryAddress.makeByteList() + amountToWrite.makeByteList() +
            listOf(0x02) + newValue.makeByteList()

    return byteArrayFromInts(list)
}

fun ByteArray.readBytes(offset: Int, outputType: OutputType = OutputType.UINT16): Number =
    when (outputType) {
        OutputType.UINT16 -> ByteBuffer.wrap(this, offset, 2).short.toUShort().toInt()
        OutputType.INT16 -> ByteBuffer.wrap(this, offset, 2).short.toInt()
        OutputType.INT32 -> ByteBuffer.wrap(this, offset, 4).int
        OutputType.REAL32 -> ByteBuffer.wrap(this, offset, 4).float
    }


fun ByteArray.getTransactionAndFunctionNumber(): Pair<Int, Int> =
    (this.readBytes(0, OutputType.UINT16).toInt() to this.read1ByteFromBuffer(7))

fun ByteArray.read1ByteFromBuffer(offset: Int): Int =
    this[offset].toInt() and 0xff

fun CommandToSend.setUpEmptyResponse(): ByteArray =
    ByteArray(
        if (this.functionNumber == 0x10) 12
        else (9 + if (this.outputType == OutputType.INT16
            || this.outputType == OutputType.UINT16
        ) 2 else 4)
    )


fun Int.makeByteList(): List<Int> =
    when {
        (this > 255) -> {
            val hex = this.toString(16)
            println(hex)
            val length = hex.length
            listOf(
                hex.substring(0, length - 2).toInt(16),
                hex.substring(length - 2, length).toInt(16)
            )
        }

        (this >= 0) -> {
            listOf(0x00, this)
        }

        (this > -256) -> {
            listOf(0xff, this)
        }

        else -> { //TODO check this part, probably wrong
            val hex = this.toString(16)
            println(hex)
            val length = hex.length
            listOf(
                hex.substring(0, length - 2).toInt(16),
                hex.substring(length - 2, length).toInt(16)
            )
        }
    }
