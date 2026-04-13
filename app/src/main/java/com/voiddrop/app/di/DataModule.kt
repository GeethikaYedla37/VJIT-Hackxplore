package com.voiddrop.app.di

import android.content.Context
import com.voiddrop.app.data.local.FileSystemManager
import com.voiddrop.app.data.local.FileSystemManagerImpl
import com.voiddrop.app.data.repository.ConnectionRepositoryImpl
import com.voiddrop.app.data.repository.FileTransferRepositoryImpl
import com.voiddrop.app.domain.repository.ConnectionRepository
import com.voiddrop.app.domain.repository.FileTransferRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing data layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindFileTransferRepository(
        fileTransferRepositoryImpl: FileTransferRepositoryImpl
    ): FileTransferRepository

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(
        connectionRepositoryImpl: ConnectionRepositoryImpl
    ): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindFileSystemManager(
        fileSystemManagerImpl: FileSystemManagerImpl
    ): FileSystemManager

    companion object {
        @Provides
        @Singleton
        fun provideApplicationContext(@ApplicationContext context: Context): Context {
            return context
        }
    }
}