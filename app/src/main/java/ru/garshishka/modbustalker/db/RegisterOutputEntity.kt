package ru.garshishka.modbustalker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.garshishka.modbustalker.data.RegisterOutput
import ru.garshishka.modbustalker.data.enums.OutputType
import ru.garshishka.modbustalker.data.enums.RegisterConnection

@Entity(tableName = "registers_output_table")
data class RegisterOutputEntity(
    @PrimaryKey
    val place: Int,
    val name: String,
    val address: Int,
    val value: Int?,
    val valueFloat: Float?,
    val transactionNumber: Int,
    val outputType: OutputType,
    val status: RegisterConnection,
) {
    fun toDto() = RegisterOutput(
        place,
        name,
        address,
        value,
        valueFloat,
        transactionNumber,
        outputType,
        status
    )

    companion object {
        fun fromDto(dto: RegisterOutput) =
            RegisterOutputEntity(
                dto.place, dto.name, dto.address,
                if (dto.outputType != OutputType.REAL32) dto.value else null,
                if (dto.outputType == OutputType.REAL32) dto.valueFloat else null,
                dto.transactionNumber, dto.outputType, dto.status
            )
    }
}
