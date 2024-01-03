package ru.garshishka.modbustalker.utils

fun byteArrayFromHex(numbers: List<Int>) =
    ByteArray(numbers.size) { pos -> numbers[pos].toByte() }

fun makeByteArrayForAnalogueOut(registryAddress: Int, amountToCheck: Int): ByteArray {
    val identifier = 1
    val messageLength = 0x06
    val deviceAddress = 0x01
    val function = 0x03

    val list = identifier.makeByteList() +
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

fun ByteArray.read2BytesOutput(): Int =
    (this[this.size - 2].toInt() and 0xff shl 8) or
            (this[this.size - 1].toInt() and 0xff)


private fun read4BytesFromBuffer(buffer: ByteArray, offset: Int): Int {
    return (buffer[offset + 3].toInt() shl 24) or
            (buffer[offset + 2].toInt() and 0xff shl 16) or
            (buffer[offset + 1].toInt() and 0xff shl 8) or
            (buffer[offset + 0].toInt() and 0xff)
}