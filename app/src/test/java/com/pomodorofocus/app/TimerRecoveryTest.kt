package com.pomodorofocus.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.receiver.TimerBootReceiver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimerRecoveryTest {

    private lateinit var context: Context
    private lateinit var prefs: TimerPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("pomodoro_focus_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = TimerPreferences(context)
    }

    @Test
    fun bootRestoresRealtimeDeadlineFromSavedWallClockDeadline() {
        val sessionId = 77L
        prefs.saveActiveTimerState(
            sessionType = SessionType.FOCUS,
            isRunning = true,
            targetEndRealtime = 1L,
            remainingMillis = 60_000L,
            totalDurationMillis = 60_000L,
            sessionId = sessionId
        )
        val savedWallDeadline = prefs.getTargetEndWallClockTime()

        TimerBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val expectedRealtimeDeadline = SystemClock.elapsedRealtime() +
            (savedWallDeadline - System.currentTimeMillis())
        assertTrue(prefs.isTimerRunning())
        assertEquals(sessionId, prefs.getActiveSessionId())
        assertTrue(kotlin.math.abs(prefs.getTargetEndRealtime() - expectedRealtimeDeadline) < 1_000L)
    }

    @Test
    fun simultaneousCompletionCallbacksCountOnlyOnceAcrossPreferenceInstances() {
        val sessionId = 88L
        prefs.seedActiveSession(sessionId, SessionType.FOCUS)
        val otherInstance = TimerPreferences(context)
        val gate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<Boolean> {
                gate.await()
                prefs.markSessionCompleted(sessionId, SessionType.FOCUS)
            }
            val second = executor.submit<Boolean> {
                gate.await()
                otherInstance.markSessionCompleted(sessionId, SessionType.FOCUS)
            }
            gate.countDown()

            val markedCount = listOf(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)).count { it }
            assertEquals(1, markedCount)
            assertEquals(1, prefs.loadCompletedSessions())
            assertEquals(1, prefs.loadFocusHistory().size)
        } finally {
            executor.shutdownNow()
        }
    }
}
