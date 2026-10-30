package com.pomodorofocus.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusDndControllerTest {

    private lateinit var prefs: TimerPreferences
    private lateinit var nm: NotificationManager

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("pomodoro_focus_prefs", "pomodoro_focus_dnd_state").forEach {
            app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        prefs = TimerPreferences(app)
        nm = app.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    private fun startFocus() = prefs.saveActiveTimerState(SessionType.FOCUS, true, 1000L, 60_000L, 60_000L, 7L)
    private fun pauseFocus() = prefs.saveActiveTimerState(SessionType.FOCUS, false, 0L, 30_000L, 60_000L, 7L)

    @Test
    fun focusRunningTurnsDndOnAndPauseRestoresIt() {
        prefs.saveSettings(PomodoroSettings(dndDuringFocus = true))

        startFocus()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)

        pauseFocus()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    @Test
    fun finishingTheSessionRestoresDnd() {
        prefs.saveSettings(PomodoroSettings(dndDuringFocus = true))
        startFocus()

        prefs.markSessionCompleted(7L, SessionType.FOCUS)

        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    @Test
    fun settingOffDoesNothing() {
        prefs.saveSettings(PomodoroSettings(dndDuringFocus = false))
        startFocus()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    @Test
    fun breaksDoNotTurnDndOn() {
        prefs.saveSettings(PomodoroSettings(dndDuringFocus = true))
        prefs.saveActiveTimerState(SessionType.SHORT_BREAK, true, 1000L, 60_000L, 60_000L, 8L)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    @Test
    fun withoutAccessNothingChanges() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        prefs.saveSettings(PomodoroSettings(dndDuringFocus = true))
        startFocus()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    @Test
    fun usersOwnDndModeIsNeverOverridden() {
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        prefs.saveSettings(PomodoroSettings(dndDuringFocus = true))

        startFocus()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter)

        pauseFocus()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter)
    }
}
