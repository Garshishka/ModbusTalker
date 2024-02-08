package ru.garshishka.modbustalker.utils

import android.widget.EditText

fun String.setToIpInput(inputs: List<EditText>){
    val digits = this.split(".")
    for (part in digits.indices){
        inputs[part].setText(digits[part])
    }
}

fun List<EditText>.getIpString():String{
    var result = ""
    for (part in this){
        result += "${part.text}."
    }
    return result.take(result.length-1)
}