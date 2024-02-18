package ru.garshishka.modbustalker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.data.enums.RegisterConnection
import ru.garshishka.modbustalker.db.RegisterOutputDao
import ru.garshishka.modbustalker.db.RegisterOutputEntity
import ru.garshishka.modbustalker.utils.readBytes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegistryOutputRepositoryImpl @Inject constructor(
    private val dao: RegisterOutputDao
) : RegistryOutputRepository {
    override fun getAll() = dao.getAll()

    override suspend fun save(output: RegisterOutput) = withContext(Dispatchers.IO) {
        dao.save(RegisterOutputEntity.fromDto(output))
    }

    override suspend fun updateValue(transactionNumber: Int, responseArray: ByteArray): Unit =
        withContext(Dispatchers.IO) {
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

    override suspend fun delete(addressToDelete: Int) = withContext(Dispatchers.IO) {
        dao.deleteById(addressToDelete)
    }

    override suspend fun getRegisterByAddress(address: Int): RegisterOutput? =
        withContext(Dispatchers.IO) {
            return@withContext dao.findByAddress(address)?.toDto()
        }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}