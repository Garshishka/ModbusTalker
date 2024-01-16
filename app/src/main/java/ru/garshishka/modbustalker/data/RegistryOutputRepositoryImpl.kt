package ru.garshishka.modbustalker.data

import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.db.RegisterOutputDao
import ru.garshishka.modbustalker.db.RegisterOutputEntity
import ru.garshishka.modbustalker.utils.readBytes

class RegistryOutputRepositoryImpl(private val dao: RegisterOutputDao) : RegistryOutputRepository {
    override fun getAll() = dao.getAll()

    override suspend fun save(output: RegisterOutput) {
        dao.save(RegisterOutputEntity.fromDto(output))
    }

    override suspend fun updateValue(transactionNumber: Int, responseArray: ByteArray) {
        //TODO update for 4 byte types
        dao.findByTransactionNumber(transactionNumber)?.let {
            val offset =
                if (it.outputType == OutputType.UINT16 || it.outputType == OutputType.INT16)
                    responseArray.size - 2 else responseArray.size - 4
            dao.save(it.copy(value = responseArray.readBytes(offset,it.outputType).toInt()))
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