package com.pomodorofocus.app.data

import android.content.Context
import android.os.SystemClock
import android.content.SharedPreferences
import com.pomodorofocus.app.model.FocusSessionRecord
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.SessionTag
import com.pomodorofocus.app.model.sanitizeSessionTitle
import com.pomodorofocus.app.model.SessionType
import java.util.UUID

/**
 * Local device persistence for Pomodoro settings, session counts, active timer state,
 * and historical completed focus session records.
 * Enables tracking elapsed time reliably even across app pauses or process restarts.
 */
class TimerPreferences(context: Context) {

    private val appContext: Context = context.applicationContext ?: context

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "pomodoro_focus_prefs"
        private const val KEY_FOCUS_MINUTES = "focus_minutes"
        private const val KEY_SHORT_BREAK_MINUTES = "short_break_minutes"
        private const val KEY_LONG_BREAK_MINUTES = "long_break_minutes"
        private const val KEY_SESSIONS_BEFORE_LONG_BREAK = "sessions_before_long_break"
        private const val KEY_AUTO_START_BREAKS = "auto_start_breaks"
        private const val KEY_AUTO_START_FOCUS = "auto_start_focus"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_DND_DURING_FOCUS = "dnd_during_focus"

        private const val KEY_SESSION_TYPE = "session_type"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_TARGET_END_REALTIME = "target_end_realtime"
        private const val KEY_REMAINING_MILLIS = "remaining_millis"
        private const val KEY_TOTAL_DURATION_MILLIS = "total_duration_millis"
        private const val KEY_COMPLETED_SESSIONS = "completed_sessions"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_LAST_COMPLETED_SESSION_ID = "last_completed_session_id"
        private const val KEY_LAST_ALLOCATED_SESSION_ID = "last_allocated_session_id"
        private val SESSION_ID_LOCK = Any()
        // Shared by every TimerPreferences instance in this process. Completion can be reported
        // concurrently by the ViewModel, foreground service, and alarm receiver.
        private val SESSION_STATE_LOCK = Any()
        private const val KEY_TARGET_END_WALL_CLOCK_TIME = "target_end_wall_clock_time"
        private const val KEY_FOCUS_HISTORY = "focus_session_history"
        // Optional title/tag of the Focus session in progress, tied to its session id.
        private const val KEY_META_SESSION_ID = "pending_meta_session_id"
        private const val KEY_META_TITLE = "pending_meta_title"
        private const val KEY_META_TAG = "pending_meta_tag"

