package ru.garshishka.modbustalker.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RegisterOutputDao {
    @Query("SELECT * FROM registers_output_table")
    fun getAll(): LiveData<List<RegisterOutputEntity>>

    @Upsert
    suspend fun save(output: RegisterOutputEntity)

    @Query("SELECT * FROM registers_output_table WHERE transactionNumber =:transactionNumber")
    fun findByTransactionNumber(transactionNumber: Int): RegisterOutputEntity?
    @Query("SELECT * FROM registers_output_table WHERE address =:address")
    fun findByAddress(address: Int): RegisterOutputEntity?

    @Query("DELETE FROM registers_output_table WHERE address = :address")
    fun deleteById(address: Int)

    @Query("DELETE FROM registers_output_table")
    fun deleteAll()
}