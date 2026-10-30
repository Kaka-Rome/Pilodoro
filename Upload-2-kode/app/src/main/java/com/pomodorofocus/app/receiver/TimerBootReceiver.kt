package com.pomodorofocus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.notification.TimerAlarmScheduler
import com.pomodorofocus.app.service.TimerForegroundService

/** Restores a running Pomodoro alarm after Android has finished booting. */
class TimerBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = TimerPreferences(context)
        val sessionId = prefs.getActiveSessionId()
        if (sessionId == 0L || !prefs.isTimerRunning()) return

        val sessionType = prefs.loadSavedSessionType()
        val totalDuration = prefs.getSavedTotalDurationMillis().takeIf { it > 0L }
            ?: prefs.loadSettings().getDurationMillis(sessionType)
        val nowWall = System.currentTimeMillis()
        val nowRealtime = SystemClock.elapsedRealtime()
        val wallDeadline = prefs.getTargetEndWallClockTime()

        val remaining = if (wallDeadline > 0L) {
            wallDeadline - nowWall
        } else {
            // Recover older saved state that predates the durable wall-clock deadline.
            prefs.getSavedRemainingMillis().coerceAtLeast(0L)
        }

        if (remaining <= 0L) {
            // Preserve the expired running session so the normal expiry receiver completes it.
            prefs.saveActiveTimerState(
                sessionType = sessionType,
                isRunning = true,
                targetEndRealtime = nowRealtime,
                remainingMillis = 0L,
                totalDurationMillis = totalDuration,
                sessionId = sessionId
            )
            TimerAlarmScheduler(context).scheduleAlarm(sessionType, nowRealtime + 100L, sessionId)
            return
        }

        val targetRealtime = nowRealtime + remaining
        // elapsedRealtime resets at boot; refresh its deadline from the durable wall deadline.
        prefs.saveActiveTimerState(
            sessionType = sessionType,
            isRunning = true,
            targetEndRealtime = targetRealtime,
            remainingMillis = remaining,
            totalDurationMillis = totalDuration,
            sessionId = sessionId
        )
        TimerAlarmScheduler(context).scheduleAlarm(sessionType, targetRealtime, sessionId)
        TimerForegroundService.startService(
            context = context,
            sessionType = sessionType,
            targetEndRealtime = targetRealtime,
            totalDurationMillis = totalDuration,
            sessionId = sessionId
        )
    }
}
