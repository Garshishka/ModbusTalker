package ru.garshishka.modbustalker

import android.app.Application
import ru.garshishka.modbustalker.di.DependencyContainer

class MainApplication: Application() {
    override fun onCreate() {
        DependencyContainer.initApp(this)
        super.onCreate()
    }
}