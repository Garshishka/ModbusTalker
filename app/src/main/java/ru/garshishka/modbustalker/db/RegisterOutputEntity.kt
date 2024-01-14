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
    val transactionNumber: Int,
    val outputType: OutputType,
) {
    fun toDto() = RegisterOutput(address, value, transactionNumber, outputType)

    companion object {
        fun fromDto(dto: RegisterOutput) =
            RegisterOutputEntity(dto.address, dto.value, dto.transactionNumber, dto.outputType)
    }
}
