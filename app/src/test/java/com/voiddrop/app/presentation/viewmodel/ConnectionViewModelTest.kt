package com.voiddrop.app.presentation.viewmodel

import com.voiddrop.app.domain.model.ConnectionState
import com.voiddrop.app.domain.model.PairingCode
import com.voiddrop.app.domain.model.PeerInfo
import com.voiddrop.app.domain.repository.ConnectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private lateinit var viewModel: ConnectionViewModel
    private lateinit var repository: ConnectionRepository
    
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        repository = mockk(relaxed = true)
        
        // Mock flows
        every { repository.getConnectionStatus() } returns MutableStateFlow(ConnectionState.DISCONNECTED)
        every { repository.getIncomingPairingRequests() } returns MutableStateFlow(null)
        every { repository.getSessions() } returns emptyFlow()
        
        viewModel = ConnectionViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `generatePairingCode updates uiState on success`() = testScope.runTest {
        val mockConnectionInfo = com.voiddrop.app.domain.model.ConnectionInfo("device1", "Test Device", "{}", emptyList())
        val testCode = PairingCode("123456", mockConnectionInfo, System.currentTimeMillis() + 60000, "qr-data")
        coEvery { repository.generatePairingCode() } returns Result.success(testCode)
        
        viewModel.generatePairingCode()
        advanceUntilIdle() // Wait for coroutine
        
        val state = viewModel.uiState.value
        assertEquals("123456", state.generatedCode)
        assertFalse(state.isLoading)
        assertEquals(null, state.error)
    }
    
    @Test
    fun `generatePairingCode updates uiState on failure`() = testScope.runTest {
        val errorMessage = "Network Error"
        coEvery { repository.generatePairingCode() } returns Result.failure(Exception(errorMessage))
        
        viewModel.generatePairingCode()
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(errorMessage, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `connectToPeer validates inputs locally`() = testScope.runTest {
        viewModel.connectToPeer("123", "Valid Alias")
        assertEquals("Invalid Code Length", viewModel.uiState.value.error)
        
        viewModel.connectToPeer("123456", "")
        assertEquals("Alias must be 1-15 characters", viewModel.uiState.value.error)
    }

    @Test
    fun `connectToPeer flows through repository on success`() = testScope.runTest {
        val codeStr = "123456"
        val alias = "Alice"
        val mockConnectionInfo = com.voiddrop.app.domain.model.ConnectionInfo("device2", "Peer Device", "{}", emptyList())
        val pairingCode = PairingCode(codeStr, mockConnectionInfo, System.currentTimeMillis() + 60000, "qr-data")
        
        coEvery { repository.validateQRCode(codeStr) } returns Result.success(pairingCode)
        coEvery { repository.connectToPeer(pairingCode, alias) } returns Result.success(Unit)
        
        viewModel.connectToPeer(codeStr, alias)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { repository.connectToPeer(pairingCode, alias) }
    }
}
