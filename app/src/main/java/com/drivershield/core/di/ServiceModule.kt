package com.drivershield.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    // TODO: Proveer dependencias de servicios (TimerService, NotificationManager, etc.)
}
