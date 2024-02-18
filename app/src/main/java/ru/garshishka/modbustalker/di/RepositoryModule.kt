package ru.garshishka.modbustalker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.garshishka.modbustalker.data.RegistryOutputRepository
import ru.garshishka.modbustalker.data.RegistryOutputRepositoryImpl
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
interface RepositoryModule {
    @Singleton
    @Binds
    fun bindsCatalogueRepository(
        catalogueRepositoryImpl: RegistryOutputRepositoryImpl
    ): RegistryOutputRepository
}