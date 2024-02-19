package ru.garshishka.modbustalker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.viewmodel.interactor.CommunicationInteractor
import ru.garshishka.modbustalker.viewmodel.interactor.CommunicationInteractorImpl
import ru.garshishka.modbustalker.viewmodel.interactor.ConnectionInteractor
import ru.garshishka.modbustalker.viewmodel.interactor.ConnectionInteractorKtorImpl
import ru.garshishka.modbustalker.viewmodel.interactor.DebugInteractor
import ru.garshishka.modbustalker.viewmodel.interactor.DebugInteractorImpl
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class InteractorModule {
    @Provides
    @Singleton
    fun provideDebugInteractor(): DebugInteractor {
        return DebugInteractorImpl()
    }

    @Provides
    @Singleton
    fun provideConnectionInteractor(
        debugInteractor: DebugInteractor,
        repository: RegistryOutputRepository
    ): ConnectionInteractor {
        return ConnectionInteractorKtorImpl(debugInteractor, repository)
    }

    @Provides
    @Singleton
    fun providesCommunicationInteractor(
        debugInteractor: DebugInteractor,
        connectionInteractor: ConnectionInteractor,
        repository: RegistryOutputRepository,
    ) : CommunicationInteractor{
        return CommunicationInteractorImpl(debugInteractor, connectionInteractor, repository)
    }
}