package com.pomodorofocus.app.service

import android.content.Context
import android.os.SystemClock
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.notification.TimerAlarmScheduler

/** Data sesi berikutnya yang dimulai otomatis. */
data class NextSession(
    val sessionType: SessionType,
    val targetEndRealtime: Long,
    val totalDurationMillis: Long,
    val sessionId: Long,
    val isRunning: Boolean = true
)

/**
 * Memulai sesi berikutnya secara otomatis (opsional) setelah sebuah sesi selesai.
 *
 * Sesi bisa selesai dari beberapa tempat sekaligus (layar aplikasi, layanan latar belakang,
 * alarm). Fungsi ini aman dipanggil dari semuanya: hanya satu pemanggil yang berhasil
 * memulai sesi berikutnya, yang lain mendapat null. Seluruh keadaan sesi baru disimpan di
 * penyimpanan lokal, sehingga pemanggil mana pun (atau layar aplikasi saat dibuka lagi)
 * dapat membacanya.
 */
object SessionAutoAdvance {

    private val lock = Any()

    /**
     * Moves to the next session after completion. Auto-start preferences decide only whether
     * that session starts immediately; when disabled, the next timer is saved as idle.
     */
    fun advanceAfterCompletion(
        context: Context,
        prefs: TimerPreferences,
        completedType: SessionType,
        completedSessionId: Long
    ): NextSession? = advance(context, prefs, completedType, completedSessionId, prepareIdle = true)

    /** Backward-compatible entry point that advances only when auto-start is enabled. */
    fun advanceIfEnabled(
        context: Context,
        prefs: TimerPreferences,
        completedType: SessionType,
        completedSessionId: Long
    ): NextSession? = advance(context, prefs, completedType, completedSessionId, prepareIdle = false)

    private fun advance(
        context: Context,
        prefs: TimerPreferences,
        completedType: SessionType,
        completedSessionId: Long,
        prepareIdle: Boolean
    ): NextSession? = synchronized(lock) {
        val settings = prefs.loadSettings()

        val activeId = prefs.getActiveSessionId()
        val finishedId = if (completedSessionId != 0L) completedSessionId else activeId
        // Sudah dilanjutkan, di-reset, atau belum ditandai selesai: jangan lakukan apa pun
        if (finishedId == 0L || activeId != finishedId) return null
        if (prefs.getLastCompletedSessionId() != finishedId) return null
        if (prefs.isTimerRunning()) return null

        val nextType = completedType.nextSession(
            prefs.loadCompletedSessions(),
            settings.sessionsBeforeLongBreak
        )
        val allowed = if (nextType == SessionType.FOCUS) settings.autoStartFocus else settings.autoStartBreaks
        val duration = settings.getDurationMillis(nextType)
        if (!allowed) {
            if (!prepareIdle) return null
            prefs.saveNextSessionAsIdle(nextType, duration)
            return NextSession(
                sessionType = nextType,
                targetEndRealtime = 0L,
                totalDurationMillis = duration,
                sessionId = 0L,
                isRunning = false
            )
        }

        val now = SystemClock.elapsedRealtime()
        val newId = prefs.allocateSessionId()
        val targetEnd = now + duration

        prefs.saveActiveTimerState(
            sessionType = nextType,
            isRunning = true,
            targetEndRealtime = targetEnd,
            remainingMillis = duration,
            totalDurationMillis = duration,
            sessionId = newId
        )
        // Alarm lama otomatis tergantikan oleh alarm sesi baru
        TimerAlarmScheduler(context).scheduleAlarm(nextType, targetEnd, newId)

        NextSession(nextType, targetEnd, duration, newId, isRunning = true)
    }
}
