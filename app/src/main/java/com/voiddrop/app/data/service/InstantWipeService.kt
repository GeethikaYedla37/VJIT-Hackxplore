package com.voiddrop.app.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.voiddrop.app.data.local.FileSystemManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A service that triggers a forensic wipe when the app is swiped away from recents.
 */
@AndroidEntryPoint
class InstantWipeService : Service() {

    @Inject
    lateinit var fileSystemManager: FileSystemManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("InstantWipeService", "App swiped away. Wiping Void...")
        
        // Final forensic wipe of all ephemeral files
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fileSystemManager.clearAllFiles()
                stopSelf()
            } catch (e: Exception) {
                Log.e("InstantWipeService", "Failed to wipe Void on close", e)
            }
        }
    }
}
