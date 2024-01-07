package ru.garshishka.modbustalker.data

import ru.garshishka.modbustalker.db.RegisterOutputDao
import ru.garshishka.modbustalker.db.RegisterOutputEntity

class RegistryOutputRepositoryImpl(private val dao: RegisterOutputDao) : RegistryOutputRepository {
    override fun getAll() = dao.getAll()

    override suspend fun save(output: RegisterOutput) {
        dao.save(RegisterOutputEntity.fromDto(output))
    }

    override suspend fun updateValue(transactionNumber: Int, newValue: Int) {
        dao.findByTransactionNumber(transactionNumber)?.let {
            dao.save(it.copy(value = newValue))
        }
    }

    override suspend fun delete(addressToDelete: Int) {
        dao.deleteById(addressToDelete)
    }

    override fun checkRegisterByAddress(address: Int): Boolean =
        (dao.findByAddress(address) != null)


    override fun deleteAll() {
        dao.deleteAll()
    }
}