package com.pomodorofocus.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pomodorofocus.app.data.BackupManager
import com.pomodorofocus.app.data.BackupManager.ParseResult
import com.pomodorofocus.app.data.FocusSessionJsonAdapter
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.AppearanceSettings
import com.pomodorofocus.app.model.FocusSessionRecord
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.SessionTag
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.model.sanitizeSessionTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionTitleTagTest {

    private lateinit var prefs: TimerPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("pomodoro_focus_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = TimerPreferences(context)
    }

    private fun startFocus(): Long {
        val id = prefs.allocateSessionId()
        prefs.saveActiveTimerState(SessionType.FOCUS, true, 0L, 25 * 60_000L, 25 * 60_000L, id)
        return id
    }

    @Test
    fun completedSessionKeepsTitleAndTag() {
        val id = startFocus()
        assertTrue(prefs.saveSessionMeta(id, "  Client B   illustration ", SessionTag.CREATIVE))
        assertTrue(prefs.markSessionCompleted(id, SessionType.FOCUS))

        val record = prefs.loadFocusHistory().single()
        assertEquals("Client B illustration", record.title)
        assertEquals(SessionTag.CREATIVE, record.tag)
    }

    @Test
    fun leavingEverythingEmptyKeepsSessionNormal() {
        val id = startFocus()
        assertTrue(prefs.markSessionCompleted(id, SessionType.FOCUS))

        val record = prefs.loadFocusHistory().single()
        assertEquals("", record.title)
        assertNull(record.tag)
        assertEquals(1, prefs.loadCompletedSessions())
    }

    @Test
    fun blankTitleAndNoTagClearsEarlierMeta() {
        val id = startFocus()
        prefs.saveSessionMeta(id, "Draft", SessionTag.WORK)
        prefs.saveSessionMeta(id, "   ", null)
        assertNull(prefs.loadSessionMeta(id))
    }

    @Test
    fun metaDoesNotLeakToAnotherSession() {
        val first = startFocus()
        prefs.saveSessionMeta(first, "Old", SessionTag.STUDY)
        prefs.clearActiveTimerState() // cancelled / reset

        val second = startFocus()
        assertNull(prefs.loadSessionMeta(second))
        assertTrue(prefs.markSessionCompleted(second, SessionType.FOCUS))
        assertEquals("", prefs.loadFocusHistory().single().title)
    }

    @Test
    fun saveIsIgnoredWhenSessionIsNotActiveOrAlreadyCompleted() {
        val id = startFocus()
        prefs.markSessionCompleted(id, SessionType.FOCUS)
        assertFalse(prefs.saveSessionMeta(id, "Too late", SessionTag.WORK))
        assertFalse(prefs.saveSessionMeta(0L, "No session", SessionTag.WORK))
    }

    @Test
    fun jsonRoundTripAndOldRecordsStillLoad() {
        val records = listOf(
            FocusSessionRecord("a", 1000L, 60_000L, "Read paper", SessionTag.READING),
            FocusSessionRecord("b", 2000L, 60_000L)
        )
        val loaded = FocusSessionJsonAdapter.fromJson(FocusSessionJsonAdapter.toJson(records))
        assertEquals(records, loaded)

        val old = """[{"id":"x","timestamp":5,"durationMillis":1000}]"""
        val oldLoaded = FocusSessionJsonAdapter.fromJson(old).single()
        assertEquals("", oldLoaded.title)
        assertNull(oldLoaded.tag)

        val unknownTag = """[{"id":"y","timestamp":5,"durationMillis":1000,"tag":"NOPE"}]"""
        assertNull(FocusSessionJsonAdapter.fromJson(unknownTag).single().tag)
    }

    @Test
    fun backupKeepsTitleAndTag() {
        val history = listOf(FocusSessionRecord("a", 1000L, 60_000L, "Run", SessionTag.FITNESS))
        val result = BackupManager.parse(
            BackupManager.toJson(PomodoroSettings(), AppearanceSettings(), history)
        )
        val data = (result as ParseResult.Success).data
        assertEquals("Run", data.history.single().title)
        assertEquals(SessionTag.FITNESS, data.history.single().tag)
    }

    @Test
    fun titleIsTrimmedAndLimited() {
        assertEquals("", sanitizeSessionTitle("  \n "))
        assertEquals(60, sanitizeSessionTitle("x".repeat(200)).length)
    }
}
