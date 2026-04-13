package com.voiddrop.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_LOGS = 500
    private val logFlow = MutableStateFlow<List<String>>(emptyList())
    val logs = logFlow.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun v(tag: String, message: String) {
        Log.v(tag, message)
        appendLog("V", tag, message)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        appendLog("D", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        appendLog("E", tag, "$message ${throwable?.message ?: ""}")
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        appendLog("W", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        appendLog("I", tag, message)
    }

    private fun appendLog(level: String, tag: String, message: String) {
        synchronized(this) {
            val time = timeFormat.format(Date())
            val logLine = "[$time] $level/$tag: $message"
            
            val currentLogs = logFlow.value.toMutableList()
            currentLogs.add(0, logLine) // Add to top
            
            if (currentLogs.size > MAX_LOGS) {
                currentLogs.removeAt(currentLogs.size - 1)
            }
            
            logFlow.value = currentLogs
        }
    }
    
    fun clearLogs() {
        logFlow.value = emptyList()
    }
}
