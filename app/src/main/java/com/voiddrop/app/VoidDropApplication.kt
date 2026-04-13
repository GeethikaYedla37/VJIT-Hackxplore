package com.voiddrop.app

import android.app.Application
import android.content.Intent
import com.voiddrop.app.data.local.FileSystemManager
import com.voiddrop.app.data.service.InstantWipeService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for VoidDrop Native MVP.
 */
@HiltAndroidApp
class VoidDropApplication : Application() {
    
    @Inject
    lateinit var fileSystemManager: FileSystemManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Security: Start the background service that wipes the Void on app close
        startService(Intent(this, InstantWipeService::class.java))
        
        // Safety: Also wipe any leftovers from previous sessions on startup
        MainScope().launch {
            try {
                fileSystemManager.clearAllFiles()
            } catch (e: Exception) {
                // Background cleanup
            }
        }
    }
}