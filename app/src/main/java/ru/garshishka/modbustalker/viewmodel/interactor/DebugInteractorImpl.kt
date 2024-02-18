package ru.garshishka.modbustalker.viewmodel.interactor

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ru.garshishka.modbustalker.utils.NotFoundTransactionNumberErrorException
import ru.garshishka.modbustalker.utils.RegisterWatchException
import ru.garshishka.modbustalker.utils.ResponseErrorException
import ru.garshishka.modbustalker.utils.SingleLiveEvent

class DebugInteractorImpl : DebugInteractor {
    private val _debugText = MutableLiveData<String>()
    override val debugText: LiveData<String>
        get() = _debugText

    private val _transactionNotFoundError = SingleLiveEvent<Int>()
    override val transactionNotFoundError: LiveData<Int>
        get() = _transactionNotFoundError
    private val _registerWatchError = SingleLiveEvent<Pair<String, Int>>()
    override val registerWatchError: LiveData<Pair<String, Int>>
        get() = _registerWatchError
    private val _registerResponseError = SingleLiveEvent<Pair<Int, Int>>()
    override val registerResponseError: LiveData<Pair<Int, Int>>
        get() = _registerResponseError

    init {
        _debugText.value = ""
    }

    override fun addToLog(message: String, logType: LogType) {
        when (logType) {
            LogType.LOG -> {
                Log.d("ModBus", message)
                addToDebugText("$message\n")
            }

            LogType.ERROR -> {
                Log.e("ModBus", message)
                addToDebugText("!ERROR! $message\n")
            }

            LogType.RESPONSE -> {
                Log.i("ModBus", message)
                addToDebugText("RESPONSE $message\n")
            }
        }
    }

    override fun getException(e: Exception, message: String) {
        addToLog("$message: $e", LogType.ERROR)
        addToLog(e.message.toString(), LogType.ERROR)
        when (e) {
            is NotFoundTransactionNumberErrorException -> {
                _transactionNotFoundError.postValue(e.transactionNumber)
            }

            is ResponseErrorException -> {
                _registerResponseError.postValue(e.errorCode to e.registerNumber)
            }

            is RegisterWatchException -> {
                _registerWatchError.postValue((e.message ?: e.toString()) to e.registerNumber)
            }
        }
    }

    private fun addToDebugText(message: String) {
        _debugText.value?.let {
            _debugText.value = (message + it).lines().takeLast(100).joinToString("\n")
        }
    }
}