package com.pomodorofocus.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.notification.NotificationHelper
import com.pomodorofocus.app.notification.TimerAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps Pomodoro focus and break countdowns running reliably
 * while the app is backgrounded or the screen is turned off.
 *
 * Displays an ongoing notification in the notification shade with:
 * - Current mode (Focus, Short Break, Long Break)
 * - Remaining time in MM:SS
 * - Progress bar
 * - Interactive actions: Pause / Resume, and Reset
 */
class TimerForegroundService : Service() {
    private val logTag = "PomodoroTimerService"

    companion object {
        const val ACTION_START = "com.pomodorofocus.app.service.ACTION_START"
        const val ACTION_PAUSE = "com.pomodorofocus.app.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.pomodorofocus.app.service.ACTION_RESUME"
        const val ACTION_RESET = "com.pomodorofocus.app.service.ACTION_RESET"
        const val ACTION_NEXT = "com.pomodorofocus.app.service.ACTION_NEXT"
        const val ACTION_END_SESSION = "com.pomodorofocus.app.service.ACTION_END_SESSION"
        const val ACTION_STOP = "com.pomodorofocus.app.service.ACTION_STOP"

        const val EXTRA_SESSION_TYPE = "extra_session_type"
        const val EXTRA_TARGET_END_REALTIME = "extra_target_end_realtime"
        const val EXTRA_TOTAL_DURATION_MILLIS = "extra_total_duration_millis"
        const val EXTRA_REMAINING_MILLIS = "extra_remaining_millis"
        const val EXTRA_SESSION_ID = "extra_session_id"

        fun startService(
            context: Context,
            sessionType: SessionType,
            targetEndRealtime: Long,
            totalDurationMillis: Long,
            sessionId: Long
        ) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_TYPE, sessionType.name)
                putExtra(EXTRA_TARGET_END_REALTIME, targetEndRealtime)
                putExtra(EXTRA_TOTAL_DURATION_MILLIS, totalDurationMillis)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Handle cases where background start is restricted
                android.util.Log.e("PomodoroTimerService", "Unable to start timer foreground service", e)
            }
        }

        fun pauseService(
            context: Context,
            sessionType: SessionType,
            remainingMillis: Long,
            totalDurationMillis: Long,
            sessionId: Long
        ) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_SESSION_TYPE, sessionType.name)
                putExtra(EXTRA_REMAINING_MILLIS, remainingMillis)
                putExtra(EXTRA_TOTAL_DURATION_MILLIS, totalDurationMillis)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun resumeService(
            context: Context,
            sessionType: SessionType,
            targetEndRealtime: Long,
            totalDurationMillis: Long,
            sessionId: Long
        ) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_SESSION_TYPE, sessionType.name)
                putExtra(EXTRA_TARGET_END_REALTIME, targetEndRealtime)
                putExtra(EXTRA_TOTAL_DURATION_MILLIS, totalDurationMillis)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null
    private var tickerGeneration: Long = 0L

    private var sessionType: SessionType = SessionType.FOCUS
    private var targetEndRealtime: Long = 0L
    private var totalDurationMillis: Long = 25 * 60 * 1000L
    private var sessionId: Long = 0L
    private var isPaused: Boolean = false
    private var latestStartId: Int = 0
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val action = intent?.action ?: ACTION_STOP

        if (action in setOf(ACTION_PAUSE, ACTION_RESUME, ACTION_RESET, ACTION_NEXT, ACTION_END_SESSION)) {
            val prefs = TimerPreferences(this)
            val requestedSessionId = intent?.getLongExtra(EXTRA_SESSION_ID, 0L) ?: 0L
            val activeId = prefs.getActiveSessionId()
            if (requestedSessionId != 0L && requestedSessionId != activeId) {
                // Ignore stale controls from a notification belonging to an earlier timer.
                if (tickerJob == null) {
                    if (activeId == 0L) stopSelfResult(startId) else restoreServiceFromSavedState(prefs)
                }
                return START_NOT_STICKY
            }
            if (activeId != 0L) {
                sessionId = activeId
                sessionType = prefs.loadSavedSessionType()
                totalDurationMillis = prefs.getSavedTotalDurationMillis().takeIf { it > 0L } ?: totalDurationMillis
                targetEndRealtime = prefs.getTargetEndRealtime()
                isPaused = !prefs.isTimerRunning()
            }
        }

        when (action) {
            ACTION_START, ACTION_RESUME -> {
                handleStartOrResume(intent)
            }
            ACTION_PAUSE -> {
                handlePause(intent)
            }
            ACTION_RESET -> {
                handleReset()
            }
            ACTION_NEXT -> {
                handleNext()
            }
            ACTION_END_SESSION -> {
                handleEndSession()
            }
            ACTION_STOP -> {
                handleStop()
            }
            else -> {
                handleStop()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStartOrResume(intent: Intent?) {
        val prefs = TimerPreferences(this)

        // Tombol Resume di notifikasi tidak membawa data apa pun (extras kosong),
        // jadi semua data harus dibaca dari penyimpanan lokal (state saat di-pause).
        val typeStr = intent?.getStringExtra(EXTRA_SESSION_TYPE)
        sessionType = try {
            if (typeStr != null) SessionType.valueOf(typeStr) else prefs.loadSavedSessionType()
        } catch (_: Exception) {
            prefs.loadSavedSessionType()
        }

        val extraTargetEnd = intent?.getLongExtra(EXTRA_TARGET_END_REALTIME, 0L) ?: 0L
        val extraTotal = intent?.getLongExtra(EXTRA_TOTAL_DURATION_MILLIS, 0L) ?: 0L
        val extraSessionId = intent?.getLongExtra(EXTRA_SESSION_ID, 0L) ?: 0L

        totalDurationMillis = extraTotal.takeIf { it > 0L }
            ?: prefs.getSavedTotalDurationMillis().takeIf { it > 0L }
            ?: totalDurationMillis
        sessionId = if (extraSessionId != 0L) extraSessionId else prefs.getActiveSessionId()

        val now = SystemClock.elapsedRealtime()
        val resumedFromSavedState = extraTargetEnd <= 0L

        if (!resumedFromSavedState) {
            if (extraSessionId == 0L || prefs.getActiveSessionId() != extraSessionId) {
                // A delayed START intent must not resurrect an older generation after Next/Reset.
                restoreServiceFromSavedState(prefs)
                return
            }
            // Dimulai / dilanjutkan dari dalam aplikasi: waktu selesai sudah dihitung oleh ViewModel
            targetEndRealtime = extraTargetEnd
        } else {
            // Dilanjutkan dari tombol notifikasi: hitung ulang waktu selesai dari sisa waktu tersimpan
            val savedRemaining = prefs.getSavedRemainingMillis()
            if (savedRemaining <= 0L) {
                // Tidak ada sisa waktu yang valid: jangan anggap sesi selesai, cukup hentikan layanan
                handleStop()
                return
            }
            targetEndRealtime = now + savedRemaining

            prefs.saveActiveTimerState(
                sessionType = sessionType,
                isRunning = true,
                targetEndRealtime = targetEndRealtime,
                remainingMillis = savedRemaining,
                totalDurationMillis = totalDurationMillis,
                sessionId = sessionId
            )
            // Jadwalkan ulang alarm karena alarm lama dibatalkan saat pause
            TimerAlarmScheduler(this).scheduleAlarm(sessionType, targetEndRealtime, sessionId)
        }

        val remaining = (targetEndRealtime - now).coerceAtLeast(0L)
        prefs.saveActiveTimerState(
            sessionType = sessionType,
            isRunning = true,
            targetEndRealtime = targetEndRealtime,
            remainingMillis = remaining,
            totalDurationMillis = totalDurationMillis,
            sessionId = sessionId
        )

        isPaused = false

        // Start in foreground immediately to fulfill Android's 5s requirement
        if (!startInForeground(remaining, isPaused = false)) return

        startTicker()
        TimerServiceBridge.notifyStateChanged()
    }

    private fun handlePause(intent: Intent?) {
        cancelTicker()
        isPaused = true

        val prefs = TimerPreferences(this)
        val now = SystemClock.elapsedRealtime()
        val remaining = if (targetEndRealtime > 0L) {
            (targetEndRealtime - now).coerceAtLeast(0L)
        } else {
            (intent?.getLongExtra(EXTRA_REMAINING_MILLIS, 0L) ?: 0L).takeIf { it > 0L }
                ?: prefs.getSavedRemainingMillis().coerceAtLeast(0L)
        }
        if (remaining <= 0L) {
            onTimerFinished()
            return
        }
        // Waktu selesai tidak berlaku lagi selama pause
        targetEndRealtime = 0L

        // Save paused state
        prefs.saveActiveTimerState(
            sessionType = sessionType,
            isRunning = false,
            targetEndRealtime = 0L,
            remainingMillis = remaining,
            totalDurationMillis = totalDurationMillis,
            sessionId = sessionId
        )

        // Cancel background alarm while paused
        TimerAlarmScheduler(this).cancelAlarm()

        // Keep paused controls attached to the foreground service notification.
        if (!startInForeground(remaining, isPaused = true)) return

        TimerServiceBridge.notifyStateChanged()
    }

    private fun handleReset() {
        cancelTicker()

        val prefs = TimerPreferences(this)
        sessionType = prefs.loadSavedSessionType()
        prefs.clearActiveTimerState()
        prefs.saveNextSessionAsIdle(sessionType, prefs.loadSettings().getDurationMillis(sessionType))
        TimerAlarmScheduler(this).cancelAlarm()
        NotificationHelper.cancelOngoingNotification(this)

        TimerServiceBridge.notifyStateChanged()

        stopForegroundAndRemove()
        stopSelf(latestStartId)
    }

    private fun handleNext() {
        cancelTicker()
        val prefs = TimerPreferences(this)
        sessionType = prefs.loadSavedSessionType()
        val settings = prefs.loadSettings()
        val nextType = sessionType.upcoming(prefs.loadCompletedSessions(), settings.sessionsBeforeLongBreak)
        val duration = settings.getDurationMillis(nextType)
        TimerAlarmScheduler(this).cancelAlarm()
        val shouldStart = if (nextType == SessionType.FOCUS) settings.autoStartFocus else settings.autoStartBreaks
        NotificationHelper.cancelOngoingNotification(this)
        if (shouldStart) {
            val now = SystemClock.elapsedRealtime()
            val id = prefs.allocateSessionId()
            prefs.saveActiveTimerState(nextType, false, 0L, duration, duration, id)
            handleStartOrResume(null)
        } else {
            prefs.saveNextSessionAsIdle(nextType, duration)
            TimerServiceBridge.notifyStateChanged()
            stopForegroundAndRemove()
            stopSelf(latestStartId)
        }
    }

    private fun handleEndSession() {
        cancelTicker()
        val prefs = TimerPreferences(this)
        sessionType = prefs.loadSavedSessionType()
        if (sessionType != SessionType.FOCUS) return
        TimerAlarmScheduler(this).cancelAlarm()
        NotificationHelper.cancelOngoingNotification(this)
        val duration = prefs.loadSettings().getDurationMillis(SessionType.FOCUS)
        prefs.clearActiveTimerState()
        prefs.saveNextSessionAsIdle(SessionType.FOCUS, duration)
        TimerServiceBridge.notifyStateChanged()
        stopForegroundAndRemove()
        stopSelf(latestStartId)
    }

    private fun handleStop() {
        cancelTicker()

        NotificationHelper.cancelOngoingNotification(this)
        stopForegroundAndRemove()
        stopSelf(latestStartId)
    }

    private fun startInForeground(remaining: Long, isPaused: Boolean): Boolean {
        val notification = NotificationHelper.buildOngoingNotification(
            this,
            sessionType,
            remaining,
            totalDurationMillis,
            isPaused,
            sessionId
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIFICATION_ID_ONGOING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIFICATION_ID_ONGOING,
                    notification,
                    0
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID_ONGOING, notification)
            }
            foregroundStarted = true
            return true
        } catch (e: Exception) {
            Log.e(logTag, "Unable to publish the ongoing timer notification", e)
            if (!foregroundStarted) {
                cancelTicker()
                stopForegroundAndRemove()
                stopSelf(latestStartId)
                TimerServiceBridge.notifyStateChanged()
            }
            return foregroundStarted
        }
    }

    private fun startTicker() {
        cancelTicker()
        val generation = ++tickerGeneration
        val expectedSessionId = sessionId
        tickerJob = serviceScope.launch {
            while (isActive && generation == tickerGeneration) {
                val prefs = TimerPreferences(applicationContext)
                if (prefs.getActiveSessionId() != expectedSessionId || !prefs.isTimerRunning()) {
                    restoreServiceFromSavedState(prefs)
                    break
                }
                val now = SystemClock.elapsedRealtime()
                val remaining = targetEndRealtime - now

                if (remaining <= 0L) {
                    onTimerFinished()
                    break
                } else {
                    NotificationHelper.updateOngoingNotification(
                        applicationContext,
                        sessionType,
                        remaining,
                        totalDurationMillis,
                        isPaused = false,
                        sessionId = sessionId
                    )
                }

                delay(1000L)
            }
        }
    }

    private fun onTimerFinished() {
        cancelTicker()

        val prefs = TimerPreferences(applicationContext)
        val finishedType = sessionType
        val finishedId = sessionId
        if (prefs.getActiveSessionId() != finishedId || !prefs.isTimerRunning()) {
            restoreServiceFromSavedState(prefs)
            return
        }
        val savedWallDeadline = prefs.getTargetEndWallClockTime()
        if (savedWallDeadline > System.currentTimeMillis()) {
            targetEndRealtime = SystemClock.elapsedRealtime() + (savedWallDeadline - System.currentTimeMillis())
            startTicker()
            return
        }
        val marked = prefs.markSessionCompleted(finishedId, finishedType)
        if (!marked) {
            restoreServiceFromSavedState(prefs)
            return
        }

        // Show completion notification (sound & vibration)
        if (marked) {
            NotificationHelper.showSessionFinishedNotification(applicationContext, finishedType)
        }

        // Mulai otomatis sesi berikutnya jika opsi diaktifkan (aman walau sudah dilakukan
        // oleh layar aplikasi atau alarm: hanya satu yang berhasil)
        SessionAutoAdvance.advanceAfterCompletion(applicationContext, prefs, finishedType, finishedId)

        if (prefs.isTimerRunning() && prefs.getActiveSessionId() != finishedId) {
            // Sesi berikutnya sudah berjalan: layanan ini melanjutkan sebagai sesi baru
            sessionType = prefs.loadSavedSessionType()
            targetEndRealtime = prefs.getTargetEndRealtime()
            totalDurationMillis = prefs.getSavedTotalDurationMillis().takeIf { it > 0L } ?: totalDurationMillis
            sessionId = prefs.getActiveSessionId()
            isPaused = false

            val remaining = (targetEndRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (!startInForeground(remaining, isPaused = false)) return
            startTicker()
            TimerServiceBridge.notifyStateChanged()
            return
        }

        TimerAlarmScheduler(applicationContext).cancelAlarm()
        TimerServiceBridge.notifyStateChanged()

        stopForegroundAndRemove()
        stopSelf(latestStartId)
    }

    private fun restoreServiceFromSavedState(prefs: TimerPreferences) {
        val activeId = prefs.getActiveSessionId()
        if (activeId == 0L) {
            TimerAlarmScheduler(this).cancelAlarm()
            NotificationHelper.cancelOngoingNotification(this)
            stopForegroundAndRemove()
            stopSelf(latestStartId)
            return
        }
        sessionId = activeId
        sessionType = prefs.loadSavedSessionType()
        totalDurationMillis = prefs.getSavedTotalDurationMillis().takeIf { it > 0L } ?: totalDurationMillis
        isPaused = !prefs.isTimerRunning()
        if (isPaused) {
            targetEndRealtime = 0L
            if (!startInForeground(prefs.getSavedRemainingMillis().coerceAtLeast(0L), isPaused = true)) return
        } else {
            val wallRemaining = (prefs.getTargetEndWallClockTime() - System.currentTimeMillis()).coerceAtLeast(0L)
            if (prefs.getTargetEndWallClockTime() <= 0L) {
                val savedRemaining = prefs.getSavedRemainingMillis()
                if (savedRemaining > 0L) {
                    prefs.saveActiveTimerState(sessionType, false, 0L, savedRemaining, totalDurationMillis, activeId)
                    isPaused = true
                    targetEndRealtime = 0L
                    if (!startInForeground(savedRemaining, isPaused = true)) return
                } else {
                    prefs.clearActiveTimerState()
                    NotificationHelper.cancelOngoingNotification(this)
                    stopForegroundAndRemove()
                    stopSelf(latestStartId)
                }
                return
            }
            targetEndRealtime = SystemClock.elapsedRealtime() + wallRemaining
            if (!startInForeground(wallRemaining, isPaused = false)) return
            startTicker()
        }
    }

    private fun cancelTicker() {
        tickerGeneration++
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun stopForegroundAndRemove() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTicker()
        serviceScope.cancel()
    }
}
