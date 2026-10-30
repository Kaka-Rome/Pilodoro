package com.pomodorofocus.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pomodorofocus.app.MainActivity
import com.pomodorofocus.app.R
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.service.TimerForegroundService
import java.util.Locale

/**
 * Handles creation of notification channels and building ongoing and completion notifications.
 */
object NotificationHelper {

    const val CHANNEL_ID = "pomodoro_alerts_channel"
    const val ALERTS_CHANNEL_ID = "pomodoro_alerts_channel"
    const val RUNNING_CHANNEL_ID = "pomodoro_running_channel"

    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_ID_COMPLETION = 1001
    const val NOTIFICATION_ID_ONGOING = 2001

    fun createNotificationChannel(context: Context) {
        createNotificationChannels(context)
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Completion alerts channel (High importance, sound, vibration)
            val alertsName = context.getString(R.string.notification_channel_name)
            val alertsDesc = context.getString(R.string.notification_channel_description)
            val alertsChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                alertsName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = alertsDesc
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertsChannel)

            // 2. Ongoing timer channel (Low importance for silent updates in status bar/shade)
            val runningName = context.getString(R.string.notification_running_channel_name)
            val runningDesc = context.getString(R.string.notification_running_channel_description)
            val runningChannel = NotificationChannel(
                RUNNING_CHANNEL_ID,
                runningName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = runningDesc
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(runningChannel)
        }
    }

    /**
     * Builds the persistent notification for the active foreground service.
     */
    fun buildOngoingNotification(
        context: Context,
        sessionType: SessionType,
        remainingMillis: Long,
        totalDurationMillis: Long,
        isPaused: Boolean,
        sessionId: Long = 0L
    ): Notification {
        createNotificationChannels(context)

        val modeTitle = when (sessionType) {
            SessionType.FOCUS -> context.getString(R.string.notification_ongoing_focus_title)
            SessionType.SHORT_BREAK -> context.getString(R.string.notification_ongoing_short_break_title)
            SessionType.LONG_BREAK -> context.getString(R.string.notification_ongoing_long_break_title)
        }

        val minutes = (remainingMillis / 1000) / 60
        val seconds = (remainingMillis / 1000) % 60
        val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

        val contentText = if (isPaused) {
            context.getString(R.string.notification_ongoing_paused, modeTitle, timeFormatted)
        } else {
            context.getString(R.string.notification_ongoing_time_remaining, timeFormatted)
        }

        // Tap notification to open MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Pause / Resume
        val toggleAction = if (isPaused) TimerForegroundService.ACTION_RESUME else TimerForegroundService.ACTION_PAUSE
        val toggleIntent = Intent(context, TimerForegroundService::class.java).apply {
            action = toggleAction
            putExtra(TimerForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        val togglePendingIntent = PendingIntent.getService(
            context,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleActionLabel = if (isPaused) {
            context.getString(R.string.notification_action_resume)
        } else {
            context.getString(R.string.notification_action_pause)
        }

        // Action: Reset
        val resetIntent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_RESET
            putExtra(TimerForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        val resetPendingIntent = PendingIntent.getService(
            context,
            2,
            resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val totalSec = (totalDurationMillis / 1000).toInt().coerceAtLeast(1)
        val elapsedSec = ((totalDurationMillis - remainingMillis) / 1000).toInt().coerceIn(0, totalSec)

        val builder = NotificationCompat.Builder(context, RUNNING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (isPaused) "$modeTitle (Paused)" else modeTitle)
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            // Keep controls available while paused; users can still resume from the notification.
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(totalSec, elapsedSec, false)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                toggleActionLabel,
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notification_action_reset),
                resetPendingIntent
            )

        val nextIntent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_NEXT
            putExtra(TimerForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        val nextPendingIntent = PendingIntent.getService(
            context, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_media_next,
            context.getString(R.string.action_skip),
            nextPendingIntent
        )
        if (sessionType == SessionType.FOCUS) {
            val endIntent = Intent(context, TimerForegroundService::class.java).apply {
                action = TimerForegroundService.ACTION_END_SESSION
                putExtra(TimerForegroundService.EXTRA_SESSION_ID, sessionId)
            }
            val endPendingIntent = PendingIntent.getService(
                context, 4, endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.action_end_session),
                endPendingIntent
            )
        }

        return builder.build()
    }

    fun updateOngoingNotification(
        context: Context,
        sessionType: SessionType,
        remainingMillis: Long,
        totalDurationMillis: Long,
        isPaused: Boolean,
        sessionId: Long = 0L
    ) {
        if (!hasNotificationPermission(context)) return

        try {
            val notification = buildOngoingNotification(
                context,
                sessionType,
                remainingMillis,
                totalDurationMillis,
                isPaused,
                sessionId
            )
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID_ONGOING, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelOngoingNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID_ONGOING)
        } catch (_: SecurityException) {
        }
    }

    fun showSessionFinishedNotification(context: Context, sessionType: SessionType) {
        createNotificationChannels(context)

        // Cancel the ongoing timer notification when the session finishes
        cancelOngoingNotification(context)

        if (!hasNotificationPermission(context)) return

        val title = if (sessionType == SessionType.FOCUS) {
            context.getString(R.string.notification_focus_finished_title)
        } else {
            context.getString(R.string.notification_break_finished_title)
        }

        val body = if (sessionType == SessionType.FOCUS) {
            context.getString(R.string.notification_focus_finished_body)
        } else {
            context.getString(R.string.notification_break_finished_body)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID_COMPLETION, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID_COMPLETION)
        } catch (_: SecurityException) {
        }
    }

    fun cancelAll(context: Context) {
        cancelOngoingNotification(context)
        cancelNotification(context)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            if (manager.getNotificationChannel(RUNNING_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }
}