        private const val KEY_THEME_MODE = "appearance_theme_mode"
        private const val KEY_COLOR_PALETTE = "appearance_color_palette"
        private const val KEY_FONT_FAMILY = "appearance_font_family"
        private const val KEY_IS_MINIMAL_MODE = "appearance_is_minimal_mode"
        private const val KEY_CUSTOM_BACKGROUND_ENABLED = "appearance_custom_bg_enabled"
        private const val KEY_BACKGROUND_DIM_PERCENT = "appearance_background_dim_percent"
        private const val KEY_TIMER_FONT = "appearance_timer_font"
    }

    fun loadSettings(): PomodoroSettings {
        val focus = prefs.getInt(KEY_FOCUS_MINUTES, 25)
        val shortBreak = prefs.getInt(KEY_SHORT_BREAK_MINUTES, 5)
        val longBreak = prefs.getInt(KEY_LONG_BREAK_MINUTES, 15)
        return PomodoroSettings(
            focusMinutes = focus.coerceIn(1, 180),
            shortBreakMinutes = shortBreak.coerceIn(1, 60),
            longBreakMinutes = longBreak.coerceIn(1, 60),
            sessionsBeforeLongBreak = prefs.getInt(KEY_SESSIONS_BEFORE_LONG_BREAK, 4).coerceIn(1, 10),
            autoStartBreaks = prefs.getBoolean(KEY_AUTO_START_BREAKS, false),
            autoStartFocus = prefs.getBoolean(KEY_AUTO_START_FOCUS, false),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            dailyGoal = prefs.getInt(KEY_DAILY_GOAL, 4).coerceIn(0, 10),
            dndDuringFocus = prefs.getBoolean(KEY_DND_DURING_FOCUS, false)
        )
    }

    fun saveSettings(settings: PomodoroSettings) {
        prefs.edit()
            .putInt(KEY_FOCUS_MINUTES, settings.focusMinutes)
            .putInt(KEY_SHORT_BREAK_MINUTES, settings.shortBreakMinutes)
            .putInt(KEY_LONG_BREAK_MINUTES, settings.longBreakMinutes)
            .putInt(KEY_SESSIONS_BEFORE_LONG_BREAK, settings.sessionsBeforeLongBreak.coerceIn(1, 10))
            .putBoolean(KEY_AUTO_START_BREAKS, settings.autoStartBreaks)
            .putBoolean(KEY_AUTO_START_FOCUS, settings.autoStartFocus)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putInt(KEY_DAILY_GOAL, settings.dailyGoal.coerceIn(0, 10))
            .putBoolean(KEY_DND_DURING_FOCUS, settings.dndDuringFocus)
            .apply()
        // Turning the option on/off while a Focus session is running should take effect right away.
        FocusDndController.sync(appContext, this)
    }

    fun loadAppearanceSettings(): com.pomodorofocus.app.model.AppearanceSettings {
        val themeName = prefs.getString(KEY_THEME_MODE, com.pomodorofocus.app.model.ThemeMode.SYSTEM.name)
        val paletteName = prefs.getString(KEY_COLOR_PALETTE, com.pomodorofocus.app.model.ColorPalette.TOMATO.name)
        val fontName = prefs.getString(KEY_FONT_FAMILY, com.pomodorofocus.app.model.AppFont.SANS_SERIF.name)
        val isMinimal = prefs.getBoolean(KEY_IS_MINIMAL_MODE, false)
        val hasCustomBg = prefs.getBoolean(KEY_CUSTOM_BACKGROUND_ENABLED, false)
        val dimPercent = prefs.getInt(KEY_BACKGROUND_DIM_PERCENT, 40)
        val timerFontName = prefs.getString(KEY_TIMER_FONT, com.pomodorofocus.app.model.TimerFont.GEOMETRIC_BOLD.name)

        val themeMode = try {
            com.pomodorofocus.app.model.ThemeMode.valueOf(themeName ?: com.pomodorofocus.app.model.ThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            com.pomodorofocus.app.model.ThemeMode.SYSTEM
        }

        val palette = try {
            com.pomodorofocus.app.model.ColorPalette.valueOf(paletteName ?: com.pomodorofocus.app.model.ColorPalette.TOMATO.name)
        } catch (_: Exception) {
            com.pomodorofocus.app.model.ColorPalette.TOMATO
        }

        val font = try {
            com.pomodorofocus.app.model.AppFont.valueOf(fontName ?: com.pomodorofocus.app.model.AppFont.SANS_SERIF.name)
        } catch (_: Exception) {
            com.pomodorofocus.app.model.AppFont.SANS_SERIF
        }

        val timerFont = try {
            com.pomodorofocus.app.model.TimerFont.valueOf(timerFontName ?: com.pomodorofocus.app.model.TimerFont.GEOMETRIC_BOLD.name)
        } catch (_: Exception) {
            com.pomodorofocus.app.model.TimerFont.GEOMETRIC_BOLD
        }

        return com.pomodorofocus.app.model.AppearanceSettings(
            themeMode = themeMode,
            colorPalette = palette,
            appFont = font,
            isMinimalMode = isMinimal,
            hasCustomBackground = hasCustomBg,
            backgroundDimPercent = dimPercent.coerceIn(0, 90),
            timerFont = timerFont
        )
    }

    fun saveAppearanceSettings(settings: com.pomodorofocus.app.model.AppearanceSettings) {
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_COLOR_PALETTE, settings.colorPalette.name)
            .putString(KEY_FONT_FAMILY, settings.appFont.name)
            .putBoolean(KEY_IS_MINIMAL_MODE, settings.isMinimalMode)
            .putBoolean(KEY_CUSTOM_BACKGROUND_ENABLED, settings.hasCustomBackground)
            .putInt(KEY_BACKGROUND_DIM_PERCENT, settings.backgroundDimPercent)
            .putString(KEY_TIMER_FONT, settings.timerFont.name)
            .apply()
    }

    fun setMinimalMode(isMinimal: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_MINIMAL_MODE, isMinimal)
            .apply()
    }

    /** Stores the next session as ready but not running after a session completes. */
    fun saveNextSessionAsIdle(sessionType: SessionType, durationMillis: Long) {
        prefs.edit()
            .putString(KEY_SESSION_TYPE, sessionType.name)
            .putBoolean(KEY_IS_RUNNING, false)
            .putLong(KEY_TARGET_END_REALTIME, 0L)
            .putLong(KEY_TARGET_END_WALL_CLOCK_TIME, 0L)
            .putLong(KEY_REMAINING_MILLIS, 0L)
            .putLong(KEY_TOTAL_DURATION_MILLIS, durationMillis)
            .putLong(KEY_ACTIVE_SESSION_ID, 0L)
            .apply()
        FocusDndController.sync(appContext, this)
    }

    fun setCustomBackgroundEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CUSTOM_BACKGROUND_ENABLED, enabled)
            .apply()
    }

    fun setBackgroundDimPercent(percent: Int) {
        prefs.edit()
            .putInt(KEY_BACKGROUND_DIM_PERCENT, percent.coerceIn(0, 90))
            .apply()
    }

    fun saveActiveTimerState(
        sessionType: SessionType,
        isRunning: Boolean,
        targetEndRealtime: Long,
        remainingMillis: Long,
        totalDurationMillis: Long,
        sessionId: Long = 0L
    ) {
        val editor = prefs.edit()
            .putString(KEY_SESSION_TYPE, sessionType.name)
            .putBoolean(KEY_IS_RUNNING, isRunning)
            .putLong(KEY_TARGET_END_REALTIME, targetEndRealtime)
            .putLong(KEY_REMAINING_MILLIS, remainingMillis)
            .putLong(KEY_TOTAL_DURATION_MILLIS, totalDurationMillis)
            .putLong(
                KEY_TARGET_END_WALL_CLOCK_TIME,
                if (isRunning && remainingMillis > 0L) System.currentTimeMillis() + remainingMillis else 0L
            )

        if (sessionId != 0L) {
            editor.putLong(KEY_ACTIVE_SESSION_ID, sessionId)
        }
        editor.apply()
        // Keep Do Not Disturb in step with the timer (on while Focus runs, restored on pause/finish/reset).
        FocusDndController.sync(appContext, this)
    }

    fun getTargetEndWallClockTime(): Long {
        return prefs.getLong(KEY_TARGET_END_WALL_CLOCK_TIME, 0L)
    }

    fun getActiveSessionId(): Long {
        return prefs.getLong(KEY_ACTIVE_SESSION_ID, 0L)
    }

    fun getLastCompletedSessionId(): Long {
        return prefs.getLong(KEY_LAST_COMPLETED_SESSION_ID, 0L)
    }

    /** Process safe, strictly increasing identity for timer generations, even on rapid taps. */
    fun allocateSessionId(): Long = synchronized(SESSION_ID_LOCK) {
        val next = maxOf(SystemClock.elapsedRealtime(), prefs.getLong(KEY_LAST_ALLOCATED_SESSION_ID, 0L) + 1L)
        prefs.edit().putLong(KEY_LAST_ALLOCATED_SESSION_ID, next).commit()
        next
    }

    /**
     * Atomically marks a session as completed.
     * Increments completed sessions count ONLY if sessionType is FOCUS.
     * Appends a FocusSessionRecord history entry ONLY if sessionType is FOCUS.
     * Prevents double-counting and duplicate notifications by checking sessionId against lastCompletedSessionId.
     *
     * @return true if marked completed for the first time, false if already completed or invalid.
     */
    fun markSessionCompleted(
        sessionId: Long,
        sessionType: SessionType,
        durationMillis: Long? = null
    ): Boolean = synchronized(SESSION_STATE_LOCK) {
        val activeId = prefs.getLong(KEY_ACTIVE_SESSION_ID, 0L)
        val lastCompletedId = prefs.getLong(KEY_LAST_COMPLETED_SESSION_ID, 0L)
        val targetSessionId = if (sessionId != 0L) sessionId else activeId

        // If no active session or this session was already marked completed, avoid double counting
        // A stale service/alarm callback must never complete the session that replaced it.
        if (targetSessionId == 0L || targetSessionId != activeId || targetSessionId == lastCompletedId) {
            return@synchronized false
        }
        if (prefs.getString(KEY_SESSION_TYPE, SessionType.FOCUS.name) != sessionType.name) return@synchronized false

        val currentCompleted = prefs.getInt(KEY_COMPLETED_SESSIONS, 0)
        val newCompleted = if (sessionType == SessionType.FOCUS) {
            currentCompleted + 1
        } else {
            currentCompleted
        }

        val editor = prefs.edit()
            .putBoolean(KEY_IS_RUNNING, false)
            .putLong(KEY_TARGET_END_REALTIME, 0L)
            .putLong(KEY_TARGET_END_WALL_CLOCK_TIME, 0L)
            .putLong(KEY_REMAINING_MILLIS, 0L)
            .putLong(KEY_LAST_COMPLETED_SESSION_ID, targetSessionId)
            .putInt(KEY_COMPLETED_SESSIONS, newCompleted)

        // Only record completed history for genuine FOCUS sessions
        if (sessionType == SessionType.FOCUS) {
            val actualDuration = durationMillis
                ?: prefs.getLong(KEY_TOTAL_DURATION_MILLIS, 0L).takeIf { it > 0L }
                ?: (prefs.getInt(KEY_FOCUS_MINUTES, 25) * 60 * 1000L)

            // Attach the optional title/tag, but only if they belong to exactly this session.
            val meta = loadSessionMeta(targetSessionId)
            val currentHistory = loadFocusHistory().toMutableList()
            currentHistory.add(
                FocusSessionRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    durationMillis = actualDuration,
                    title = meta?.first ?: "",
                    tag = meta?.second
                )
            )
            editor.putString(KEY_FOCUS_HISTORY, FocusSessionJsonAdapter.toJson(currentHistory))
        }

        // The title/tag was consumed by (or never belonged to) this session; don't keep it around.
        if (prefs.getLong(KEY_META_SESSION_ID, 0L) == targetSessionId) {
            editor.remove(KEY_META_SESSION_ID).remove(KEY_META_TITLE).remove(KEY_META_TAG)
        }

        editor.apply()
        // Restore Do Not Disturb before any "session finished" notification is posted by the caller.
        FocusDndController.sync(appContext, this)
        true
    }

    /**
     * Saves the optional title/tag for the Focus session [sessionId] that is currently active.
     * Ignored when that session is no longer the active one or has already been completed, so a
     * late save can never attach itself to the wrong session. Blank title + no tag clears it.
     * Synchronized with [markSessionCompleted].
     *
     * @return true if stored (or cleared), false if ignored.
     */
    fun saveSessionMeta(sessionId: Long, title: String, tag: SessionTag?): Boolean = synchronized(SESSION_STATE_LOCK) {
        if (sessionId == 0L) return@synchronized false
        if (sessionId != prefs.getLong(KEY_ACTIVE_SESSION_ID, 0L)) return@synchronized false
        if (sessionId == prefs.getLong(KEY_LAST_COMPLETED_SESSION_ID, 0L)) return@synchronized false
        val clean = sanitizeSessionTitle(title)
        val editor = prefs.edit()
        if (clean.isEmpty() && tag == null) {
            editor.remove(KEY_META_SESSION_ID).remove(KEY_META_TITLE).remove(KEY_META_TAG)
        } else {
            editor.putLong(KEY_META_SESSION_ID, sessionId)
                .putString(KEY_META_TITLE, clean)
            if (tag != null) editor.putString(KEY_META_TAG, tag.name) else editor.remove(KEY_META_TAG)
        }
        editor.apply()
        true
    }

    /** Title/tag saved for [sessionId], or null if none was set for exactly that session. */
    fun loadSessionMeta(sessionId: Long): Pair<String, SessionTag?>? {
        if (sessionId == 0L || prefs.getLong(KEY_META_SESSION_ID, 0L) != sessionId) return null
        val title = sanitizeSessionTitle(prefs.getString(KEY_META_TITLE, "") ?: "")
        val tag = SessionTag.fromName(prefs.getString(KEY_META_TAG, null))
        return if (title.isEmpty() && tag == null) null else title to tag
    }

    fun loadFocusHistory(): List<FocusSessionRecord> {
        val json = prefs.getString(KEY_FOCUS_HISTORY, null)
        return FocusSessionJsonAdapter.fromJson(json)
    }

    fun addFocusSessionRecord(record: FocusSessionRecord) {
        val history = loadFocusHistory().toMutableList()
        history.add(record)
        prefs.edit().putString(KEY_FOCUS_HISTORY, FocusSessionJsonAdapter.toJson(history)).apply()
    }

    fun clearFocusHistory() {
        prefs.edit().remove(KEY_FOCUS_HISTORY).apply()
    }

    /**
     * Replaces the whole focus history (used when restoring a backup).
     * Synchronized with [markSessionCompleted] so a finishing session can't interleave with the swap.
     */
    fun replaceFocusHistory(records: List<FocusSessionRecord>) {
        synchronized(SESSION_STATE_LOCK) {
            prefs.edit()
                .putString(KEY_FOCUS_HISTORY, FocusSessionJsonAdapter.toJson(records))
                .apply()
        }
    }

    /**
     * Wipes all focus history and the completed-focus counter (which drives the Long Break cycle),
     * in one atomic edit. Settings, appearance, wallpaper and any active timer are left untouched.
     */
    fun resetFocusStatistics() {
        synchronized(SESSION_STATE_LOCK) {
            prefs.edit()
                .remove(KEY_FOCUS_HISTORY)
                .putInt(KEY_COMPLETED_SESSIONS, 0)
                .apply()
        }
    }

    fun loadSavedSessionType(): SessionType {
        val name = prefs.getString(KEY_SESSION_TYPE, SessionType.FOCUS.name)
        return try {
            SessionType.valueOf(name ?: SessionType.FOCUS.name)
        } catch (_: Exception) {
            SessionType.FOCUS
        }
    }

    fun loadCompletedSessions(): Int {
        return prefs.getInt(KEY_COMPLETED_SESSIONS, 0)
    }

    fun isTimerRunning(): Boolean {
        return prefs.getBoolean(KEY_IS_RUNNING, false)
    }

    fun getTargetEndRealtime(): Long {
        return prefs.getLong(KEY_TARGET_END_REALTIME, 0L)
    }

    fun getSavedRemainingMillis(): Long {
        return prefs.getLong(KEY_REMAINING_MILLIS, -1L)
    }

    fun getSavedTotalDurationMillis(): Long {
        return prefs.getLong(KEY_TOTAL_DURATION_MILLIS, -1L)
    }

    fun clearActiveTimerState() {
        prefs.edit()
            .putBoolean(KEY_IS_RUNNING, false)
            .putLong(KEY_TARGET_END_REALTIME, 0L)
            .putLong(KEY_TARGET_END_WALL_CLOCK_TIME, 0L)
            .putLong(KEY_REMAINING_MILLIS, 0L)
            .putLong(KEY_ACTIVE_SESSION_ID, 0L)
            .apply()
        FocusDndController.sync(appContext, this)
    }
}
