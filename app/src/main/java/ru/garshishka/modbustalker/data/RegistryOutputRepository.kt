package ru.garshishka.modbustalker.data

import androidx.lifecycle.LiveData
import ru.garshishka.modbustalker.db.RegisterOutputEntity

interface RegistryOutputRepository {
    fun getAll() : LiveData<List<RegisterOutputEntity>>
    suspend fun save(output: RegisterOutput)
    suspend fun delete(addressToDelete: Int)

    fun deleteAll()
}