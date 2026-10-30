package com.pomodorofocus.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pomodorofocus.app.data.BackupManager
import com.pomodorofocus.app.data.BackupManager.ParseResult
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.AppFont
import com.pomodorofocus.app.model.AppearanceSettings
import com.pomodorofocus.app.model.ColorPalette
import com.pomodorofocus.app.model.FocusSessionRecord
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.ThemeMode
import com.pomodorofocus.app.model.TimerFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataManagementTest {

    private lateinit var prefs: TimerPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("pomodoro_focus_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = TimerPreferences(context)
    }

    @Test
    fun backupRoundTripPreservesSettingsAppearanceAndHistory() {
        val settings = PomodoroSettings(50, 10, 20, 3, true, true, false, 6, true)
        val appearance = AppearanceSettings(ThemeMode.DARK, ColorPalette.VIOLET, AppFont.SERIF, false, false, 55, TimerFont.MONO_BOLD)
        val history = listOf(
            FocusSessionRecord("b", 2000L, 1_500_000L),
            FocusSessionRecord("a", 1000L, 1_500_000L)
        )

        val result = BackupManager.parse(BackupManager.toJson(settings, appearance, history))

        assertTrue(result is ParseResult.Success)
        val data = (result as ParseResult.Success).data
        assertEquals(settings, data.settings)
        assertEquals(ThemeMode.DARK, data.themeMode)
        assertEquals(ColorPalette.VIOLET, data.colorPalette)
        assertEquals(AppFont.SERIF, data.appFont)
        assertEquals(55, data.backgroundDimPercent)
        assertEquals(TimerFont.MONO_BOLD, data.timerFont)
        assertEquals(listOf("a", "b"), data.history.map { it.id })
    }

    @Test
    fun invalidFilesAreRejected() {
        assertEquals(
            BackupManager.FailureReason.NOT_A_BACKUP,
            (BackupManager.parse("not json") as ParseResult.Failure).reason
        )
        assertEquals(
            BackupManager.FailureReason.NOT_A_BACKUP,
            (BackupManager.parse("{\"foo\":1}") as ParseResult.Failure).reason
        )
        assertEquals(
            BackupManager.FailureReason.UNSUPPORTED_VERSION,
            (BackupManager.parse(
                "{\"app\":\"pomodoro-focus\",\"version\":99,\"settings\":{},\"appearance\":{},\"history\":[]}"
            ) as ParseResult.Failure).reason
        )
        assertEquals(
            BackupManager.FailureReason.CORRUPT,
            (BackupManager.parse("{\"app\":\"pomodoro-focus\",\"version\":1}") as ParseResult.Failure).reason
        )
    }

    @Test
    fun outOfRangeValuesAreClampedAndBadRecordsDropped() {
        val json = "{\"app\":\"pomodoro-focus\",\"version\":1," +
            "\"settings\":{\"focusMinutes\":9999,\"dailyGoal\":50,\"sessionsBeforeLongBreak\":0}," +
            "\"appearance\":{\"themeMode\":\"NOPE\",\"backgroundDimPercent\":500}," +
            "\"history\":[{\"id\":\"x\",\"timestamp\":5,\"durationMillis\":-9}," +
            "{\"id\":\"x\",\"timestamp\":6,\"durationMillis\":1},{\"id\":\"\",\"timestamp\":7}]}"

        val data = (BackupManager.parse(json) as ParseResult.Success).data

        assertEquals(180, data.settings.focusMinutes)
        assertEquals(10, data.settings.dailyGoal)
        assertEquals(1, data.settings.sessionsBeforeLongBreak)
        assertEquals(ThemeMode.SYSTEM, data.themeMode)
        assertEquals(90, data.backgroundDimPercent)
        assertEquals(1, data.history.size)
        assertEquals(0L, data.history[0].durationMillis)
    }

    @Test
    fun backupWithoutDndFieldDefaultsToOff() {
        val json = "{\"app\":\"pomodoro-focus\",\"version\":1,\"settings\":{\"focusMinutes\":30}," +
            "\"appearance\":{},\"history\":[]}"

        val data = (BackupManager.parse(json) as ParseResult.Success).data

        assertEquals(false, data.settings.dndDuringFocus)
    }

    @Test
    fun backupWithoutTimerFontFallsBackToDefault() {
        val json = "{\"app\":\"pomodoro-focus\",\"version\":1,\"settings\":{}," +
            "\"appearance\":{\"themeMode\":\"LIGHT\"},\"history\":[]}"

        val data = (BackupManager.parse(json) as ParseResult.Success).data

        assertEquals(TimerFont.GEOMETRIC_BOLD, data.timerFont)
    }

    @Test
    fun timerFontDefaultsToGeometricBoldAndPersists() {
        assertEquals(TimerFont.GEOMETRIC_BOLD, prefs.loadAppearanceSettings().timerFont)

        prefs.saveAppearanceSettings(AppearanceSettings(timerFont = TimerFont.APP_FONT))

        assertEquals(TimerFont.APP_FONT, TimerPreferences(
            ApplicationProvider.getApplicationContext<Application>()
        ).loadAppearanceSettings().timerFont)
    }

    @Test
    fun replaceFocusHistoryOverwritesStoredRecords() {
        prefs.addFocusSessionRecord(FocusSessionRecord("old", 1L, 1000L))

        prefs.replaceFocusHistory(listOf(FocusSessionRecord("new", 2L, 2000L)))

        assertEquals(listOf("new"), prefs.loadFocusHistory().map { it.id })
    }

    @Test
    fun resetFocusStatisticsClearsHistoryAndCounterButKeepsSettings() {
        prefs.saveSettings(PomodoroSettings(focusMinutes = 40))
        prefs.addFocusSessionRecord(FocusSessionRecord("a", 1L, 1000L))
        prefs.seedActiveSession(42L, com.pomodorofocus.app.model.SessionType.FOCUS)
        prefs.markSessionCompleted(sessionId = 42L, sessionType = com.pomodorofocus.app.model.SessionType.FOCUS)
        assertEquals(1, prefs.loadCompletedSessions())

        prefs.resetFocusStatistics()

        assertTrue(prefs.loadFocusHistory().isEmpty())
        assertEquals(0, prefs.loadCompletedSessions())
        assertEquals(40, prefs.loadSettings().focusMinutes)
    }
}
