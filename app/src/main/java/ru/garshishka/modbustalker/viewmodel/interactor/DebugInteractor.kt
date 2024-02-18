package ru.garshishka.modbustalker.viewmodel.interactor

import androidx.lifecycle.LiveData

interface DebugInteractor {
    val debugText: LiveData<String>
    val transactionNotFoundError: LiveData<Int>
    val registerWatchError: LiveData<Pair<String, Int>>
    val registerResponseError: LiveData<Pair<Int, Int>>
    fun addToLog(message: String, logType: LogType = LogType.LOG)
    fun getException(e: Exception, message: String)
}

enum class LogType {
    LOG, ERROR, RESPONSE
}