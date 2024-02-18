package ru.garshishka.modbustalker.data

import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.data.enums.RegisterConnection
import ru.garshishka.modbustalker.db.RegisterOutputDao
import ru.garshishka.modbustalker.db.RegisterOutputEntity
import ru.garshishka.modbustalker.utils.errors.NotFoundTransactionNumberErrorException
import ru.garshishka.modbustalker.utils.readBytes

class RegistryOutputRepositoryImpl(private val dao: RegisterOutputDao) : RegistryOutputRepository {
    override fun getAll() = dao.getAll()

    override suspend fun save(output: RegisterOutput) {
        dao.save(RegisterOutputEntity.fromDto(output))
    }

    override suspend fun updateValue(transactionNumber: Int, responseArray: ByteArray) {
        dao.findByTransactionNumber(transactionNumber)?.let {
            val offset =
                if (it.outputType == OutputType.UINT16 || it.outputType == OutputType.INT16)
                    responseArray.size - 2 else responseArray.size - 4
            dao.save(
                if (it.outputType != OutputType.REAL32) it.copy(
                    value = responseArray.readBytes(offset, it.outputType).toInt(),
                    status = RegisterConnection.WORKING
                ) else it.copy(
                    valueFloat = responseArray.readBytes(offset, it.outputType).toFloat(),
                    status = RegisterConnection.WORKING
                )
            )
        }
    }

    override suspend fun delete(addressToDelete: Int) {
        dao.deleteById(addressToDelete)
    }

    override fun findRegisterByTransaction(transactionNumber: Int): RegisterOutput =
        dao.findByTransactionNumber(transactionNumber)?.toDto()
            ?: throw NotFoundTransactionNumberErrorException(transactionNumber)

    override fun getRegisterByAddress(address: Int): RegisterOutput? =
        dao.findByAddress(address)?.toDto()


    override fun deleteAll() {
        dao.deleteAll()
    }
}