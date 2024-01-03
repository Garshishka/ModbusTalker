package ru.garshishka.modbustalker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.garshishka.modbustalker.data.RegistryOutputRepository

class ViewModelFactory(private val repository: RegistryOutputRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        when {
            modelClass.isAssignableFrom(ConnectionViewModel::class.java) ->
                return ConnectionViewModel(repository = repository) as T
            else ->
                throw java.lang.IllegalArgumentException("unknown ViewModel class ${modelClass.name}")
        }
    }
}