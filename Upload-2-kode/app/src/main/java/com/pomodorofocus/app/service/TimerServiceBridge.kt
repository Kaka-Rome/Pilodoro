package com.pomodorofocus.app.service

/**
 * Thread-safe callback bridge between TimerForegroundService and the UI/ViewModel.
 * Allows the foreground service notification actions (Pause, Resume, Reset) and background completion
 * to instantly synchronize the ViewModel state without complex IPC.
 */
object TimerServiceBridge {

    @Volatile
    private var listener: (() -> Unit)? = null

    fun registerListener(onChanged: () -> Unit) {
        listener = onChanged
    }

    fun unregisterListener() {
        listener = null
    }

    fun notifyStateChanged() {
        listener?.invoke()
    }
}
