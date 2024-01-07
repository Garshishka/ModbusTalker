package ru.garshishka.modbustalker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.garshishka.modbustalker.data.RegisterOutput

@Entity(tableName = "registers_output_table")
data class RegisterOutputEntity(
    @PrimaryKey
    val address: Int,
    val value: Int?,
    val transactionNumber: Int,
) {
    fun toDto() = RegisterOutput(address, value, transactionNumber)

    companion object {
        fun fromDto(dto: RegisterOutput) =
            RegisterOutputEntity(dto.address, dto.value, dto.transactionNumber)
    }
}
