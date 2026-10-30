package com.pomodorofocus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.notification.NotificationHelper
import com.pomodorofocus.app.notification.TimerAlarmScheduler
import android.os.SystemClock

/**
 * BroadcastReceiver invoked when the timer expires while backgrounded or locked.
 * Displays the completion notification and updates the persisted session completion state.
 */
class TimerExpiredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val sessionTypeName = intent?.getStringExtra(TimerAlarmScheduler.EXTRA_SESSION_TYPE)
        val sessionType = try {
            if (sessionTypeName != null) SessionType.valueOf(sessionTypeName) else SessionType.FOCUS
        } catch (_: Exception) {
            SessionType.FOCUS
        }
        val sessionId = intent?.getLongExtra(TimerAlarmScheduler.EXTRA_SESSION_ID, 0L) ?: 0L

        val prefs = TimerPreferences(context)
        // Exact alarms can arrive late, and a cancelled alarm can still be delivered after Next.
        // Only the currently running session whose deadline has elapsed may advance the cycle.
        if (
            sessionId == 0L || sessionId != prefs.getActiveSessionId() ||
            sessionType != prefs.loadSavedSessionType() || !prefs.isTimerRunning()
        ) return
        val wallRemaining = prefs.getTargetEndWallClockTime() - System.currentTimeMillis()
        if (wallRemaining > 0L) {
            TimerAlarmScheduler(context).scheduleAlarm(
                sessionType, SystemClock.elapsedRealtime() + wallRemaining, sessionId
            )
            return
        }
        val remaining = prefs.getTargetEndRealtime() - SystemClock.elapsedRealtime()
        if (remaining > 0L) {
            TimerAlarmScheduler(context).scheduleAlarm(sessionType, prefs.getTargetEndRealtime(), sessionId)
            return
        }
        val markedCompletedNow = prefs.markSessionCompleted(sessionId, sessionType)
        if (!markedCompletedNow) return

        // Only show notification if this completion event has not already been processed
        if (markedCompletedNow) {
            NotificationHelper.showSessionFinishedNotification(context, sessionType)
        }

        // Pindah ke sesi berikutnya; mulai timer hanya bila opsi auto-start diaktifkan.
        val next = com.pomodorofocus.app.service.SessionAutoAdvance.advanceAfterCompletion(
            context, prefs, sessionType, sessionId
        )
        if (next?.isRunning == true) {
            try {
                com.pomodorofocus.app.service.TimerForegroundService.startService(
                    context = context,
                    sessionType = next.sessionType,
                    targetEndRealtime = next.targetEndRealtime,
                    totalDurationMillis = next.totalDurationMillis,
                    sessionId = next.sessionId
                )
            } catch (_: Exception) {
                // Sesi baru tetap tersimpan dan alarmnya terjadwal; layanan akan menyusul saat aplikasi dibuka
            }
        } else if (next != null || !prefs.isTimerRunning()) {
            // Stop foreground service and cancel ongoing timer notification
            com.pomodorofocus.app.service.TimerForegroundService.stopService(context)
        }
        com.pomodorofocus.app.service.TimerServiceBridge.notifyStateChanged()
    }
}
