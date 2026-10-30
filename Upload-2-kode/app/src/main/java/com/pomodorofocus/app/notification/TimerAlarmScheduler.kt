package com.pomodorofocus.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.receiver.TimerExpiredReceiver

/**
 * Schedules alarms using AlarmManager so that the session completion notification
 * reliably fires even when the device is locked, the screen is off, or the app is backgrounded.
 */
class TimerAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    companion object {
        const val ACTION_TIMER_EXPIRED = "com.pomodorofocus.app.ACTION_TIMER_EXPIRED"
        const val EXTRA_SESSION_TYPE = "extra_session_type"
        const val EXTRA_SESSION_ID = "extra_session_id"
        private const val ALARM_REQUEST_CODE = 42
    }

    fun scheduleAlarm(sessionType: SessionType, triggerAtRealtime: Long, sessionId: Long = 0L) {
        if (alarmManager == null) return

        val intent = Intent(context, TimerExpiredReceiver::class.java).apply {
            action = ACTION_TIMER_EXPIRED
            putExtra(EXTRA_SESSION_TYPE, sessionType.name)
            putExtra(EXTRA_SESSION_ID, sessionId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel any previous alarm
        alarmManager.cancel(pendingIntent)

        val triggerTime = triggerAtRealtime.coerceAtLeast(SystemClock.elapsedRealtime() + 100)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // Fallback gracefully to non-exact idle-allowed alarm if exact is denied
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancelAlarm() {
        if (alarmManager == null) return

        val intent = Intent(context, TimerExpiredReceiver::class.java).apply {
            action = ACTION_TIMER_EXPIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
