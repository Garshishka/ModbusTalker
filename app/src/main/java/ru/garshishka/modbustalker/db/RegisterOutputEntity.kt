package ru.garshishka.modbustalker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.garshishka.modbustalker.data.RegisterOutput
import ru.garshishka.modbustalker.data.enums.OutputType

@Entity(tableName = "registers_output_table")
data class RegisterOutputEntity(
    @PrimaryKey
    val address: Int,
    val value: Int?,
    val valueFloat: Float?,
    val transactionNumber: Int,
    val outputType: OutputType,
) {
    fun toDto() = RegisterOutput(address, value, valueFloat, transactionNumber, outputType)

    companion object {
        fun fromDto(dto: RegisterOutput) =
            RegisterOutputEntity(dto.address,
                if (dto.outputType != OutputType.REAL32) dto.value else null,
                if (dto.outputType == OutputType.REAL32) dto.valueFloat else null,
                dto.transactionNumber, dto.outputType)
    }
}
