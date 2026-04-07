package com.drivershield.di

import com.drivershield.data.repository.impl.ScheduleRepositoryImpl
import com.drivershield.data.repository.impl.ShiftRepositoryImpl
import com.drivershield.domain.repository.ScheduleRepository
import com.drivershield.domain.repository.ShiftRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindShiftRepository(
        impl: ShiftRepositoryImpl
    ): ShiftRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        impl: ScheduleRepositoryImpl
    ): ScheduleRepository
}
