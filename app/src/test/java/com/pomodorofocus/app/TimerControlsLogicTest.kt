package com.pomodorofocus.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.model.TimerState
import com.pomodorofocus.app.notification.TimerAlarmScheduler
import com.pomodorofocus.app.ui.PomodoroViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimerControlsLogicTest {

    private lateinit var app: Application
    private lateinit var prefs: TimerPreferences

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("pomodoro_focus_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = TimerPreferences(app)
    }

    private fun newViewModel() = PomodoroViewModel(app, prefs, TimerAlarmScheduler(app))

    @Test
    fun upcomingSessionFollowsTheNextInfoRule() {
        assertEquals(SessionType.SHORT_BREAK, SessionType.FOCUS.upcoming(0, 4))
        assertEquals(SessionType.SHORT_BREAK, SessionType.FOCUS.upcoming(2, 4))
        assertEquals(SessionType.LONG_BREAK, SessionType.FOCUS.upcoming(3, 4))
        assertEquals(SessionType.FOCUS, SessionType.SHORT_BREAK.upcoming(3, 4))
        assertEquals(SessionType.FOCUS, SessionType.LONG_BREAK.upcoming(3, 4))
    }

    @Test
    fun pauseKeepsTheRemainingTimeAndResumeContinuesFromIt() {
        val vm = newViewModel()
        vm.startTimer()
        vm.pauseTimer()

        val paused = vm.uiState.value
        assertEquals(TimerState.PAUSED, paused.timerState)
        assertEquals(paused.remainingMillis, prefs.getSavedRemainingMillis())

        vm.resumeTimer()
        val resumed = vm.uiState.value
        assertEquals(TimerState.RUNNING, resumed.timerState)
        assertTrue(resumed.remainingMillis <= paused.remainingMillis)
        assertTrue(resumed.remainingMillis > paused.remainingMillis - 5_000L)
    }

    @Test
    fun resetThenUndoBringsBackTheRemainingTime() {
        val vm = newViewModel()
        vm.startTimer()
        vm.pauseTimer()
        val remainingBeforeReset = vm.uiState.value.remainingMillis

        vm.resetTimer()
        assertEquals(TimerState.IDLE, vm.uiState.value.timerState)
        assertEquals(vm.uiState.value.totalDurationMillis, vm.uiState.value.remainingMillis)

        vm.undoResetTimer()
        assertEquals(TimerState.PAUSED, vm.uiState.value.timerState)
        assertEquals(remainingBeforeReset, vm.uiState.value.remainingMillis)
    }

    @Test
    fun undoDoesNothingOnceAnotherTimerHasStarted() {
        val vm = newViewModel()
        vm.startTimer()
        vm.pauseTimer()
        vm.resetTimer()
        vm.startTimer()

        vm.undoResetTimer()

        assertEquals(TimerState.RUNNING, vm.uiState.value.timerState)
    }

    @Test
    fun resetIsSavedAsReadyNotPausedSoReopeningShowsTheSinglePlayIcon() {
        val first = newViewModel()
        first.startTimer()
        first.pauseTimer()
        first.resetTimer()

        val reopened = newViewModel()

        assertEquals(TimerState.IDLE, reopened.uiState.value.timerState)
    }

    @Test
    fun endSessionWithoutMarkingCompletedAddsNothingToStats() {
        val vm = newViewModel()
        vm.startTimer()
        vm.pauseTimer()

        vm.endSession(markCompleted = false)

        assertEquals(TimerState.IDLE, vm.uiState.value.timerState)
        assertEquals(SessionType.FOCUS, vm.uiState.value.sessionType)
        assertEquals(0, prefs.loadFocusHistory().size)
        assertEquals(0, prefs.loadCompletedSessions())
    }

    @Test
    fun endSessionMarkedCompletedRecordsTheTimeActuallySpent() {
        val vm = newViewModel()
        vm.startTimer()
        vm.pauseTimer()
        val total = vm.uiState.value.totalDurationMillis

        vm.endSession(markCompleted = true)

        val history = prefs.loadFocusHistory()
        assertEquals(1, history.size)
        assertTrue(history[0].durationMillis < total)
        assertEquals(1, prefs.loadCompletedSessions())
        // Moves on to the next session like a normal finish (auto-start is off by default)
        assertEquals(SessionType.SHORT_BREAK, vm.uiState.value.sessionType)
        assertEquals(TimerState.IDLE, vm.uiState.value.timerState)
    }

    @Test
    fun nextGoesToTheSessionShownInTheNextInfo() {
        prefs.saveSettings(PomodoroSettings(sessionsBeforeLongBreak = 1))
        val vm = newViewModel()
        vm.startTimer()
        vm.pauseTimer()

        vm.skipToNextSession()

        assertEquals(SessionType.LONG_BREAK, vm.uiState.value.sessionType)
        assertEquals(TimerState.IDLE, vm.uiState.value.timerState)
        assertEquals(0, prefs.loadFocusHistory().size)
    }

    @Test
    fun nextFollowsTheAutoStartSetting() {
        prefs.saveSettings(PomodoroSettings(autoStartBreaks = true))
        val vm = newViewModel()
        vm.startTimer()
        vm.pauseTimer()

        vm.skipToNextSession()

        assertEquals(SessionType.SHORT_BREAK, vm.uiState.value.sessionType)
        assertEquals(TimerState.RUNNING, vm.uiState.value.timerState)
    }

    @Test
    fun notificationNextStateIsReflectedAsIdleInTheViewModel() {
        val vm = newViewModel()
        vm.startTimer()
        val breakDuration = prefs.loadSettings().getDurationMillis(SessionType.SHORT_BREAK)

        // Simulate the foreground service completing its Next transition before notifying the UI.
        prefs.saveNextSessionAsIdle(SessionType.SHORT_BREAK, breakDuration)
        vm.syncWithElapsedTime()

        assertEquals(SessionType.SHORT_BREAK, vm.uiState.value.sessionType)
        assertEquals(TimerState.IDLE, vm.uiState.value.timerState)
        assertEquals(0L, vm.uiState.value.remainingMillis - breakDuration)
    }

    @Test
    fun staleCompletionCannotCompleteTheSessionThatReplacedIt() {
        prefs.saveActiveTimerState(
            SessionType.FOCUS,
            isRunning = true,
            targetEndRealtime = 10_000L,
            remainingMillis = 10_000L,
            totalDurationMillis = 10_000L,
            sessionId = 100L
        )
        prefs.saveNextSessionAsIdle(
            SessionType.SHORT_BREAK,
            prefs.loadSettings().getDurationMillis(SessionType.SHORT_BREAK)
        )

        assertTrue(!prefs.markSessionCompleted(100L, SessionType.FOCUS))
        assertEquals(0, prefs.loadCompletedSessions())
        assertEquals(SessionType.SHORT_BREAK, prefs.loadSavedSessionType())
    }
}
