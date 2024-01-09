package ru.garshishka.modbustalker.utils

fun byteArrayFromHex(numbers: List<Int>) =
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
    return byteArrayFromHex(list)
}

fun ByteArray.getTransactionAndFunctionNumber(): Pair<Int, Int> =
        (this.read2BytesFromBuffer(0) to this.read1ByteFromBuffer(7))


fun ByteArray.getOutput(fourByte: Boolean = false): Int =
    if (fourByte) {
        this.read4BytesFromBuffer(this.size - 4)
    } else {
        this.read2BytesFromBuffer(this.size - 2)
    }

fun ByteArray.read1ByteFromBuffer(offset: Int): Int =
    this[offset].toInt() and 0xff

fun ByteArray.read2BytesFromBuffer(offset: Int): Int =
    (this[offset + 0].toInt() and 0xff shl 8) or
            (this[offset + 1].toInt() and 0xff)

fun ByteArray.read4BytesFromBuffer(offset: Int): Int =
    (this[offset + 3].toInt() shl 24) or
            (this[offset + 2].toInt() and 0xff shl 16) or
            (this[offset + 1].toInt() and 0xff shl 8) or
            (this[offset + 0].toInt() and 0xff)

fun Int.makeByteList(): List<Int> =
    if (this > 16) {
        val hex = this.toString(16)
        listOf(hex.substring(0, 1).toInt(16), hex.substring(1, 2).toInt(16))
    } else {
        listOf(0x00, this)
    }
