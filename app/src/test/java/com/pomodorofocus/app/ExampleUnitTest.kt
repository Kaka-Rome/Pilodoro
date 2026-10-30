package com.pomodorofocus.app

import com.pomodorofocus.app.data.FocusStatisticsCalculator
import com.pomodorofocus.app.model.FocusSessionRecord
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.PomodoroUiState
import com.pomodorofocus.app.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ExampleUnitTest {

    @Test
    fun testSettingsDefaults() {
        val settings = PomodoroSettings()
        assertEquals(25, settings.focusMinutes)
        assertEquals(5, settings.shortBreakMinutes)
        assertEquals(15, settings.longBreakMinutes)
        assertEquals(25 * 60 * 1000L, settings.getDurationMillis(SessionType.FOCUS))
        assertEquals(5 * 60 * 1000L, settings.getDurationMillis(SessionType.SHORT_BREAK))
        assertEquals(15 * 60 * 1000L, settings.getDurationMillis(SessionType.LONG_BREAK))
    }

    @Test
    fun testSessionProgression() {
        // Focus -> Short Break
        assertEquals(SessionType.SHORT_BREAK, SessionType.FOCUS.nextSession(1))
        assertEquals(SessionType.SHORT_BREAK, SessionType.FOCUS.nextSession(2))
        assertEquals(SessionType.SHORT_BREAK, SessionType.FOCUS.nextSession(3))

        // After 4 completed focus sessions -> Long Break
        assertEquals(SessionType.LONG_BREAK, SessionType.FOCUS.nextSession(4))

        // Break -> Focus
        assertEquals(SessionType.FOCUS, SessionType.SHORT_BREAK.nextSession(1))
        assertEquals(SessionType.FOCUS, SessionType.LONG_BREAK.nextSession(4))
    }

    @Test
    fun testNewSettingsDefaults() {
        val settings = PomodoroSettings()
        assertEquals(4, settings.sessionsBeforeLongBreak)
        assertEquals(false, settings.autoStartBreaks)
        assertEquals(false, settings.autoStartFocus)
        assertEquals(true, settings.keepScreenOn)
        assertEquals(4, settings.dailyGoal)
    }

    @Test
    fun testLongBreakIntervalIsConfigurable() {
        // Setiap 2 sesi Focus -> Long Break
        assertEquals(SessionType.SHORT_BREAK, SessionType.FOCUS.nextSession(1, 2))
        assertEquals(SessionType.LONG_BREAK, SessionType.FOCUS.nextSession(2, 2))
        assertEquals(SessionType.SHORT_BREAK, SessionType.FOCUS.nextSession(3, 2))
        assertEquals(SessionType.LONG_BREAK, SessionType.FOCUS.nextSession(4, 2))
        // Nilai tidak valid tidak boleh menyebabkan error pembagian nol
        assertEquals(SessionType.LONG_BREAK, SessionType.FOCUS.nextSession(3, 0))
    }

    @Test
    fun testCountSessionsToday() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = 1_700_000_000_000L // 14 Nov 2023, 22:13 UTC
        val dayStart = now - (now % 86_400_000L)
        val records = listOf(
            FocusSessionRecord("a", dayStart + 1_000L, 1_500_000L),        // hari ini
            FocusSessionRecord("b", dayStart + 3_600_000L, 1_500_000L),    // hari ini
            FocusSessionRecord("c", dayStart - 1_000L, 1_500_000L),        // kemarin
            FocusSessionRecord("d", dayStart + 86_400_000L + 5L, 1_500_000L) // besok
        )
        assertEquals(2, FocusStatisticsCalculator.countSessionsToday(records, now, utc, Locale.US))
        assertEquals(0, FocusStatisticsCalculator.countSessionsToday(emptyList(), now, utc, Locale.US))
    }

    @Test
    fun testTimeFormatting() {
        val state25Min = PomodoroUiState(remainingMillis = 25 * 60 * 1000L)
        assertEquals("25:00", state25Min.formattedTime)

        val state5Min = PomodoroUiState(remainingMillis = 5 * 60 * 1000L)
        assertEquals("05:00", state5Min.formattedTime)

        val state10Sec = PomodoroUiState(remainingMillis = 10 * 1000L)
        assertEquals("00:10", state10Sec.formattedTime)

        val state0 = PomodoroUiState(remainingMillis = 0L)
        assertEquals("00:00", state0.formattedTime)
    }

    @Test
    fun testProgressCalculation() {
        val stateFull = PomodoroUiState(
            remainingMillis = 1000L,
            totalDurationMillis = 1000L
        )
        assertEquals(0f, stateFull.progress, 0.001f)

        val stateHalf = PomodoroUiState(
            remainingMillis = 500L,
            totalDurationMillis = 1000L
        )
        assertEquals(0.5f, stateHalf.progress, 0.001f)

        val stateDone = PomodoroUiState(
            remainingMillis = 0L,
            totalDurationMillis = 1000L
        )
        assertEquals(1f, stateDone.progress, 0.001f)
    }
}
