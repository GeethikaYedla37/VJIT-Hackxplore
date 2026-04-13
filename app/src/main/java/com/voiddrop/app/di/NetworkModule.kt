package com.voiddrop.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module providing network and communication layer dependencies.
 * WebRTCEngine and SupabaseSignalingManager are @Singleton @Inject constructor
 * so Hilt provides them automatically.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    // All network dependencies (WebRTCEngine, SupabaseSignalingManager) 
    // are auto-provided by Hilt via @Inject constructor
}