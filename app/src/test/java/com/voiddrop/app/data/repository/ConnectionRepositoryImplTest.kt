package com.voiddrop.app.data.repository

import com.voiddrop.app.data.remote.ConnectionEvent
import com.voiddrop.app.data.remote.SupabaseSignalingManager
import com.voiddrop.app.data.remote.WebRTCEngine
import com.voiddrop.app.domain.model.ConnectionInfo
import com.voiddrop.app.domain.model.PairingCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionRepositoryImplTest {

    private lateinit var repository: ConnectionRepositoryImpl
    private lateinit var webRTCEngine: WebRTCEngine
    private lateinit var signalingManager: SupabaseSignalingManager
    
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testPeerId = "test-peer-id-123"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        
        webRTCEngine = mockk(relaxed = true)
        signalingManager = mockk(relaxed = true)
        
        val eventFlow = MutableSharedFlow<ConnectionEvent>()
        every { webRTCEngine.getConnectionEvents() } returns eventFlow
        
        repository = ConnectionRepositoryImpl(webRTCEngine, signalingManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `generatePairingCode returns 6 digit code`() = testScope.runTest {
        val result = repository.generatePairingCode()
        
        assertTrue(result.isSuccess)
        val code = result.getOrNull()?.id
        assertTrue(code != null && code.length == 6)
        assertTrue(code!!.matches(Regex("\\d+")))
        
        coVerify { signalingManager.connectToSession(any(), any()) }
    }

    @Test
    fun `connectToPeer initiates connection`() = testScope.runTest {
        val mockConnectionInfo = ConnectionInfo("device1", "Test Device", "{}", emptyList())
        val pairingCode = PairingCode("123456", mockConnectionInfo, System.currentTimeMillis() + 60000, "qr-data")
        val alias = "Alice"
        
        coEvery { webRTCEngine.initializePeerConnection(any(), any()) } returns Result.success(mockk())
        coEvery { webRTCEngine.connect(any()) } returns Result.success(Unit)

        repository.connectToPeer(pairingCode, alias)

        // Verify signaling actions
        coVerify { signalingManager.connectToSession(pairingCode.id, any()) }
        coVerify { signalingManager.sendSignal(match { it.type.name == "PAIRING_REQUEST" }) }
        
        // WebRTC init happens later upon response, so we don't verify it here
    }
}
