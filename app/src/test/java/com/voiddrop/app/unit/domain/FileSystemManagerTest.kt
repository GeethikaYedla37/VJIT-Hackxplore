package com.voiddrop.app.unit.domain

import android.content.Context
import android.net.Uri
import com.voiddrop.app.data.local.FileSystemManager as DataFileSystemManager
import com.voiddrop.app.domain.manager.FileSystemManagerImpl
import com.voiddrop.app.domain.model.FileInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for FileSystemManagerImpl.
 */
class FileSystemManagerTest {
    
    private lateinit var dataFileSystemManager: DataFileSystemManager
    private lateinit var fileSystemManager: FileSystemManagerImpl
    
    @Before
    fun setup() {
        dataFileSystemManager = mockk()
        fileSystemManager = FileSystemManagerImpl(dataFileSystemManager)
    }
    
    @Test
    fun `readFile should delegate to data layer`() = runTest {
        // Given
        val uri = mockk<Uri>()
        val expectedData = byteArrayOf(1, 2, 3, 4, 5)
        coEvery { dataFileSystemManager.readFile(uri) } returns flowOf(expectedData)
        
        // When
        val result = fileSystemManager.readFile(uri).toList()
        
        // Then
        assertEquals(1, result.size)
        assertArrayEquals(expectedData, result[0])
        coVerify { dataFileSystemManager.readFile(uri) }
    }
    
    @Test
    fun `writeFile should sanitize file name and delegate to data layer`() = runTest {
        // Given
        val fileName = "test/file:name*.txt"
        val data = flowOf(byteArrayOf(1, 2, 3))
        val expectedUri = mockk<Uri>()
        coEvery { dataFileSystemManager.writeFile("test_file_name_.txt", data) } returns Result.success(expectedUri)
        
        // When
        val result = fileSystemManager.writeFile(fileName, data)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedUri, result.getOrNull())
        coVerify { dataFileSystemManager.writeFile("test_file_name_.txt", data) }
    }
    
    @Test
    fun `writeFile should fail for blank file name`() = runTest {
        // Given
        val fileName = "   "
        val data = flowOf(byteArrayOf(1, 2, 3))
        
        // When
        val result = fileSystemManager.writeFile(fileName, data)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    
    @Test
    fun `getFileInfo should delegate to data layer`() = runTest {
        // Given
        val uri = mockk<Uri>()
        val expectedFileInfo = FileInfo(
            uri = uri,
            name = "test.txt",
            size = 1024L,
            mimeType = "text/plain"
        )
        coEvery { dataFileSystemManager.getFileInfo(uri) } returns Result.success(expectedFileInfo)
        
        // When
        val result = fileSystemManager.getFileInfo(uri)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedFileInfo, result.getOrNull())
        coVerify { dataFileSystemManager.getFileInfo(uri) }
    }
    
    @Test
    fun `createVoidDropDirectory should delegate to data layer`() = runTest {
        // Given
        val expectedDir = mockk<File>()
        coEvery { dataFileSystemManager.createVoidDropDirectory() } returns Result.success(expectedDir)
        
        // When
        val result = fileSystemManager.createVoidDropDirectory()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedDir, result.getOrNull())
        coVerify { dataFileSystemManager.createVoidDropDirectory() }
    }
    
    @Test
    fun `getTransferredFiles should delegate to data layer`() = runTest {
        // Given
        val expectedFiles = listOf(
            FileInfo(
                uri = mockk(),
                name = "file1.txt",
                size = 100L,
                mimeType = "text/plain",
                transferDate = System.currentTimeMillis()
            )
        )
        every { dataFileSystemManager.getTransferredFiles() } returns flowOf(expectedFiles)
        
        // When
        val result = fileSystemManager.getTransferredFiles().toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(expectedFiles, result[0])
    }
    
    @Test
    fun `hasEnoughSpace should return true for zero bytes`() = runTest {
        // When
        val result = fileSystemManager.hasEnoughSpace(0L)
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `hasEnoughSpace should delegate to data layer for positive bytes`() = runTest {
        // Given
        val requiredBytes = 1024L
        coEvery { dataFileSystemManager.hasEnoughSpace(requiredBytes) } returns true
        
        // When
        val result = fileSystemManager.hasEnoughSpace(requiredBytes)
        
        // Then
        assertTrue(result)
        coVerify { dataFileSystemManager.hasEnoughSpace(requiredBytes) }
    }
    
    @Test
    fun `deleteFile should delegate to data layer`() = runTest {
        // Given
        val uri = mockk<Uri>()
        coEvery { dataFileSystemManager.deleteFile(uri) } returns Result.success(Unit)
        
        // When
        val result = fileSystemManager.deleteFile(uri)
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { dataFileSystemManager.deleteFile(uri) }
    }
    
    @Test
    fun `validateFileAccess should return success for valid file`() = runTest {
        // Given
        val uri = mockk<Uri>()
        val fileInfo = FileInfo(
            uri = uri,
            name = "test.txt",
            size = 1024L,
            mimeType = "text/plain"
        )
        coEvery { dataFileSystemManager.getFileInfo(uri) } returns Result.success(fileInfo)
        
        // When
        val result = fileSystemManager.validateFileAccess(uri)
        
        // Then
        assertTrue(result.isSuccess)
        val accessInfo = result.getOrNull()!!
        assertTrue(accessInfo.canRead)
        assertFalse(accessInfo.canWrite)
        assertTrue(accessInfo.exists)
        assertEquals(1024L, accessInfo.size)
    }
    
    @Test
    fun `getSupportedFileTypes should delegate to data layer`() {
        // Given
        val expectedTypes = listOf("text/plain", "image/jpeg")
        every { dataFileSystemManager.getSupportedFileTypes() } returns expectedTypes
        
        // When
        val result = fileSystemManager.getSupportedFileTypes()
        
        // Then
        assertEquals(expectedTypes, result)
    }
    
    @Test
    fun `isFileTypeSupported should delegate to data layer`() {
        // Given
        val mimeType = "text/plain"
        every { dataFileSystemManager.isFileTypeSupported(mimeType) } returns true
        
        // When
        val result = fileSystemManager.isFileTypeSupported(mimeType)
        
        // Then
        assertTrue(result)
    }
}