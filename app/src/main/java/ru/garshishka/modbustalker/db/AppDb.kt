package ru.garshishka.modbustalker.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RegisterOutputEntity::class], version = 8, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun registerOutputDao(): RegisterOutputDao
}