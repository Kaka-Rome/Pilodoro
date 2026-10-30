package com.pomodorofocus.app.data

import com.pomodorofocus.app.model.AppFont
import com.pomodorofocus.app.model.AppearanceSettings
import com.pomodorofocus.app.model.ColorPalette
import com.pomodorofocus.app.model.FocusSessionRecord
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.ThemeMode
import com.pomodorofocus.app.model.TimerFont
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds and validates the local JSON backup file (settings + focus history).
 *
 * Fully offline: it only turns data into a JSON string and back. Reading/writing the file
 * itself is done through the system file picker (Storage Access Framework), so no storage
 * permission is needed.
 *
 * Not included on purpose: the wallpaper image (a separate private file) and the live timer
 * state (running/paused session), so restoring never disturbs a timer that is in progress.
 */
object BackupManager {

    const val FORMAT_VERSION = 1
    const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

    private const val APP_ID = "pomodoro-focus"
    private const val KEY_APP = "app"
    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_HISTORY = "history"

    private const val KEY_FOCUS_MINUTES = "focusMinutes"
    private const val KEY_SHORT_BREAK_MINUTES = "shortBreakMinutes"
    private const val KEY_LONG_BREAK_MINUTES = "longBreakMinutes"
    private const val KEY_SESSIONS_BEFORE_LONG_BREAK = "sessionsBeforeLongBreak"
    private const val KEY_AUTO_START_BREAKS = "autoStartBreaks"
    private const val KEY_AUTO_START_FOCUS = "autoStartFocus"
    private const val KEY_KEEP_SCREEN_ON = "keepScreenOn"
    private const val KEY_DAILY_GOAL = "dailyGoal"
    private const val KEY_DND_DURING_FOCUS = "dndDuringFocus"

    private const val KEY_THEME_MODE = "themeMode"
    private const val KEY_COLOR_PALETTE = "colorPalette"
    private const val KEY_APP_FONT = "appFont"
    private const val KEY_BACKGROUND_DIM = "backgroundDimPercent"
    private const val KEY_TIMER_FONT = "timerFont"

    /** Validated contents of a backup file, ready to be applied. */
    data class BackupData(
        val settings: PomodoroSettings,
        val themeMode: ThemeMode,
        val colorPalette: ColorPalette,
        val appFont: AppFont,
        val backgroundDimPercent: Int,
        val timerFont: TimerFont,
        val history: List<FocusSessionRecord>
    )

    enum class FailureReason { NOT_A_BACKUP, UNSUPPORTED_VERSION, CORRUPT }

