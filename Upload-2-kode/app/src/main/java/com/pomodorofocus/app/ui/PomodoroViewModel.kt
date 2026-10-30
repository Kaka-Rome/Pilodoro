package com.pomodorofocus.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pomodorofocus.app.data.FocusStatisticsCalculator
import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.AppScreen
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.PomodoroUiState
import com.pomodorofocus.app.model.SessionTag
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.model.StatsPeriod
import com.pomodorofocus.app.model.TimerState
import com.pomodorofocus.app.notification.NotificationHelper
import com.pomodorofocus.app.notification.TimerAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.pomodorofocus.app.R
import com.pomodorofocus.app.data.BackupManager
import com.pomodorofocus.app.model.DataManagementMessage
import com.pomodorofocus.app.data.BackgroundImageManager
import com.pomodorofocus.app.service.TimerForegroundService
import com.pomodorofocus.app.service.TimerServiceBridge

/**
 * ViewModel managing Pomodoro timer state, background elapsed time tracking,
 * session progression, settings persistence, and local statistics.
 */
class PomodoroViewModel(
    application: Application,
    private val preferences: TimerPreferences,
    private val alarmScheduler: TimerAlarmScheduler,
    private val backgroundImageManager: BackgroundImageManager = BackgroundImageManager(application)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        TimerPreferences(application),
        TimerAlarmScheduler(application),
        BackgroundImageManager(application)
    )

    private val _uiState = MutableStateFlow(PomodoroUiState())
    val uiState: StateFlow<PomodoroUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    // Invalidates a cancelled countdown before it can finish a newer session.
    private var timerGeneration: Long = 0L
    private var targetEndRealtime: Long = 0L
    private var currentSessionId: Long = 0L
    private var statsOffset: Int = 0

    /** Timer yang "diparkir" saat pengguna berpindah mode (Focus / Short Break / Long Break). */
    private data class ParkedTimer(
        val remainingMillis: Long,
        val totalDurationMillis: Long,
        val sessionId: Long
    )
    private val parkedTimers = mutableMapOf<SessionType, ParkedTimer>()

    /** Timer state right before the last Reset, so "Undo" can bring the remaining time back. */
    private data class ResetSnapshot(
        val sessionType: SessionType,
        val remainingMillis: Long,
        val totalDurationMillis: Long,
        val sessionId: Long
    )
    private var resetSnapshot: ResetSnapshot? = null

    private val _resetEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once after each Reset that can be undone; the UI shows the "Timer reset - Undo" message. */
    val resetEvents: SharedFlow<Unit> = _resetEvents.asSharedFlow()

    init {
        loadInitialState()
        observeSessionMeta()
    }

    /**
     * Keeps the optional Focus title/tag shown in the UI in step with whichever session is current.
     * It only reads state, so the timer logic itself is untouched.
     */
    private fun observeSessionMeta() {
        viewModelScope.launch {
            _uiState
                .map { it.sessionType to it.timerState }
                .distinctUntilChanged()
                .collect { syncSessionMeta() }
        }
    }

    private fun syncSessionMeta() {
        val state = _uiState.value
        val meta = if (state.sessionType == SessionType.FOCUS &&
            (state.timerState == TimerState.RUNNING || state.timerState == TimerState.PAUSED)
        ) preferences.loadSessionMeta(currentSessionId) else null
        val title = meta?.first ?: ""
        val tag = meta?.second
        if (state.sessionTitle != title || state.sessionTag != tag) {
            _uiState.update { it.copy(sessionTitle = title, sessionTag = tag) }
        }
    }

    /**
     * Saves the optional title/tag of the Focus session in progress. Optional: an empty title with
     * no tag simply clears it. Never touches the timer itself.
     */
    fun setSessionMeta(title: String, tag: SessionTag?) {
        val state = _uiState.value
        if (state.sessionType != SessionType.FOCUS) return
        if (state.timerState != TimerState.RUNNING && state.timerState != TimerState.PAUSED) return
        if (preferences.saveSessionMeta(currentSessionId, title, tag)) syncSessionMeta()
    }

    private fun loadInitialState() {
        val settings = preferences.loadSettings()
        val savedSession = preferences.loadSavedSessionType()
        val completedSessions = preferences.loadCompletedSessions()
        val wasRunning = preferences.isTimerRunning()
        val savedRemaining = preferences.getSavedRemainingMillis()
        val savedTotal = preferences.getSavedTotalDurationMillis()
        val activeId = preferences.getActiveSessionId()
        val lastCompletedId = preferences.getLastCompletedSessionId()

        val defaultDuration = settings.getDurationMillis(savedSession)
        val appearance = preferences.loadAppearanceSettings()
        val savedTargetWall = preferences.getTargetEndWallClockTime()
        currentSessionId = activeId

        if (wasRunning) {
            val now = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()
            // Wall clock is the durable deadline; elapsedRealtime resets after device reboot.
            val remaining = if (savedTargetWall > 0L) savedTargetWall - nowWall else 0L

            if (savedTargetWall > 0L && remaining <= 0L) {
                // Timer finished while process was away
                preferences.markSessionCompleted(activeId, savedSession)
                if (!restoreNextSessionAfterCompletion(savedSession, activeId, settings, appearance)) {
                    val updatedCompleted = preferences.loadCompletedSessions()
                    _uiState.update {
                        it.copy(
                            sessionType = savedSession,
                            timerState = TimerState.COMPLETED,
                            remainingMillis = 0L,
                            totalDurationMillis = if (savedTotal > 0L) savedTotal else defaultDuration,
                            completedSessions = updatedCompleted,
                            settings = settings,
                            appearanceSettings = appearance
                        )
                    }
                }
            } else if (remaining > 0L) {
                // Restore running timer
                targetEndRealtime = now + remaining
                val total = if (savedTotal > 0L) savedTotal else defaultDuration
                preferences.saveActiveTimerState(
                    sessionType = savedSession,
                    isRunning = true,
                    targetEndRealtime = targetEndRealtime,
                    remainingMillis = remaining,
                    totalDurationMillis = total,
                    sessionId = activeId
                )
                _uiState.update {
                    it.copy(
                        sessionType = savedSession,
                        timerState = TimerState.RUNNING,
                        remainingMillis = remaining,
                        totalDurationMillis = total,
                        completedSessions = completedSessions,
                        settings = settings,
                        appearanceSettings = appearance
                    )
                }
                TimerForegroundService.startService(
                    getApplication(), savedSession, targetEndRealtime, total, activeId
                )
                startCountdownLoop()
            } else if (savedRemaining > 0L && savedTotal > 0L) {
                // A running flag without a trustworthy deadline is not proof of completion.
                preferences.saveActiveTimerState(
                    sessionType = savedSession,
                    isRunning = false,
                    targetEndRealtime = 0L,
                    remainingMillis = savedRemaining,
                    totalDurationMillis = savedTotal,
                    sessionId = activeId
                )
                _uiState.update {
                    it.copy(
                        sessionType = savedSession,
                        timerState = TimerState.PAUSED,
                        remainingMillis = savedRemaining,
                        totalDurationMillis = savedTotal,
                        completedSessions = completedSessions,
                        settings = settings,
                        appearanceSettings = appearance
                    )
                }
            } else {
                preferences.clearActiveTimerState()
                _uiState.update {
                    it.copy(
                        sessionType = savedSession,
                        timerState = TimerState.IDLE,
                        remainingMillis = defaultDuration,
                        totalDurationMillis = defaultDuration,
                        completedSessions = completedSessions,
                        settings = settings,
                        appearanceSettings = appearance
                    )
                }
            }
        } else if (activeId != 0L && activeId == lastCompletedId) {
            // Already marked completed by receiver while app was closed
            if (!restoreNextSessionAfterCompletion(savedSession, activeId, settings, appearance)) {
                _uiState.update {
                    it.copy(
                        sessionType = savedSession,
                        timerState = TimerState.COMPLETED,
                        remainingMillis = 0L,
                        totalDurationMillis = if (savedTotal > 0L) savedTotal else defaultDuration,
                        completedSessions = completedSessions,
                        settings = settings,
                        appearanceSettings = appearance
                    )
                }
            }
        } else if (savedRemaining > 0L && savedTotal > 0L) {
            // Paused state
            _uiState.update {
                it.copy(
                    sessionType = savedSession,
                    timerState = TimerState.PAUSED,
                    remainingMillis = savedRemaining,
                    totalDurationMillis = savedTotal,
                    completedSessions = completedSessions,
                    settings = settings,
                    appearanceSettings = appearance
                )
            }
        } else {
            // Fresh idle state
            _uiState.update {
                it.copy(
                    sessionType = savedSession,
                    timerState = TimerState.IDLE,
                    remainingMillis = defaultDuration,
                    totalDurationMillis = defaultDuration,
                    completedSessions = completedSessions,
                    settings = settings,
                    appearanceSettings = appearance
                )
            }
        }

        if (appearance.hasCustomBackground) {
            viewModelScope.launch {
                val bitmap = backgroundImageManager.loadBitmap()
                if (bitmap != null) {
                    _uiState.update { it.copy(customBackgroundBitmap = bitmap) }
                } else {
                    // Stored file was deleted or cannot be decoded - fall back gracefully
                    preferences.setCustomBackgroundEnabled(false)
                    _uiState.update {
                        it.copy(
                            appearanceSettings = it.appearanceSettings.copy(hasCustomBackground = false),
                            customBackgroundBitmap = null
                        )
                    }
                }
            }
        }

        refreshStatistics()

        // Synchronize with background service when notification actions or events fire
        TimerServiceBridge.registerListener {
            syncWithElapsedTime()
        }
    }

    private fun restoreNextSessionAfterCompletion(
        completedType: SessionType,
        completedId: Long,
        settings: PomodoroSettings,
        appearance: com.pomodorofocus.app.model.AppearanceSettings
    ): Boolean {
        val next = com.pomodorofocus.app.service.SessionAutoAdvance.advanceAfterCompletion(
            getApplication(), preferences, completedType, completedId
        )
        val nextType = next?.sessionType ?: preferences.loadSavedSessionType()
        val isRunning = preferences.isTimerRunning()
        val activeId = preferences.getActiveSessionId()
        val wasAdvanced = next != null || (
            nextType != completedType &&
                (isRunning || activeId == 0L) &&
                preferences.getLastCompletedSessionId() == completedId
            )
        if (!wasAdvanced) return false

        val total = settings.getDurationMillis(nextType)
        val target = if (isRunning) preferences.getTargetEndRealtime() else 0L
        val remaining = if (isRunning) {
            (target - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else total
        currentSessionId = if (isRunning) activeId else 0L
        targetEndRealtime = target
        _uiState.update {
            it.copy(
                sessionType = nextType,
                timerState = if (isRunning) TimerState.RUNNING else TimerState.IDLE,
                remainingMillis = remaining,
                totalDurationMillis = total,
                completedSessions = preferences.loadCompletedSessions(),
                settings = settings,
                appearanceSettings = appearance
            )
        }
        if (isRunning) {
            if (next?.isRunning == true) {
                try {
                    TimerForegroundService.startService(
                        context = getApplication(),
                        sessionType = nextType,
                        targetEndRealtime = target,
                        totalDurationMillis = total,
                        sessionId = activeId
                    )
                } catch (_: Exception) {
                    // Session state and alarm are already saved; the UI can still resume it.
                }
            }
            startCountdownLoop()
        }
        return true
    }

    fun startTimer() {
        // Ignore duplicate taps or stale callbacks; restarting a running timer corrupts its deadline.
        if (_uiState.value.timerState == TimerState.RUNNING) return
        timerGeneration++
        resetSnapshot = null
        val current = _uiState.value
        val total = if (current.timerState == TimerState.PAUSED) {
            current.totalDurationMillis
        } else {
            current.settings.getDurationMillis(current.sessionType)
        }
        val remaining = if (current.timerState == TimerState.PAUSED && current.remainingMillis > 0L) {
            current.remainingMillis
        } else {
            total
        }

        if (current.timerState != TimerState.PAUSED || currentSessionId == 0L) {
            currentSessionId = preferences.allocateSessionId()
        }
        targetEndRealtime = SystemClock.elapsedRealtime() + remaining

        _uiState.update {
            it.copy(
                timerState = TimerState.RUNNING,
                remainingMillis = remaining,
                totalDurationMillis = total
            )
        }

        // Schedule background alarm so device alerts even when asleep
        alarmScheduler.scheduleAlarm(current.sessionType, targetEndRealtime, currentSessionId)

        // Save active state to device storage
        preferences.saveActiveTimerState(
            sessionType = current.sessionType,
            isRunning = true,
            targetEndRealtime = targetEndRealtime,
            remainingMillis = remaining,
            totalDurationMillis = total,
            sessionId = currentSessionId
        )

        // Launch persistent foreground service for active notification shade & background execution
        TimerForegroundService.startService(
            context = getApplication(),
            sessionType = current.sessionType,
            targetEndRealtime = targetEndRealtime,
            totalDurationMillis = total,
            sessionId = currentSessionId
        )

        startCountdownLoop()
    }

    fun pauseTimer() {
        timerGeneration++
        timerJob?.cancel()
        timerJob = null

        val now = SystemClock.elapsedRealtime()
        val remaining = (targetEndRealtime - now).coerceAtLeast(0L)
        if (remaining <= 0L) {
            onSessionFinished()
            return
        }

        alarmScheduler.cancelAlarm()

        val current = _uiState.value
        _uiState.update {
            it.copy(
                timerState = TimerState.PAUSED,
                remainingMillis = remaining
            )
        }

        preferences.saveActiveTimerState(
            sessionType = current.sessionType,
            isRunning = false,
            targetEndRealtime = 0L,
            remainingMillis = remaining,
            totalDurationMillis = current.totalDurationMillis,
            sessionId = currentSessionId
        )

        // Notify foreground service to pause and update notification to paused status
        TimerForegroundService.pauseService(
            context = getApplication(),
            sessionType = current.sessionType,
            remainingMillis = remaining,
            totalDurationMillis = current.totalDurationMillis,
            sessionId = currentSessionId
        )
    }

    fun resumeTimer() {
        startTimer()
    }

    fun resetTimer() {
        timerGeneration++
        val before = _uiState.value
        val remainingBefore = when (before.timerState) {
            TimerState.RUNNING -> (targetEndRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            TimerState.PAUSED -> before.remainingMillis
            else -> 0L
        }
        val snapshot = if (remainingBefore > 0L) {
            ResetSnapshot(before.sessionType, remainingBefore, before.totalDurationMillis, currentSessionId)
        } else null

        timerJob?.cancel()
        timerJob = null
        alarmScheduler.cancelAlarm()
        TimerForegroundService.stopService(getApplication())
        NotificationHelper.cancelNotification(getApplication())
        currentSessionId = 0L

        val current = _uiState.value
        parkedTimers.remove(current.sessionType)
        val duration = current.settings.getDurationMillis(current.sessionType)

        _uiState.update {
            it.copy(
                timerState = TimerState.IDLE,
                remainingMillis = duration,
                totalDurationMillis = duration
            )
        }

        // Saved as a fresh idle timer (no leftover remaining time), so reopening the app shows Ready, not Paused.
        preferences.clearActiveTimerState()
        preferences.saveNextSessionAsIdle(current.sessionType, duration)

        resetSnapshot = snapshot
        if (snapshot != null) _resetEvents.tryEmit(Unit)
    }

    /**
     * Brings back the time that was left before the last Reset (as a paused timer).
     * Does nothing if anything else happened to the timer since the reset.
     */
    fun undoResetTimer() {
        val snapshot = resetSnapshot ?: return
        resetSnapshot = null
        val current = _uiState.value
        if (current.timerState != TimerState.IDLE || current.sessionType != snapshot.sessionType) return

        currentSessionId = snapshot.sessionId
        _uiState.update {
            it.copy(
                timerState = TimerState.PAUSED,
                remainingMillis = snapshot.remainingMillis,
                totalDurationMillis = snapshot.totalDurationMillis
            )
        }
        preferences.saveActiveTimerState(
            sessionType = snapshot.sessionType,
            isRunning = false,
            targetEndRealtime = 0L,
            remainingMillis = snapshot.remainingMillis,
            totalDurationMillis = snapshot.totalDurationMillis,
            sessionId = snapshot.sessionId
        )
    }

    /**
     * Ends the current session early.
     * - [markCompleted] = true: the session counts as completed (Focus time actually spent goes into stats)
     *   and the timer moves on like a normal finish, following the auto-start settings.
     * - [markCompleted] = false: the session is cancelled, nothing is added to stats, and the timer
     *   goes back to the start of the same session.
     */
    fun endSession(markCompleted: Boolean) {
        val current = _uiState.value
        if (current.sessionType != SessionType.FOCUS) return
        if (current.timerState != TimerState.PAUSED && current.timerState != TimerState.RUNNING) return
        timerGeneration++
        resetSnapshot = null

        timerJob?.cancel()
        timerJob = null
        alarmScheduler.cancelAlarm()
        TimerForegroundService.stopService(getApplication())
        NotificationHelper.cancelNotification(getApplication())

        val remaining = if (current.timerState == TimerState.RUNNING) {
            (targetEndRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else current.remainingMillis
        val elapsed = (current.totalDurationMillis - remaining).coerceAtLeast(0L)

        if (!markCompleted) {
            parkedTimers.remove(current.sessionType)
            currentSessionId = 0L
            val duration = current.settings.getDurationMillis(current.sessionType)
            _uiState.update {
                it.copy(
                    timerState = TimerState.IDLE,
                    remainingMillis = duration,
                    totalDurationMillis = duration
                )
            }
            preferences.clearActiveTimerState()
            preferences.saveNextSessionAsIdle(current.sessionType, duration)
            return
        }

        parkedTimers.remove(current.sessionType)
        if (currentSessionId == 0L) currentSessionId = preferences.allocateSessionId()
        val sessionId = currentSessionId
        // Make sure the saved state points at this session so completion and auto-advance can find it.
        preferences.saveActiveTimerState(
            sessionType = current.sessionType,
            isRunning = false,
            targetEndRealtime = 0L,
            remainingMillis = remaining,
            totalDurationMillis = current.totalDurationMillis,
            sessionId = sessionId
        )
        // Records the Focus time actually spent (not the full planned length). No "finished" alert: the user ended it.
        preferences.markSessionCompleted(sessionId, current.sessionType, durationMillis = elapsed)

        if (!restoreNextSessionAfterCompletion(current.sessionType, sessionId, current.settings, current.appearanceSettings)) {
            _uiState.update {
                it.copy(
                    timerState = TimerState.COMPLETED,
                    remainingMillis = 0L,
                    completedSessions = preferences.loadCompletedSessions()
                )
            }
        }
        refreshStatistics()
    }

    fun selectSessionType(type: SessionType) {
        if (_uiState.value.sessionType == type && _uiState.value.timerState != TimerState.COMPLETED) {
            return
        }
        resetSnapshot = null
        timerGeneration++

        val current = _uiState.value

        // Simpan (parkir) timer mode saat ini jika sedang berjalan atau di-pause,
        // supaya sisa waktunya tidak hilang ketika pindah mode.
        when (current.timerState) {
            TimerState.RUNNING -> {
                val remaining = (targetEndRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (remaining > 0L) {
                    parkedTimers[current.sessionType] =
                        ParkedTimer(remaining, current.totalDurationMillis, currentSessionId)
                }
            }
            TimerState.PAUSED -> {
                if (current.remainingMillis > 0L) {
                    parkedTimers[current.sessionType] =
                        ParkedTimer(current.remainingMillis, current.totalDurationMillis, currentSessionId)
                }
            }
            else -> parkedTimers.remove(current.sessionType)
        }

        timerJob?.cancel()
        timerJob = null
        alarmScheduler.cancelAlarm()
        TimerForegroundService.stopService(getApplication())
        currentSessionId = 0L

        val parked = parkedTimers.remove(type)

        preferences.clearActiveTimerState()

        if (parked != null) {
            // Kembali ke mode yang sebelumnya dijeda: tampilkan sisa waktunya (status Paused)
            currentSessionId = parked.sessionId
            _uiState.update {
                it.copy(
                    sessionType = type,
                    timerState = TimerState.PAUSED,
                    remainingMillis = parked.remainingMillis,
                    totalDurationMillis = parked.totalDurationMillis
                )
            }
            preferences.saveActiveTimerState(
                sessionType = type,
                isRunning = false,
                targetEndRealtime = 0L,
                remainingMillis = parked.remainingMillis,
                totalDurationMillis = parked.totalDurationMillis,
                sessionId = parked.sessionId
            )
        } else {
            val duration = current.settings.getDurationMillis(type)
            _uiState.update {
                it.copy(
                    sessionType = type,
                    timerState = TimerState.IDLE,
                    remainingMillis = duration,
                    totalDurationMillis = duration
                )
            }
            preferences.saveNextSessionAsIdle(type, duration)
        }
    }

    /**
     * Next button: leaves the current session (nothing is added to stats) and moves to the session shown
     * as "Next" under the controls. Auto-start settings decide whether that session starts right away.
     */
    fun skipToNextSession() {
        val current = _uiState.value
        if (current.timerState != TimerState.PAUSED) return
        resetSnapshot = null
        timerGeneration++
        timerJob?.cancel()
        timerJob = null
        alarmScheduler.cancelAlarm()
        currentSessionId = 0L

        val nextType = current.sessionType.upcoming(
            current.completedSessions,
            current.settings.sessionsBeforeLongBreak
        )
        val duration = current.settings.getDurationMillis(nextType)

        _uiState.update {
            it.copy(
                sessionType = nextType,
                timerState = TimerState.IDLE,
                remainingMillis = duration,
                totalDurationMillis = duration
            )
        }

        val autoStart = if (nextType == SessionType.FOCUS) {
            current.settings.autoStartFocus
        } else {
            current.settings.autoStartBreaks
        }
        preferences.clearActiveTimerState()
        preferences.saveNextSessionAsIdle(nextType, duration)
        if (!autoStart) TimerForegroundService.stopService(getApplication())
        if (autoStart) startTimer()
    }

    /**
     * Called when the app is brought back to the foreground (e.g. from onResume).
     * Synchronizes the UI with actual elapsed wall-clock / uptime time.
     */
    fun syncWithElapsedTime() {
        val current = _uiState.value
        val activeId = preferences.getActiveSessionId()
        val lastCompletedId = preferences.getLastCompletedSessionId()
        val currentCompletedCount = preferences.loadCompletedSessions()
        val isRunning = preferences.isTimerRunning()
        val targetWall = preferences.getTargetEndWallClockTime()
        val savedRemaining = preferences.getSavedRemainingMillis()

        // 1. If background service or alarm receiver already marked this session as completed
        if (activeId != 0L && activeId == lastCompletedId) {
            timerJob?.cancel()
            timerJob = null
            TimerForegroundService.stopService(getApplication())
            _uiState.update {
                it.copy(
                    timerState = TimerState.COMPLETED,
                    remainingMillis = 0L,
                    completedSessions = currentCompletedCount
                )
            }
            refreshStatistics()
            return
        }

        // 2. If timer is currently running, calculate remaining time
        if (isRunning) {
            val nowRealtime = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()
            val remainingRealtime = if (targetWall > 0L) targetWall - nowWall else 0L

            if (targetWall > 0L && remainingRealtime <= 0L) {
                currentSessionId = activeId
                targetEndRealtime = SystemClock.elapsedRealtime()
                _uiState.update {
                    it.copy(
                        sessionType = preferences.loadSavedSessionType(),
                        timerState = TimerState.RUNNING,
                        completedSessions = currentCompletedCount
                    )
                }
                onSessionFinished()
            } else if (remainingRealtime > 0L) {
                targetEndRealtime = nowRealtime + remainingRealtime
                val sessionChanged = activeId != currentSessionId
                if (sessionChanged) {
                    timerGeneration++
                    timerJob?.cancel()
                    timerJob = null
                }
                currentSessionId = activeId
                val savedTotal = preferences.getSavedTotalDurationMillis()
                val total = if (savedTotal > 0L) savedTotal else current.totalDurationMillis
                preferences.saveActiveTimerState(
                    preferences.loadSavedSessionType(), true, targetEndRealtime,
                    remainingRealtime, total, activeId
                )
                _uiState.update {
                    it.copy(
                        sessionType = preferences.loadSavedSessionType(),
                        timerState = TimerState.RUNNING,
                        remainingMillis = remainingRealtime,
                        totalDurationMillis = total,
                        completedSessions = currentCompletedCount
                    )
                }
                if (sessionChanged || timerJob == null || timerJob?.isActive != true) {
                    startCountdownLoop()
                }
            } else {
                // Missing deadlines are recovered as paused; they are never treated as natural completion.
                val remaining = savedRemaining.takeIf { it > 0L } ?: current.remainingMillis.coerceAtLeast(0L)
                timerGeneration++
                timerJob?.cancel()
                timerJob = null
                alarmScheduler.cancelAlarm()
                TimerForegroundService.stopService(getApplication())
                preferences.saveActiveTimerState(
                    preferences.loadSavedSessionType(), false, 0L, remaining,
                    preferences.getSavedTotalDurationMillis().takeIf { it > 0L } ?: current.totalDurationMillis,
                    activeId
                )
                _uiState.update {
                    it.copy(timerState = TimerState.PAUSED, remainingMillis = remaining)
                }
            }
        } else if (activeId != 0L && savedRemaining > 0L && current.timerState != TimerState.COMPLETED) {
            // Paused state (e.g. from notification action); cancel any stale running loop.
            timerGeneration++
            timerJob?.cancel()
            timerJob = null
            currentSessionId = activeId
            targetEndRealtime = 0L
            val savedType = preferences.loadSavedSessionType()
            val savedTotal = preferences.getSavedTotalDurationMillis().takeIf { it > 0L }
                ?: current.settings.getDurationMillis(savedType)
            _uiState.update {
                it.copy(
                    sessionType = savedType,
                    timerState = TimerState.PAUSED,
                    remainingMillis = savedRemaining,
                    totalDurationMillis = savedTotal,
                    completedSessions = currentCompletedCount
                )
            }
        } else {
            val savedSession = preferences.loadSavedSessionType()
            timerGeneration++
            timerJob?.cancel()
            timerJob = null
            currentSessionId = 0L
            targetEndRealtime = 0L
            val duration = current.settings.getDurationMillis(savedSession)
            _uiState.update {
                it.copy(
                    sessionType = savedSession,
                    timerState = TimerState.IDLE,
                    remainingMillis = duration,
                    totalDurationMillis = duration,
                    completedSessions = currentCompletedCount
                )
            }
        }
        refreshStatistics()
    }

    private fun startCountdownLoop() {
        timerJob?.cancel()
        val generation = ++timerGeneration
        val sessionId = currentSessionId
        timerJob = viewModelScope.launch {
            while (true) {
                if (generation != timerGeneration || sessionId != currentSessionId) break
                val now = SystemClock.elapsedRealtime()
                val remaining = targetEndRealtime - now
                if (remaining <= 0L) {
                    onSessionFinished(sessionId, generation)
                    break
                }
                _uiState.update { it.copy(remainingMillis = remaining) }
                delay(200L)
            }
        }
    }

    private fun onSessionFinished(expectedSessionId: Long? = null, expectedGeneration: Long? = null) {
        if (expectedGeneration != null && expectedGeneration != timerGeneration) return
        if (expectedSessionId != null && expectedSessionId != currentSessionId) return
        if (expectedGeneration != null && _uiState.value.timerState != TimerState.RUNNING) return
        val activeId = preferences.getActiveSessionId()
        if (activeId != currentSessionId || !preferences.isTimerRunning()) {
            timerGeneration++
            timerJob?.cancel()
            timerJob = null
            syncWithElapsedTime()
            return
        }
        val wallDeadline = preferences.getTargetEndWallClockTime()
        if (wallDeadline > System.currentTimeMillis()) {
            targetEndRealtime = SystemClock.elapsedRealtime() + (wallDeadline - System.currentTimeMillis())
            startCountdownLoop()
            return
        }
        timerGeneration++
        timerJob?.cancel()
        timerJob = null

        val current = _uiState.value
        val finishedId = currentSessionId
        val justMarked = preferences.markSessionCompleted(finishedId, current.sessionType)

        // Only show notification if it was not already shown by TimerExpiredReceiver or TimerForegroundService
        if (justMarked) {
            NotificationHelper.showSessionFinishedNotification(getApplication(), current.sessionType)
        }

        // Selalu pindah ke sesi berikutnya; auto-start hanya mengatur apakah timer langsung berjalan.
        if (restoreNextSessionAfterCompletion(current.sessionType, finishedId, current.settings, current.appearanceSettings)) {
            refreshStatistics()
            return
        }

        alarmScheduler.cancelAlarm()
        TimerForegroundService.stopService(getApplication())

        val updatedCompleted = preferences.loadCompletedSessions()

        _uiState.update {
            it.copy(
                timerState = TimerState.COMPLETED,
                remainingMillis = 0L,
                completedSessions = updatedCompleted
            )
        }

        refreshStatistics()
    }

    fun navigateTo(screen: AppScreen) {
        if (screen == AppScreen.STATISTICS) {
            refreshStatistics()
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setStatsPeriod(period: StatsPeriod) {
        statsOffset = 0
        _uiState.update { it.copy(statsPeriod = period) }
        refreshStatistics()
    }

    fun navigateStatsPeriod(direction: Int) {
        if (direction < 0) {
            statsOffset -= 1
        } else if (direction > 0) {
            statsOffset = (statsOffset + 1).coerceAtMost(0)
        }
        refreshStatistics()
    }

    fun refreshStatistics() {
        val currentPeriod = _uiState.value.statsPeriod
        val records = preferences.loadFocusHistory()
        val data = FocusStatisticsCalculator.calculate(
            period = currentPeriod,
            offset = statsOffset,
            records = records
        )
        val todayCount = FocusStatisticsCalculator.countSessionsToday(records)
        _uiState.update {
            it.copy(statsPeriodData = data, todayFocusSessions = todayCount)
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun saveSettings(newSettings: PomodoroSettings) {
        preferences.saveSettings(newSettings)
        val current = _uiState.value

        // If timer is not running, adjust current remaining time to new duration
        val newDuration = newSettings.getDurationMillis(current.sessionType)
        val shouldUpdateDuration = current.timerState == TimerState.IDLE || current.timerState == TimerState.COMPLETED

        _uiState.update {
            it.copy(
                settings = newSettings,
                remainingMillis = if (shouldUpdateDuration) newDuration else it.remainingMillis,
                totalDurationMillis = if (shouldUpdateDuration) newDuration else it.totalDurationMillis
            )
        }
    }

    /**
     * Re-checks whether the user granted Do Not Disturb access (they grant it in system settings,
     * outside the app) and re-aligns the system DND state with the saved timer state.
     */
    fun refreshDndAccess() {
        val granted = com.pomodorofocus.app.data.FocusDndController.isAccessGranted(getApplication())
        com.pomodorofocus.app.data.FocusDndController.sync(getApplication(), preferences)
        _uiState.update { it.copy(isDndAccessGranted = granted) }
    }

    fun setNotificationPermission(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasNotificationPermission = granted,
                showPermissionRationale = !granted
            )
        }
    }

    fun dismissPermissionRationale() {
        _uiState.update { it.copy(showPermissionRationale = false) }
    }

    fun saveAppearanceSettings(newSettings: com.pomodorofocus.app.model.AppearanceSettings) {
        preferences.saveAppearanceSettings(newSettings)
        if (newSettings.hasCustomBackground && _uiState.value.customBackgroundBitmap == null) {
            viewModelScope.launch {
                val bitmap = backgroundImageManager.loadBitmap()
                _uiState.update {
                    it.copy(appearanceSettings = newSettings, customBackgroundBitmap = bitmap)
                }
            }
        } else {
            _uiState.update { it.copy(appearanceSettings = newSettings) }
        }
    }

    fun toggleMinimalMode() {
        val currentMinimal = _uiState.value.appearanceSettings.isMinimalMode
        val newMinimal = !currentMinimal
        preferences.setMinimalMode(newMinimal)
        _uiState.update {
            it.copy(appearanceSettings = it.appearanceSettings.copy(isMinimalMode = newMinimal))
        }
    }

    fun setMinimalMode(isMinimal: Boolean) {
        preferences.setMinimalMode(isMinimal)
        _uiState.update {
            it.copy(appearanceSettings = it.appearanceSettings.copy(isMinimalMode = isMinimal))
        }
    }

    /**
     * Safely copies and decodes an image chosen from the system picker.
     * Preserves active timer state, session counter, and historical records.
     */
    fun setCustomBackgroundUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val success = backgroundImageManager.saveImageFromUri(uri)
            if (success) {
                val bitmap = backgroundImageManager.loadBitmap()
                if (bitmap != null) {
                    preferences.setCustomBackgroundEnabled(true)
                    _uiState.update {
                        it.copy(
                            appearanceSettings = it.appearanceSettings.copy(hasCustomBackground = true),
                            customBackgroundBitmap = bitmap,
                            backgroundErrorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(backgroundErrorMessage = "Could not decode selected image.")
                    }
                }
            } else {
                _uiState.update {
                    it.copy(backgroundErrorMessage = "Unable to read or copy the selected image.")
                }
            }
        }
    }

    /** Gambar yang sedang diedit di editor wallpaper (null = editor tertutup). */
    private val _editingBackground = MutableStateFlow<android.graphics.Bitmap?>(null)
    val editingBackground: StateFlow<android.graphics.Bitmap?> = _editingBackground.asStateFlow()

    /**
     * Langkah pertama memilih wallpaper: muat gambar dari galeri lalu buka editor
     * (zoom, geser, putar, crop). Wallpaper lama tidak berubah sampai pengguna menekan Save.
     */
    fun startBackgroundEdit(uri: android.net.Uri) {
        viewModelScope.launch {
            val bitmap = backgroundImageManager.decodeForEditing(uri)
            if (bitmap != null) {
                _editingBackground.value = bitmap
            } else {
                _uiState.update {
                    it.copy(backgroundErrorMessage = "Unable to read or copy the selected image.")
                }
            }
        }
    }

    fun cancelBackgroundEdit() {
        _editingBackground.value = null
    }

    /** Menyimpan hasil crop dari editor sebagai wallpaper baru. */
    fun confirmBackgroundEdit(cropped: android.graphics.Bitmap) {
        _editingBackground.value = null
        setCustomBackgroundBitmap(cropped)
    }

    /**
     * Allows direct setting of a Bitmap (useful for tests or programmatic backgrounds).
     */
    fun setCustomBackgroundBitmap(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            val success = backgroundImageManager.saveBitmapDirectly(bitmap)
            if (success) {
                preferences.setCustomBackgroundEnabled(true)
                _uiState.update {
                    it.copy(
                        appearanceSettings = it.appearanceSettings.copy(hasCustomBackground = true),
                        customBackgroundBitmap = bitmap,
                        backgroundErrorMessage = null
                    )
                }
            }
        }
    }

    /**
     * Removes the custom background image, deletes the app-private file,
     * and returns to the default theme background.
     */
    fun removeCustomBackground() {
        viewModelScope.launch {
            backgroundImageManager.deleteCustomBackground()
            preferences.setCustomBackgroundEnabled(false)
            _uiState.update {
                it.copy(
                    appearanceSettings = it.appearanceSettings.copy(hasCustomBackground = false),
                    customBackgroundBitmap = null,
                    backgroundErrorMessage = null
                )
            }
        }
    }

    /**
     * Adjusts the background dimming percentage (0% to 90%).
     */
    fun setBackgroundDimPercent(percent: Int) {
        val clamped = percent.coerceIn(0, 90)
        preferences.setBackgroundDimPercent(clamped)
        _uiState.update {
            it.copy(appearanceSettings = it.appearanceSettings.copy(backgroundDimPercent = clamped))
        }
    }

    fun clearBackgroundError() {
        _uiState.update { it.copy(backgroundErrorMessage = null) }
    }

    // ---------------------------------------------------------------------------------------
    // Data management: backup export / import and history reset (all local, no network)
    // ---------------------------------------------------------------------------------------

    private val _dataMessages = MutableSharedFlow<DataManagementMessage>(extraBufferCapacity = 4)

    /** Short result messages for export / import / reset, to be shown once (e.g. as a Toast). */
    val dataMessages: SharedFlow<DataManagementMessage> = _dataMessages.asSharedFlow()

    /** Writes settings + focus history as JSON to the file the user chose in the system picker. */
    fun exportBackup(uri: android.net.Uri) {
        val state = _uiState.value
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val json = BackupManager.toJson(
                        settings = state.settings,
                        appearance = state.appearanceSettings,
                        history = preferences.loadFocusHistory()
                    )
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                        true
                    } ?: false
                } catch (_: Exception) {
                    false
                }
            }
            _dataMessages.tryEmit(
                DataManagementMessage(
                    if (success) R.string.data_export_success else R.string.data_export_failed
                )
            )
        }
    }

    /**
     * Restores a backup file. The file is fully validated first; nothing is changed if it is
     * invalid. The active timer, wallpaper and minimal-mode flag are never touched.
     */
    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        BackupManager.readLimited(it)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (text == null) {
                _dataMessages.tryEmit(DataManagementMessage(R.string.data_import_failed))
                return@launch
            }

            when (val result = BackupManager.parse(text)) {
                is BackupManager.ParseResult.Failure -> {
                    val res = when (result.reason) {
                        BackupManager.FailureReason.NOT_A_BACKUP -> R.string.data_import_not_backup
                        BackupManager.FailureReason.UNSUPPORTED_VERSION -> R.string.data_import_unsupported
                        BackupManager.FailureReason.CORRUPT -> R.string.data_import_failed
                    }
                    _dataMessages.tryEmit(DataManagementMessage(res))
                }
                is BackupManager.ParseResult.Success -> {
                    val data = result.data
                    preferences.replaceFocusHistory(data.history)
                    saveSettings(data.settings)
                    saveAppearanceSettings(
                        _uiState.value.appearanceSettings.copy(
                            themeMode = data.themeMode,
                            colorPalette = data.colorPalette,
                            appFont = data.appFont,
                            backgroundDimPercent = data.backgroundDimPercent,
                            timerFont = data.timerFont
                        )
                    )
                    // Close Settings so it reopens with the restored values instead of stale ones.
                    _uiState.update { it.copy(isSettingsOpen = false) }
                    refreshStatistics()
                    _dataMessages.tryEmit(
                        DataManagementMessage(R.string.data_import_success, data.history.size)
                    )
                }
            }
        }
    }

    /** Deletes every recorded focus session (statistics) and restarts the Long Break cycle count. */
    fun resetFocusHistory() {
        preferences.resetFocusStatistics()
        _uiState.update { it.copy(completedSessions = 0) }
        refreshStatistics()
        _dataMessages.tryEmit(DataManagementMessage(R.string.data_reset_done))
    }

    override fun onCleared() {
        super.onCleared()
        TimerServiceBridge.unregisterListener()
        timerJob?.cancel()
    }
}
