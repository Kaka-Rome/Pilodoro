package com.pomodorofocus.app

import com.pomodorofocus.app.data.TimerPreferences
import com.pomodorofocus.app.model.SessionType

/** Seeds the persisted state required by the production completion guard. */
internal fun TimerPreferences.seedActiveSession(
    sessionId: Long,
    sessionType: SessionType,
    durationMillis: Long = 25 * 60 * 1000L
) {
    saveActiveTimerState(
        sessionType = sessionType,
        isRunning = false,
        targetEndRealtime = 0L,
        remainingMillis = durationMillis,
        totalDurationMillis = durationMillis,
        sessionId = sessionId
    )
}