    sealed class ParseResult {
        data class Success(val data: BackupData) : ParseResult()
        data class Failure(val reason: FailureReason) : ParseResult()
    }

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        return "pomodoro-focus-backup-$day.json"
    }

    fun toJson(
        settings: PomodoroSettings,
        appearance: AppearanceSettings,
        history: List<FocusSessionRecord>,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put(KEY_APP, APP_ID)
        root.put(KEY_VERSION, FORMAT_VERSION)
        root.put(KEY_EXPORTED_AT, exportedAt)

        root.put(
            KEY_SETTINGS,
            JSONObject()
                .put(KEY_FOCUS_MINUTES, settings.focusMinutes)
                .put(KEY_SHORT_BREAK_MINUTES, settings.shortBreakMinutes)
                .put(KEY_LONG_BREAK_MINUTES, settings.longBreakMinutes)
                .put(KEY_SESSIONS_BEFORE_LONG_BREAK, settings.sessionsBeforeLongBreak)
                .put(KEY_AUTO_START_BREAKS, settings.autoStartBreaks)
                .put(KEY_AUTO_START_FOCUS, settings.autoStartFocus)
                .put(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
                .put(KEY_DAILY_GOAL, settings.dailyGoal)
                .put(KEY_DND_DURING_FOCUS, settings.dndDuringFocus)
        )
        root.put(
            KEY_APPEARANCE,
            JSONObject()
                .put(KEY_THEME_MODE, appearance.themeMode.name)
                .put(KEY_COLOR_PALETTE, appearance.colorPalette.name)
                .put(KEY_APP_FONT, appearance.appFont.name)
                .put(KEY_BACKGROUND_DIM, appearance.backgroundDimPercent)
                .put(KEY_TIMER_FONT, appearance.timerFont.name)
        )
        // Reuse the existing record serializer so the history format stays identical to storage.
        root.put(KEY_HISTORY, JSONArray(FocusSessionJsonAdapter.toJson(history)))
        return root.toString(2)
    }

    /**
     * Validates the whole file before anything is applied. Unknown or out-of-range values are
     * clamped to the same limits [TimerPreferences] uses, so a hand-edited file can't corrupt state.
     */
    fun parse(text: String): ParseResult {
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return ParseResult.Failure(FailureReason.NOT_A_BACKUP)
        }
        if (root.optString(KEY_APP) != APP_ID) {
            return ParseResult.Failure(FailureReason.NOT_A_BACKUP)
        }
        val version = root.optInt(KEY_VERSION, -1)
        if (version < 1) return ParseResult.Failure(FailureReason.CORRUPT)
        if (version > FORMAT_VERSION) return ParseResult.Failure(FailureReason.UNSUPPORTED_VERSION)

        val settingsObj = root.optJSONObject(KEY_SETTINGS)
        val appearanceObj = root.optJSONObject(KEY_APPEARANCE)
        val historyArray = root.optJSONArray(KEY_HISTORY)
        if (settingsObj == null || appearanceObj == null || historyArray == null) {
            return ParseResult.Failure(FailureReason.CORRUPT)
        }

        val d = PomodoroSettings()
        val settings = PomodoroSettings(
            focusMinutes = settingsObj.optInt(KEY_FOCUS_MINUTES, d.focusMinutes).coerceIn(1, 180),
            shortBreakMinutes = settingsObj.optInt(KEY_SHORT_BREAK_MINUTES, d.shortBreakMinutes).coerceIn(1, 60),
            longBreakMinutes = settingsObj.optInt(KEY_LONG_BREAK_MINUTES, d.longBreakMinutes).coerceIn(1, 60),
            sessionsBeforeLongBreak = settingsObj.optInt(KEY_SESSIONS_BEFORE_LONG_BREAK, d.sessionsBeforeLongBreak).coerceIn(1, 10),
            autoStartBreaks = settingsObj.optBoolean(KEY_AUTO_START_BREAKS, d.autoStartBreaks),
            autoStartFocus = settingsObj.optBoolean(KEY_AUTO_START_FOCUS, d.autoStartFocus),
            keepScreenOn = settingsObj.optBoolean(KEY_KEEP_SCREEN_ON, d.keepScreenOn),
            dailyGoal = settingsObj.optInt(KEY_DAILY_GOAL, d.dailyGoal).coerceIn(0, 10),
            dndDuringFocus = settingsObj.optBoolean(KEY_DND_DURING_FOCUS, d.dndDuringFocus)
        )

        val da = AppearanceSettings()
        val themeMode = enumOrDefault(appearanceObj.optString(KEY_THEME_MODE), da.themeMode)
        val palette = enumOrDefault(appearanceObj.optString(KEY_COLOR_PALETTE), da.colorPalette)
        val font = enumOrDefault(appearanceObj.optString(KEY_APP_FONT), da.appFont)
        val dim = appearanceObj.optInt(KEY_BACKGROUND_DIM, da.backgroundDimPercent).coerceIn(0, 90)
        // Optional: backups made before this field existed simply fall back to the default.
        val timerFont = enumOrDefault(appearanceObj.optString(KEY_TIMER_FONT), da.timerFont)

        val history = FocusSessionJsonAdapter.fromJson(historyArray.toString())
            .distinctBy { it.id }
            .map { it.copy(durationMillis = it.durationMillis.coerceAtLeast(0L)) }
            .sortedBy { it.timestamp }

        return ParseResult.Success(BackupData(settings, themeMode, palette, font, dim, timerFont, history))
    }

    /**
     * Reads at most [MAX_BACKUP_BYTES] from the stream as UTF-8 text.
     * Returns null if the stream is larger than the limit (so a wrong huge file can't exhaust memory).
     */
    fun readLimited(stream: InputStream): String? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BACKUP_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T {
        return try {
            enumValueOf<T>(name)
        } catch (_: Exception) {
            default
        }
    }
}
