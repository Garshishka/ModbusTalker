package ru.garshishka.modbustalker.di

import android.content.Context
import androidx.room.Room.databaseBuilder
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.data.RegistryOutputRepositoryImpl
import ru.garshishka.modbustalker.db.AppDb

class DependencyContainer private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: DependencyContainer? = null

        fun initApp(context: Context) {
            instance = DependencyContainer(context)
        }

        fun getInstance(): DependencyContainer {
            return instance!!
        }
    }

    private val appDb =
        databaseBuilder(context, AppDb::class.java, "app.db")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()

    private val gridPointDao = appDb.registerOutputDao()

    val repository: RegistryOutputRepository = RegistryOutputRepositoryImpl(gridPointDao)
}