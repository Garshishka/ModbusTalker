package ru.garshishka.modbustalker.utils

fun Int.makeByteList(): List<Int>{
    if (this>16){
        val hex = this.toString(16)
        return listOf(hex.substring(0,1).toInt(16),hex.substring(1,2).toInt(16))
    } else{
        return listOf(0x00,this)
    }
}