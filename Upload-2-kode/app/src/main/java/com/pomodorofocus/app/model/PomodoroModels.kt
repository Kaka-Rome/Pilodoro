package com.pomodorofocus.app.model

import androidx.annotation.StringRes
import com.pomodorofocus.app.R

/**
 * The types of Pomodoro sessions.
 */
enum class SessionType(@param:StringRes val labelRes: Int) {
    FOCUS(R.string.session_focus),
    SHORT_BREAK(R.string.session_short_break),
    LONG_BREAK(R.string.session_long_break);

    fun getDurationMinutes(settings: PomodoroSettings): Int {
        return when (this) {
            FOCUS -> settings.focusMinutes
            SHORT_BREAK -> settings.shortBreakMinutes
            LONG_BREAK -> settings.longBreakMinutes
        }
    }

    fun nextSession(completedFocusCount: Int, sessionsBeforeLongBreak: Int = 4): SessionType {
        val interval = sessionsBeforeLongBreak.coerceAtLeast(1)
        return when (this) {
            FOCUS -> {
                if (completedFocusCount > 0 && completedFocusCount % interval == 0) {
                    LONG_BREAK
                } else {
                    SHORT_BREAK
                }
            }
            SHORT_BREAK, LONG_BREAK -> FOCUS
        }
    }

    /**
     * The session shown as "Next" under the timer controls, and the one the Next button jumps to.
     * A Focus session in progress counts as one more completed Focus when picking short vs long break.
     */
    fun upcoming(completedSessions: Int, sessionsBeforeLongBreak: Int = 4): SessionType =
        if (this == FOCUS) nextSession(completedSessions + 1, sessionsBeforeLongBreak) else FOCUS
}

/**
 * Current state of the Pomodoro timer.
 */
enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

/**
 * Top-level application destinations.
 */
enum class AppScreen {
    TIMER,
    STATISTICS
}

/**
 * Periods for aggregating statistics.
 */
enum class StatsPeriod(@param:StringRes val labelRes: Int) {
    DAILY(R.string.stats_period_daily),
    WEEKLY(R.string.stats_period_weekly),
    MONTHLY(R.string.stats_period_monthly)
}

/**
 * Fixed preset categories a Focus session can be tagged with (no custom tags).
 * Only the enum name is stored; the label comes from string resources.
 */
enum class SessionTag(@param:StringRes val labelRes: Int) {
    WORK(R.string.tag_work),
    CODING(R.string.tag_coding),
    CREATIVE(R.string.tag_creative),
    READING(R.string.tag_reading),
    FITNESS(R.string.tag_fitness),
    STUDY(R.string.tag_study),
    PERSONAL(R.string.tag_personal);

    companion object {
        fun fromName(name: String?): SessionTag? =
            entries.firstOrNull { it.name == name }
    }
}

/** Max characters for an optional Focus session title. */
const val SESSION_TITLE_MAX_LENGTH = 60

/** Trims, collapses line breaks / repeated spaces, and limits length. Blank means "no title". */
fun sanitizeSessionTitle(raw: String): String =
    raw.replace(Regex("\\s+"), " ").trim().take(SESSION_TITLE_MAX_LENGTH).trim()

/**
 * A persistent record of a completed focus session.
 * [title] and [tag] are optional; older records simply have neither.
 */
data class FocusSessionRecord(
    val id: String,
    val timestamp: Long,
    val durationMillis: Long,
    val title: String = "",
    val tag: SessionTag? = null
)

/**
 * An item representing a bar on the focus statistics chart.
 */
data class ChartBarItem(
    val label: String,
    val fullDateLabel: String,
    val durationMinutes: Long,
    val sessionCount: Int,
    val isHighlighted: Boolean = false
)

/**
 * Aggregated statistics data for a selected period.
 */
data class StatsPeriodData(
    val period: StatsPeriod,
    val title: String,
    val completedCount: Int,
    val totalDurationMillis: Long,
    val chartItems: List<ChartBarItem>,
    val canGoForward: Boolean,
    val sessions: List<FocusSessionRecord> = emptyList()
)

/**
 * User-configurable session durations in minutes.
 */
data class PomodoroSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    /** Jumlah sesi Focus selesai sebelum Long Break (bawaan 4). */
    val sessionsBeforeLongBreak: Int = 4,
    /** Mulai Short/Long Break otomatis setelah Focus selesai. */
    val autoStartBreaks: Boolean = false,
    /** Mulai Focus otomatis setelah Break selesai. */
    val autoStartFocus: Boolean = false,
    /** Jaga layar tetap menyala selama timer berjalan. */
    val keepScreenOn: Boolean = true,
    /** Target sesi Focus per hari; 0 = target dimatikan. */
    val dailyGoal: Int = 4,
    /** Aktifkan Do Not Disturb selama sesi Focus berjalan (perlu izin akses DND dari sistem). */
    val dndDuringFocus: Boolean = false
) {
    fun getDurationMillis(type: SessionType): Long {
        return type.getDurationMinutes(this) * 60 * 1000L
    }
}

/**
 * Formats a duration in milliseconds into a concise readable string (e.g., "1h 25m", "45m", "0m").
 */
fun formatDurationHoursMinutes(durationMillis: Long): String {
    val totalMinutes = (durationMillis + 59999L) / 60000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "0m"
    }
}

/**
 * UI state for the Pomodoro application.
 */
data class PomodoroUiState(
    val currentScreen: AppScreen = AppScreen.TIMER,
    val sessionType: SessionType = SessionType.FOCUS,
    val timerState: TimerState = TimerState.IDLE,
    val remainingMillis: Long = 25 * 60 * 1000L,
    val totalDurationMillis: Long = 25 * 60 * 1000L,
    val completedSessions: Int = 0,
    val settings: PomodoroSettings = PomodoroSettings(),
    val isSettingsOpen: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val showPermissionRationale: Boolean = false,
    val statsPeriod: StatsPeriod = StatsPeriod.DAILY,
    val statsPeriodData: StatsPeriodData? = null,
    val appearanceSettings: AppearanceSettings = AppearanceSettings(),
    val customBackgroundBitmap: android.graphics.Bitmap? = null,
    val backgroundErrorMessage: String? = null,
    val todayFocusSessions: Int = 0,
    /** Apakah aplikasi sudah diberi izin akses Do Not Disturb oleh pengguna. */
    val isDndAccessGranted: Boolean = true,
    /** Optional title of the Focus session in progress ("" = none). */
    val sessionTitle: String = "",
    /** Optional preset tag of the Focus session in progress. */
    val sessionTag: SessionTag? = null
) {
    val progress: Float
        get() {
            if (totalDurationMillis <= 0L) return 0f
            val elapsed = (totalDurationMillis - remainingMillis).coerceAtLeast(0L)
            return (elapsed.toFloat() / totalDurationMillis.toFloat()).coerceIn(0f, 1f)
        }

    val formattedTime: String
        get() {
            val totalSeconds = (remainingMillis + 999L) / 1000L
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
