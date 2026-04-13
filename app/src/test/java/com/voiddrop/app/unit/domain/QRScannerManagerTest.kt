package com.voiddrop.app.unit.domain

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import com.voiddrop.app.data.remote.QRScanner
import com.voiddrop.app.data.remote.ScanResult
import com.voiddrop.app.domain.manager.QRScannerManager
import com.voiddrop.app.domain.manager.ValidationResult
import com.voiddrop.app.domain.model.ConnectionInfo
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for QRScannerManager.
 */
class QRScannerManagerTest {
    
    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var dataQRScanner: QRScanner
    private lateinit var qrScannerManager: QRScannerManager
    
    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        dataQRScanner = mockk(relaxed = true)
        
        every { context.packageManager } returns packageManager
        
        qrScannerManager = QRScannerManager(context, dataQRScanner)
    }
    
    @Test
    fun `generateQRCode should delegate to data layer`() = runTest {
        // Given
        val connectionInfo = ConnectionInfo(
            deviceId = "test-device",
            deviceName = "Test Device",
            signalingData = "test-data",
            iceServers = listOf("stun:example.com")
        )
        val expectedBitmap = mockk<Bitmap>()
        coEvery { dataQRScanner.generateQRCode(connectionInfo) } returns Result.success(expectedBitmap)
        
        // When
        val result = qrScannerManager.generateQRCode(connectionInfo)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        assertEquals("Should return expected bitmap", expectedBitmap, result.getOrNull())
        coVerify { dataQRScanner.generateQRCode(connectionInfo) }
    }
    
    @Test
    fun `startScanning should return PermissionRequired when camera permission not granted`() = runTest {
        // Given
        mockkStatic(ContextCompat::class)
        every { 
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
        } returns PackageManager.PERMISSION_DENIED
        
        // When
        val result = qrScannerManager.startScanning().first()
        
        // Then
        assertEquals("Should return PermissionRequired", 
            com.voiddrop.app.domain.manager.ScanResult.PermissionRequired, result)
        
        unmockkStatic(ContextCompat::class)
    }
    
    @Test
    fun `startScanning should delegate to data layer when permission granted`() = runTest {
        // Given
        mockkStatic(ContextCompat::class)
        every { 
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val dataResult = ScanResult.Success("test-qr-data")
        every { dataQRScanner.startScanning() } returns flowOf(dataResult)
        
        // When
        val result = qrScannerManager.startScanning().first()
        
        // Then
        assertTrue("Should be Success result", result is com.voiddrop.app.domain.manager.ScanResult.Success)
        assertEquals("Should contain QR data", "test-qr-data", 
            (result as com.voiddrop.app.domain.manager.ScanResult.Success).qrData)
        
        unmockkStatic(ContextCompat::class)
    }
    
    @Test
    fun `validateQRCode should return Valid result for successful validation`() = runTest {
        // Given
        val qrData = "test-qr-data"
        val connectionInfo = ConnectionInfo(
            deviceId = "test-device",
            deviceName = "Test Device", 
            signalingData = "test-data",
            iceServers = listOf("stun:example.com")
        )
        coEvery { dataQRScanner.validateQRCode(qrData) } returns Result.success(connectionInfo)
        
        // When
        val result = qrScannerManager.validateQRCode(qrData)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        val validationResult = result.getOrNull()
        assertTrue("Should be Valid result", validationResult is ValidationResult.Valid)
        assertEquals("Should contain connection info", connectionInfo, 
            (validationResult as ValidationResult.Valid).connectionInfo)
    }
    
    @Test
    fun `validateQRCode should return Invalid result for failed validation`() = runTest {
        // Given
        val qrData = "invalid-qr-data"
        val errorMessage = "Invalid QR code format"
        coEvery { dataQRScanner.validateQRCode(qrData) } returns Result.failure(Exception(errorMessage))
        
        // When
        val result = qrScannerManager.validateQRCode(qrData)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        val validationResult = result.getOrNull()
        assertTrue("Should be Invalid result", validationResult is ValidationResult.Invalid)
        assertEquals("Should contain error message", errorMessage, 
            (validationResult as ValidationResult.Invalid).reason)
    }
    
    @Test
    fun `isCameraAvailable should check system feature`() = runTest {
        // Given
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) } returns true
        
        // When
        val result = qrScannerManager.isCameraAvailable()
        
        // Then
        assertTrue("Should return true when camera available", result)
        verify { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    }
    
    @Test
    fun `isCameraAvailable should return false on exception`() = runTest {
        // Given
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) } throws RuntimeException("Test exception")
        
        // When
        val result = qrScannerManager.isCameraAvailable()
        
        // Then
        assertFalse("Should return false on exception", result)
    }
    
    @Test
    fun `requestCameraPermission should succeed when permission already granted`() = runTest {
        // Given
        mockkStatic(ContextCompat::class)
        every { 
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
        } returns PackageManager.PERMISSION_GRANTED
        
        // When
        val result = qrScannerManager.requestCameraPermission()
        
        // Then
        assertTrue("Should succeed when permission granted", result.isSuccess)
        
        unmockkStatic(ContextCompat::class)
    }
    
    @Test
    fun `requestCameraPermission should fail when permission not granted`() = runTest {
        // Given
        mockkStatic(ContextCompat::class)
        every { 
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
        } returns PackageManager.PERMISSION_DENIED
        
        // When
        val result = qrScannerManager.requestCameraPermission()
        
        // Then
        assertTrue("Should fail when permission not granted", result.isFailure)
        assertTrue("Should indicate UI interaction required", 
            result.exceptionOrNull()!!.message!!.contains("user interaction"))
        
        unmockkStatic(ContextCompat::class)
    }
    
    @Test
    fun `stopScanning should delegate to data layer`() {
        // When
        qrScannerManager.stopScanning()
        
        // Then
        verify { dataQRScanner.stopScanning() }
    }
}