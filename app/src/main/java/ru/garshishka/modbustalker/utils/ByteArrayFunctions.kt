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

    val list = transactionNum.toInt().makeShortByteList() +
            listOf(
                0x00,
                0x00,
                0x00,
                messageLength,
                deviceAddress,
                function,
            ) + registryAddress.makeShortByteList() + amountToCheck.makeShortByteList()
    return byteArrayFromInts(list)
}

fun makeByteArrayForValueChange(
    registryAddress: Int,
    newValue: Int,
    transactionNum: UShort,
    outputType: OutputType
): ByteArray {
    val messageLength =
        if (outputType == OutputType.INT16 || outputType == OutputType.UINT16) 0x9 else 0xB
    val deviceAddress = 0x01
    val function = 0x10
    val amountToWrite =
        if (outputType == OutputType.INT16 || outputType == OutputType.UINT16) 0x1 else 0x2
    val newValueBytes =
        when (outputType) {
            OutputType.INT32 -> newValue.makeIntByteList()
            OutputType.REAL32 -> TODO()
            else -> newValue.makeShortByteList()
        }
    val bytesNext =
        if (outputType == OutputType.INT16 || outputType == OutputType.UINT16) 0x02 else 0x04

    val list = transactionNum.toInt().makeShortByteList() +
            listOf(
                0x00,
                0x00,
                0x00,
                messageLength,
                deviceAddress,
                function,
            ) + registryAddress.makeShortByteList() + amountToWrite.makeShortByteList() +
            bytesNext + newValueBytes

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
        if (this.functionNumber == 0x10) {
            if (this.outputType == OutputType.INT16
                || this.outputType == OutputType.UINT16
            ) 12
            else 14
        } else {
            if (this.outputType == OutputType.INT16
                || this.outputType == OutputType.UINT16
            ) 11
            else 13
        }
    )

fun Int.makeShortByteList(): List<Int> = listOf(this shr 8, this and 0xFF)

fun Int.makeIntByteList(): List<Int> = listOf(
    (this shr 24) and 0xFF,
    (this shr 16) and 0xFF,
    (this shr 8) and 0xFF,
    this and 0xFF
)