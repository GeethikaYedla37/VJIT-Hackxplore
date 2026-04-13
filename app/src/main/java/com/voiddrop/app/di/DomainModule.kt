package com.voiddrop.app.di


import com.voiddrop.app.domain.repository.ConnectionRepository
import com.voiddrop.app.domain.repository.FileTransferRepository
import com.voiddrop.app.domain.usecase.connection.GeneratePairingCodeUseCase
import com.voiddrop.app.domain.usecase.connection.ConnectToPeerUseCase
import com.voiddrop.app.domain.usecase.connection.GetConnectionStatusUseCase
import com.voiddrop.app.domain.usecase.filetransfer.SendFilesUseCase
import com.voiddrop.app.domain.usecase.filetransfer.ReceiveFilesUseCase
import com.voiddrop.app.domain.usecase.filetransfer.GetTransferHistoryUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing domain layer dependencies (use cases and managers).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {


    companion object {
        @Provides
        @Singleton
        fun provideGeneratePairingCodeUseCase(
            connectionRepository: ConnectionRepository
        ): GeneratePairingCodeUseCase {
            return GeneratePairingCodeUseCase(connectionRepository)
        }

        @Provides
        @Singleton
        fun provideConnectToPeerUseCase(
            connectionRepository: ConnectionRepository
        ): ConnectToPeerUseCase {
            return ConnectToPeerUseCase(connectionRepository)
        }

        @Provides
        @Singleton
        fun provideGetConnectionStatusUseCase(
            connectionRepository: ConnectionRepository
        ): GetConnectionStatusUseCase {
            return GetConnectionStatusUseCase(connectionRepository)
        }

        @Provides
        @Singleton
        fun provideSendFilesUseCase(
            fileTransferRepository: FileTransferRepository
        ): SendFilesUseCase {
            return SendFilesUseCase(fileTransferRepository)
        }

        @Provides
        @Singleton
        fun provideReceiveFilesUseCase(
            fileTransferRepository: FileTransferRepository
        ): ReceiveFilesUseCase {
            return ReceiveFilesUseCase(fileTransferRepository)
        }

        @Provides
        @Singleton
        fun provideGetTransferHistoryUseCase(
            fileTransferRepository: FileTransferRepository
        ): GetTransferHistoryUseCase {
            return GetTransferHistoryUseCase(fileTransferRepository)
        }
    }
}