package ru.garshishka.modbustalker.data

import ru.garshishka.modbustalker.db.RegisterOutputDao
import ru.garshishka.modbustalker.db.RegisterOutputEntity

class RegistryOutputRepositoryImpl(private val dao: RegisterOutputDao) : RegistryOutputRepository {
    override fun getAll()= dao.getAll()

    override suspend fun save(output: RegisterOutput) {
        dao.save(RegisterOutputEntity.fromDto(output))
    }

    override suspend fun delete(addressToDelete: Int) {
        dao.deleteById(addressToDelete)
    }

    override fun deleteAll() {
        dao.deleteAll()
    }
}