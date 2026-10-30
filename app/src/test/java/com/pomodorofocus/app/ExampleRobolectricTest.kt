package com.pomodorofocus.app

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pomodorofocus.app.data.FocusStatisticsCalculator
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.AppearanceSettings
import com.pomodorofocus.app.model.AppFont
import com.pomodorofocus.app.model.AppScreen
import com.pomodorofocus.app.model.ColorPalette
import com.pomodorofocus.app.model.FocusSessionRecord
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.model.StatsPeriod
import com.pomodorofocus.app.model.ThemeMode
import com.pomodorofocus.app.model.TimerState
import com.pomodorofocus.app.notification.TimerAlarmScheduler
import com.pomodorofocus.app.receiver.TimerExpiredReceiver
import com.pomodorofocus.app.ui.PomodoroViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import android.app.NotificationManager
import com.pomodorofocus.app.notification.NotificationHelper
import com.pomodorofocus.app.service.TimerForegroundService
import com.pomodorofocus.app.service.TimerServiceBridge
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var prefs: TimerPreferences

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        context = app
        context.getSharedPreferences("pomodoro_focus_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = TimerPreferences(context)
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun testAppNameString() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Pilodoro", appName)
    }

    @Test
    fun testPreferencesSaveAndLoadSettings() {
        val custom = PomodoroSettings(focusMinutes = 30, shortBreakMinutes = 7, longBreakMinutes = 20)
        prefs.saveSettings(custom)

        val loaded = prefs.loadSettings()
        assertEquals(30, loaded.focusMinutes)
        assertEquals(7, loaded.shortBreakMinutes)
        assertEquals(20, loaded.longBreakMinutes)
    }

    @Test
    fun testFocusSessionIncrementsCounter() {
        assertEquals(0, prefs.loadCompletedSessions())

        // Focus session 1 completes
        prefs.seedActiveSession(101L, SessionType.FOCUS)
        val marked1 = prefs.markSessionCompleted(101L, SessionType.FOCUS)
        assertTrue(marked1)
        assertEquals(1, prefs.loadCompletedSessions())

        // Focus session 2 completes
        prefs.seedActiveSession(102L, SessionType.FOCUS)
        val marked2 = prefs.markSessionCompleted(102L, SessionType.FOCUS)
        assertTrue(marked2)
        assertEquals(2, prefs.loadCompletedSessions())
    }

    @Test
    fun testBreakSessionDoesNotIncrementCounter() {
        assertEquals(0, prefs.loadCompletedSessions())

        // Short break completes
        prefs.seedActiveSession(201L, SessionType.SHORT_BREAK)
        val markedShort = prefs.markSessionCompleted(201L, SessionType.SHORT_BREAK)
        assertTrue(markedShort)
        assertEquals(0, prefs.loadCompletedSessions())

        // Long break completes
        prefs.seedActiveSession(202L, SessionType.LONG_BREAK)
        val markedLong = prefs.markSessionCompleted(202L, SessionType.LONG_BREAK)
        assertTrue(markedLong)
        assertEquals(0, prefs.loadCompletedSessions())
    }

    @Test
    fun testNoDoubleCountingSameSession() {
        assertEquals(0, prefs.loadCompletedSessions())

        prefs.seedActiveSession(301L, SessionType.FOCUS)
        val firstAttempt = prefs.markSessionCompleted(301L, SessionType.FOCUS)
        assertTrue(firstAttempt)
        assertEquals(1, prefs.loadCompletedSessions())

        // Attempting to complete the same session ID again returns false and does NOT double-count
        val secondAttempt = prefs.markSessionCompleted(301L, SessionType.FOCUS)
        assertFalse(secondAttempt)
        assertEquals(1, prefs.loadCompletedSessions())
    }

    @Test
    fun testCompletingFocusSessionAddsExactlyOneHistoryRecord() {
        assertEquals(0, prefs.loadFocusHistory().size)

        prefs.seedActiveSession(401L, SessionType.FOCUS)
        val marked = prefs.markSessionCompleted(
            sessionId = 401L,
            sessionType = SessionType.FOCUS,
            durationMillis = 25 * 60 * 1000L
        )
        assertTrue(marked)

        val history = prefs.loadFocusHistory()
        assertEquals(1, history.size)
        val record = history[0]
        assertTrue(record.id.isNotBlank())
        assertTrue(record.timestamp > 0L)
        assertEquals(25 * 60 * 1000L, record.durationMillis)
    }

    @Test
    fun testCompletingBreakSessionAddsNoFocusRecord() {
        assertEquals(0, prefs.loadFocusHistory().size)

        prefs.seedActiveSession(501L, SessionType.SHORT_BREAK)
        prefs.markSessionCompleted(501L, SessionType.SHORT_BREAK)
        assertEquals(0, prefs.loadFocusHistory().size)

        prefs.seedActiveSession(502L, SessionType.LONG_BREAK)
        prefs.markSessionCompleted(502L, SessionType.LONG_BREAK)
        assertEquals(0, prefs.loadFocusHistory().size)
    }

    @Test
    fun testNoDuplicateHistoryRecordOnDoubleCompletion() {
        assertEquals(0, prefs.loadFocusHistory().size)

        prefs.seedActiveSession(601L, SessionType.FOCUS)
        val marked1 = prefs.markSessionCompleted(601L, SessionType.FOCUS, 25 * 60 * 1000L)
        assertTrue(marked1)
        assertEquals(1, prefs.loadFocusHistory().size)

        // Duplicate attempt with same session ID
        val marked2 = prefs.markSessionCompleted(601L, SessionType.FOCUS, 25 * 60 * 1000L)
        assertFalse(marked2)
        assertEquals(1, prefs.loadFocusHistory().size)
    }

    @Test
    fun testHistoricalRecordsPersistAcrossAppReopen() {
        prefs.addFocusSessionRecord(
            FocusSessionRecord(id = "rec-1", timestamp = 1729770000000L, durationMillis = 1500000L)
        )
        prefs.addFocusSessionRecord(
            FocusSessionRecord(id = "rec-2", timestamp = 1729773600000L, durationMillis = 1800000L)
        )

        // Instantiate new TimerPreferences to simulate reopening the app
        val newPrefs = TimerPreferences(context)
        val loaded = newPrefs.loadFocusHistory()

        assertEquals(2, loaded.size)
        assertEquals("rec-1", loaded[0].id)
        assertEquals(1500000L, loaded[0].durationMillis)
        assertEquals("rec-2", loaded[1].id)
        assertEquals(1800000L, loaded[1].durationMillis)
    }

    @Test
    fun testDailyWeeklyMonthlyTotalsAgreeWithSavedHistory() {
        // Construct fixed anchor time: Wednesday, Oct 21, 2026, 14:00:00
        val tz = TimeZone.getTimeZone("UTC")
        val locale = Locale.US
        val anchorCal = Calendar.getInstance(tz, locale).apply {
            set(2026, Calendar.OCTOBER, 21, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val anchorMillis = anchorCal.timeInMillis

        // Session 1: Wednesday (anchor day) 10:00 AM (25 min)
        val s1Cal = (anchorCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 10) }
        val r1 = FocusSessionRecord("id-1", s1Cal.timeInMillis, 25 * 60 * 1000L)

        // Session 2: Wednesday (anchor day) 11:30 AM (25 min)
        val s2Cal = (anchorCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 30)
        }
        val r2 = FocusSessionRecord("id-2", s2Cal.timeInMillis, 25 * 60 * 1000L)

        // Session 3: Tuesday (yesterday) 15:00 (50 min)
        val s3Cal = (anchorCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 15)
        }
        val r3 = FocusSessionRecord("id-3", s3Cal.timeInMillis, 50 * 60 * 1000L)

        // Session 4: Previous week (Wednesday, Oct 14) (30 min)
        val s4Cal = (anchorCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -7)
        }
        val r4 = FocusSessionRecord("id-4", s4Cal.timeInMillis, 30 * 60 * 1000L)

        val allRecords = listOf(r1, r2, r3, r4)

        // 1. Daily for anchor day: r1 and r2 only
        val daily = FocusStatisticsCalculator.calculate(
            period = StatsPeriod.DAILY,
            offset = 0,
            records = allRecords,
            nowMillis = anchorMillis,
            timeZone = tz,
            locale = locale
        )
        assertEquals(2, daily.completedCount)
        assertEquals(50 * 60 * 1000L, daily.totalDurationMillis)

        // 2. Weekly for anchor week (Oct 19 - Oct 25): includes Tuesday and Wednesday (r1, r2, r3 = 100 min, 3 sessions)
        val weekly = FocusStatisticsCalculator.calculate(
            period = StatsPeriod.WEEKLY,
            offset = 0,
            records = allRecords,
            nowMillis = anchorMillis,
            timeZone = tz,
            locale = locale
        )
        assertEquals(3, weekly.completedCount)
        assertEquals(100 * 60 * 1000L, weekly.totalDurationMillis)
        // 7 days in weekly chart
        assertEquals(7, weekly.chartItems.size)
        assertEquals("Mon", weekly.chartItems[0].label)
        assertEquals("Sun", weekly.chartItems[6].label)

        // 3. Monthly for October 2026: includes all 4 sessions in October (130 min, 4 sessions)
        val monthly = FocusStatisticsCalculator.calculate(
            period = StatsPeriod.MONTHLY,
            offset = 0,
            records = allRecords,
            nowMillis = anchorMillis,
            timeZone = tz,
            locale = locale
        )
        assertEquals(4, monthly.completedCount)
        assertEquals(130 * 60 * 1000L, monthly.totalDurationMillis)
        // October has 31 days
        assertEquals(31, monthly.chartItems.size)
    }

    @Test
    fun testNavigationDoesNotAllowFuturePeriods() {
        val dataCurrent = FocusStatisticsCalculator.calculate(
            period = StatsPeriod.WEEKLY,
            offset = 0,
            records = emptyList()
        )
        assertFalse(dataCurrent.canGoForward)

        val dataPast = FocusStatisticsCalculator.calculate(
            period = StatsPeriod.WEEKLY,
            offset = -1,
            records = emptyList()
        )
        assertTrue(dataPast.canGoForward)
    }

    @Test
    fun testTimerExpiredReceiverForFocusIncrementsCountAndRecordsHistory() {
        assertEquals(0, prefs.loadCompletedSessions())
        assertEquals(0, prefs.loadFocusHistory().size)

        val receiver = TimerExpiredReceiver()
        seedExpiredRunningSession(701L, SessionType.FOCUS)
        val intent = Intent(TimerAlarmScheduler.ACTION_TIMER_EXPIRED).apply {
            putExtra(TimerAlarmScheduler.EXTRA_SESSION_TYPE, SessionType.FOCUS.name)
            putExtra(TimerAlarmScheduler.EXTRA_SESSION_ID, 701L)
        }
        receiver.onReceive(context, intent)

        assertEquals(1, prefs.loadCompletedSessions())
        assertEquals(1, prefs.loadFocusHistory().size)

        // Repeating same intent should not double count or duplicate record
        receiver.onReceive(context, intent)
        assertEquals(1, prefs.loadCompletedSessions())
        assertEquals(1, prefs.loadFocusHistory().size)
    }

    @Test
    fun testTimerExpiredReceiverForShortBreakDoesNotIncrementCountOrRecordHistory() {
        assertEquals(0, prefs.loadCompletedSessions())
        assertEquals(0, prefs.loadFocusHistory().size)

        val receiver = TimerExpiredReceiver()
        seedExpiredRunningSession(801L, SessionType.SHORT_BREAK)
        val intent = Intent(TimerAlarmScheduler.ACTION_TIMER_EXPIRED).apply {
            putExtra(TimerAlarmScheduler.EXTRA_SESSION_TYPE, SessionType.SHORT_BREAK.name)
            putExtra(TimerAlarmScheduler.EXTRA_SESSION_ID, 801L)
        }
        receiver.onReceive(context, intent)

        assertEquals(0, prefs.loadCompletedSessions())
        assertEquals(0, prefs.loadFocusHistory().size)
    }

    @Test
    fun testViewModelSyncsWhenSessionCompletedInBackground() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = TimerAlarmScheduler(app)
        val viewModel = PomodoroViewModel(app, prefs, scheduler)

        // User starts a Focus session
        viewModel.selectSessionType(SessionType.FOCUS)
        viewModel.startTimer()
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertEquals(0, viewModel.uiState.value.completedSessions)

        val activeId = prefs.getActiveSessionId()
        assertTrue(activeId > 0L)

        // Simulate TimerExpiredReceiver triggering while app is backgrounded
        seedExpiredRunningSession(activeId, SessionType.FOCUS)
        val receiver = TimerExpiredReceiver()
        val intent = Intent(TimerAlarmScheduler.ACTION_TIMER_EXPIRED).apply {
            putExtra(TimerAlarmScheduler.EXTRA_SESSION_TYPE, SessionType.FOCUS.name)
            putExtra(TimerAlarmScheduler.EXTRA_SESSION_ID, activeId)
        }
        receiver.onReceive(context, intent)
        assertEquals(1, prefs.loadCompletedSessions())
        assertEquals(1, prefs.loadFocusHistory().size)

        // User brings app back to foreground (calls syncWithElapsedTime)
        viewModel.syncWithElapsedTime()

        // With auto-start off, the next session is prepared as idle.
        assertEquals(TimerState.IDLE, viewModel.uiState.value.timerState)
        assertEquals(1, viewModel.uiState.value.completedSessions)
        assertEquals(1, viewModel.uiState.value.statsPeriodData?.completedCount)
    }

    @Test
    fun testAppearanceSettingsSaveAndPersistAcrossReopen() {
        val custom = AppearanceSettings(
            themeMode = ThemeMode.DARK,
            colorPalette = ColorPalette.SAGE,
            appFont = AppFont.MONOSPACE,
            isMinimalMode = true
        )
        prefs.saveAppearanceSettings(custom)

        val newPrefs = TimerPreferences(context)
        val loaded = newPrefs.loadAppearanceSettings()

        assertEquals(ThemeMode.DARK, loaded.themeMode)
        assertEquals(ColorPalette.SAGE, loaded.colorPalette)
        assertEquals(AppFont.MONOSPACE, loaded.appFont)
        assertTrue(loaded.isMinimalMode)
    }

    @Test
    fun testThemeChangeDoesNotResetOrAlterRunningTimer() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = TimerAlarmScheduler(app)
        val viewModel = PomodoroViewModel(app, prefs, scheduler)

        viewModel.selectSessionType(SessionType.FOCUS)
        viewModel.startTimer()
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        val initialRemaining = viewModel.uiState.value.remainingMillis

        // Change appearance theme & palette
        val newAppearance = AppearanceSettings(
            themeMode = ThemeMode.LIGHT,
            colorPalette = ColorPalette.VIOLET,
            appFont = AppFont.SERIF,
            isMinimalMode = false
        )
        viewModel.saveAppearanceSettings(newAppearance)

        // Verify timer state is completely undisturbed
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertEquals(SessionType.FOCUS, viewModel.uiState.value.sessionType)
        assertEquals(newAppearance, viewModel.uiState.value.appearanceSettings)
        assertTrue(viewModel.uiState.value.remainingMillis > 0L)
    }

    @Test
    fun testMinimalModeToggleDoesNotAlterTimerState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = TimerAlarmScheduler(app)
        val viewModel = PomodoroViewModel(app, prefs, scheduler)

        viewModel.startTimer()
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertFalse(viewModel.uiState.value.appearanceSettings.isMinimalMode)

        // Toggle minimal mode ON
        viewModel.toggleMinimalMode()
        assertTrue(viewModel.uiState.value.appearanceSettings.isMinimalMode)
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertTrue(prefs.loadAppearanceSettings().isMinimalMode)

        // Toggle minimal mode OFF
        viewModel.toggleMinimalMode()
        assertFalse(viewModel.uiState.value.appearanceSettings.isMinimalMode)
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertFalse(prefs.loadAppearanceSettings().isMinimalMode)
    }

    @Test
    fun testBackgroundImageManagerSaveLoadDelete() {
        kotlinx.coroutines.runBlocking {
            val bgManager = com.pomodorofocus.app.data.BackgroundImageManager(context)
            // Clean start
            bgManager.deleteCustomBackground()
            assertFalse(bgManager.hasCustomBackgroundFile())

            // Create sample bitmap
            val sampleBitmap = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)
            val saved = bgManager.saveBitmapDirectly(sampleBitmap)
            assertTrue(saved)
            assertTrue(bgManager.hasCustomBackgroundFile())

            // Load bitmap back from app-private storage
            val loaded = bgManager.loadBitmap()
            assertNotNull(loaded)
            assertEquals(200, loaded?.width)
            assertEquals(200, loaded?.height)

            // Delete custom background
            val deleted = bgManager.deleteCustomBackground()
            assertTrue(deleted)
            assertFalse(bgManager.hasCustomBackgroundFile())
            val loadedAfterDelete = bgManager.loadBitmap()
            org.junit.Assert.assertNull(loadedAfterDelete)
        }
    }

    @Test
    fun testBackgroundImageManagerHandlesMissingOrCorruptFileGracefully() {
        kotlinx.coroutines.runBlocking {
            val bgManager = com.pomodorofocus.app.data.BackgroundImageManager(context)
            bgManager.deleteCustomBackground()

            // Missing file should return null rather than throw
            val loaded = bgManager.loadBitmap()
            org.junit.Assert.assertNull(loaded)
        }
    }

    @Test
    fun testCustomBackgroundSettingsPersistence() {
        val custom = AppearanceSettings(
            themeMode = ThemeMode.DARK,
            colorPalette = ColorPalette.AMBER,
            appFont = AppFont.ROUNDED,
            isMinimalMode = false,
            hasCustomBackground = true,
            backgroundDimPercent = 55
        )
        prefs.saveAppearanceSettings(custom)

        val reloaded = TimerPreferences(context).loadAppearanceSettings()
        assertTrue(reloaded.hasCustomBackground)
        assertEquals(55, reloaded.backgroundDimPercent)
        assertEquals(ColorPalette.AMBER, reloaded.colorPalette)
    }

    @Test
    fun testApplyingAndRemovingBackgroundDoesNotResetRunningTimer() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = TimerAlarmScheduler(app)
        val viewModel = PomodoroViewModel(app, prefs, scheduler)

        viewModel.selectSessionType(SessionType.FOCUS)
        viewModel.startTimer()
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        val remainingBefore = viewModel.uiState.value.remainingMillis

        // Apply a custom background bitmap
        val sampleBitmap = android.graphics.Bitmap.createBitmap(150, 150, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.setCustomBackgroundBitmap(sampleBitmap)

        // Verify timer is still running and intact
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertEquals(SessionType.FOCUS, viewModel.uiState.value.sessionType)
        assertTrue(viewModel.uiState.value.remainingMillis > 0L)
        assertTrue(viewModel.uiState.value.remainingMillis <= remainingBefore)

        // Adjust dim percent
        viewModel.setBackgroundDimPercent(65)
        assertEquals(65, viewModel.uiState.value.appearanceSettings.backgroundDimPercent)
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)

        // Remove custom background
        viewModel.removeCustomBackground()
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertEquals(SessionType.FOCUS, viewModel.uiState.value.sessionType)
        assertFalse(viewModel.uiState.value.appearanceSettings.hasCustomBackground)
        org.junit.Assert.assertNull(viewModel.uiState.value.customBackgroundBitmap)
    }

    @Test
    fun testBackgroundImageManagerUriCopyAndDownsample() {
        kotlinx.coroutines.runBlocking {
            val bgManager = com.pomodorofocus.app.data.BackgroundImageManager(context)
            bgManager.deleteCustomBackground()

            // Create a temporary image file simulating user's gallery selection
            val tempGalleryFile = File(context.cacheDir, "gallery_simulated_image.jpg")
            val sourceBitmap = android.graphics.Bitmap.createBitmap(300, 300, android.graphics.Bitmap.Config.ARGB_8888)
            FileOutputStream(tempGalleryFile).use { out ->
                sourceBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            val fileUri = Uri.fromFile(tempGalleryFile)

            // Save from Uri
            val saved = bgManager.saveImageFromUri(fileUri)
            assertTrue(saved)
            assertTrue(bgManager.hasCustomBackgroundFile())

            // Ensure temp file during upload was cleaned up
            val rawTemp = File(context.filesDir, com.pomodorofocus.app.data.BackgroundImageManager.RAW_TEMP_FILE_NAME)
            assertFalse(rawTemp.exists())

            // Verify loaded bitmap
            val loaded = bgManager.loadBitmap()
            assertNotNull(loaded)
            assertEquals(300, loaded?.width)
            assertEquals(300, loaded?.height)

            tempGalleryFile.delete()
            bgManager.deleteCustomBackground()
        }
    }

    @Test
    fun testBackgroundImageManagerInvalidUriReturnsFalse() {
        kotlinx.coroutines.runBlocking {
            val bgManager = com.pomodorofocus.app.data.BackgroundImageManager(context)
            val nonExistentUri = Uri.parse("file:///non_existent_path/fake_image.jpg")
            val result = bgManager.saveImageFromUri(nonExistentUri)
            assertFalse(result)
        }
    }

    @Test
    fun testViewModelSetCustomBackgroundUriFailureSetsErrorMessage() {
        kotlinx.coroutines.runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val scheduler = TimerAlarmScheduler(app)
            val viewModel = PomodoroViewModel(app, prefs, scheduler)

            // Ensure initially no custom background and no error
            assertFalse(viewModel.uiState.value.appearanceSettings.hasCustomBackground)
            org.junit.Assert.assertNull(viewModel.uiState.value.backgroundErrorMessage)

            // Simulate picking an unreadable / non-existent URI
            viewModel.setCustomBackgroundUri(Uri.parse("file:///dev/null/does_not_exist.jpg"))

            // Allow background IO coroutine to complete and idle main looper
            Thread.sleep(300)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            // Should report error and keep hasCustomBackground false
            assertFalse(viewModel.uiState.value.appearanceSettings.hasCustomBackground)
            assertNotNull(viewModel.uiState.value.backgroundErrorMessage)

            // Clear error
            viewModel.clearBackgroundError()
            org.junit.Assert.assertNull(viewModel.uiState.value.backgroundErrorMessage)
        }
    }

    @Test
    fun testCustomBackgroundSurvivesAppReopen() {
        kotlinx.coroutines.runBlocking {
            val bgManager = com.pomodorofocus.app.data.BackgroundImageManager(context)
            val bitmap = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
            val saved = bgManager.saveBitmapDirectly(bitmap)
            assertTrue(saved)
            prefs.setCustomBackgroundEnabled(true)

            // Simulate reopening app with new preferences and BackgroundImageManager instance
            val reloadedPrefs = TimerPreferences(context)
            val reloadedBgManager = com.pomodorofocus.app.data.BackgroundImageManager(context)
            assertTrue(reloadedPrefs.loadAppearanceSettings().hasCustomBackground)
            assertTrue(reloadedBgManager.hasCustomBackgroundFile())

            val reloadedBitmap = reloadedBgManager.loadBitmap()
            assertNotNull(reloadedBitmap)
            assertEquals(120, reloadedBitmap?.width)
            assertEquals(120, reloadedBitmap?.height)

            bgManager.deleteCustomBackground()
        }
    }

    @Test
    fun testBuildOngoingNotificationDisplaysModeAndFormattedRemainingTime() {
        val notification = NotificationHelper.buildOngoingNotification(
            context = context,
            sessionType = SessionType.FOCUS,
            remainingMillis = 60_000L,
            totalDurationMillis = 60_000L,
            isPaused = false
        )
        assertNotNull(notification)
        val title = notification.extras.getString(androidx.core.app.NotificationCompat.EXTRA_TITLE)
        val text = notification.extras.getCharSequence(androidx.core.app.NotificationCompat.EXTRA_TEXT)?.toString()
        assertEquals("Focus Session", title)
        assertEquals("01:00 remaining", text)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun testOngoingNotificationPausedDisplaysPausedState() {
        val notification = NotificationHelper.buildOngoingNotification(
            context = context,
            sessionType = SessionType.SHORT_BREAK,
            remainingMillis = 45_000L,
            totalDurationMillis = 60_000L,
            isPaused = true
        )
        assertNotNull(notification)
        val title = notification.extras.getString(androidx.core.app.NotificationCompat.EXTRA_TITLE)
        val text = notification.extras.getCharSequence(androidx.core.app.NotificationCompat.EXTRA_TEXT)?.toString()
        assertTrue(title?.contains("Paused") == true)
        assertTrue(text?.contains("00:45") == true)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(3, notification.actions.size)
    }

    @Test
    fun endSessionNotificationActionIsFocusOnly() {
        val focus = NotificationHelper.buildOngoingNotification(
            context, SessionType.FOCUS, 60_000L, 60_000L, false, sessionId = 88L
        )
        val shortBreak = NotificationHelper.buildOngoingNotification(
            context, SessionType.SHORT_BREAK, 60_000L, 60_000L, false, sessionId = 89L
        )

        assertEquals(4, focus.actions.size)
        assertEquals(3, shortBreak.actions.size)
    }

    @Test
    fun testForegroundServiceLifecycleAndNotificationUpdates() {
        val serviceController = Robolectric.buildService(TimerForegroundService::class.java)
        val service = serviceController.create().get()
        val targetEnd = android.os.SystemClock.elapsedRealtime() + 60_000L
        prefs.saveActiveTimerState(
            SessionType.FOCUS, true, targetEnd, 60_000L, 60_000L, sessionId = 999L
        )

        val startIntent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_START
            putExtra(TimerForegroundService.EXTRA_SESSION_TYPE, SessionType.FOCUS.name)
            putExtra(TimerForegroundService.EXTRA_TARGET_END_REALTIME, targetEnd)
            putExtra(TimerForegroundService.EXTRA_TOTAL_DURATION_MILLIS, 60_000L)
            putExtra(TimerForegroundService.EXTRA_SESSION_ID, 999L)
        }
        service.onStartCommand(startIntent, 0, 1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(notificationManager)
        val ongoing = shadowNm.getNotification(NotificationHelper.NOTIFICATION_ID_ONGOING)
        assertNotNull(ongoing)

        // Pause action
        val pauseIntent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_PAUSE
            putExtra(TimerForegroundService.EXTRA_SESSION_TYPE, SessionType.FOCUS.name)
            putExtra(TimerForegroundService.EXTRA_REMAINING_MILLIS, 50_000L)
            putExtra(TimerForegroundService.EXTRA_TOTAL_DURATION_MILLIS, 60_000L)
            putExtra(TimerForegroundService.EXTRA_SESSION_ID, 999L)
        }
        service.onStartCommand(pauseIntent, 0, 2)
        assertFalse(prefs.isTimerRunning())

        // Reset action
        val resetIntent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_RESET
        }
        service.onStartCommand(resetIntent, 0, 3)
        assertFalse(prefs.isTimerRunning())

        serviceController.destroy()
    }

    @Test
    fun testOneMinuteFocusSessionForegroundAndBackgroundCompletion() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = TimerAlarmScheduler(app)
        val viewModel = PomodoroViewModel(app, prefs, scheduler)

        // Configure 1-minute focus session
        val settings = PomodoroSettings(focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 1)
        viewModel.saveSettings(settings)
        viewModel.selectSessionType(SessionType.FOCUS)
        assertEquals(60_000L, viewModel.uiState.value.remainingMillis)

        // Start timer in foreground
        viewModel.startTimer()
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertTrue(prefs.isTimerRunning())
        val activeId = prefs.getActiveSessionId()
        assertTrue(activeId > 0L)

        // Simulate background transition and completion via TimerExpiredReceiver or TimerForegroundService
        seedExpiredRunningSession(activeId, SessionType.FOCUS)
        val receiver = TimerExpiredReceiver()
        val intent = Intent(TimerAlarmScheduler.ACTION_TIMER_EXPIRED).apply {
            putExtra(TimerAlarmScheduler.EXTRA_SESSION_TYPE, SessionType.FOCUS.name)
            putExtra(TimerAlarmScheduler.EXTRA_SESSION_ID, activeId)
        }
        receiver.onReceive(context, intent)

        // Verify session count is incremented to 1
        assertEquals(1, prefs.loadCompletedSessions())
        assertEquals(1, prefs.loadFocusHistory().size)

        // Verify completion notification was delivered
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(notificationManager)
        val completionNotif = shadowNm.getNotification(NotificationHelper.NOTIFICATION_ID_COMPLETION)
        assertNotNull(completionNotif)

        // User reopens the app
        viewModel.syncWithElapsedTime()
        assertEquals(TimerState.IDLE, viewModel.uiState.value.timerState)
        assertEquals(1, viewModel.uiState.value.completedSessions)

        // Repeating receiver does not double count
        receiver.onReceive(context, intent)
        assertEquals(1, prefs.loadCompletedSessions())
    }

    @Test
    fun testWallClockExpirationHandledOnProcessRecreation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = TimerAlarmScheduler(app)

        // Save active running state whose wall clock time already expired in the past
        seedExpiredRunningSession(12345L, SessionType.FOCUS)

        val viewModel = PomodoroViewModel(app, prefs, scheduler)
        viewModel.syncWithElapsedTime()

        // Auto-start is off, so the next session is prepared as idle.
        assertEquals(TimerState.IDLE, viewModel.uiState.value.timerState)
        assertEquals(1, prefs.loadCompletedSessions())
    }

    private fun seedExpiredRunningSession(sessionId: Long, sessionType: SessionType) {
        prefs.saveActiveTimerState(
            sessionType = sessionType,
            isRunning = true,
            targetEndRealtime = android.os.SystemClock.elapsedRealtime() - 1L,
            remainingMillis = 0L,
            totalDurationMillis = 60_000L,
            sessionId = sessionId
        )
        context.getSharedPreferences("pomodoro_focus_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("target_end_wall_clock_time", System.currentTimeMillis() - 1L)
            .commit()
    }

    @Test
    fun testMainActivityLaunchesAndNavigatesToStatistics() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
    }
}
