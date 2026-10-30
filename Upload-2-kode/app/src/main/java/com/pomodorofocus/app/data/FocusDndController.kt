package com.pomodorofocus.app.data

import android.app.NotificationManager
import android.content.Context
import com.pomodorofocus.app.model.SessionType

/**
 * Optional "Do Not Disturb during Focus" helper. Fully local: it only talks to the system
 * NotificationManager, and only works after the user grants "Do Not Disturb access" to the app.
 *
 * The desired state is always derived from the saved timer state (setting on + access granted +
 * a Focus session actually running), so [sync] is idempotent and safe to call from anywhere:
 * the app, the foreground service and the alarm receiver. It never overrides a Do Not Disturb mode
 * the user already turned on, and it only restores the mode it set itself.
 */
object FocusDndController {

    private const val STATE_PREFS = "pomodoro_focus_dnd_state"
    private const val KEY_APPLIED = "applied"
    private const val KEY_PREVIOUS_FILTER = "previous_filter"
    private const val KEY_APPLIED_FILTER = "applied_filter"

    /** Priority only: respects the user's own Do Not Disturb exceptions (alarms, starred contacts...). */
    private const val FOCUS_FILTER = NotificationManager.INTERRUPTION_FILTER_PRIORITY

    fun isAccessGranted(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    @Synchronized
    fun sync(context: Context, prefs: TimerPreferences) {
        val appContext = context.applicationContext ?: context
        val state = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val applied = state.getBoolean(KEY_APPLIED, false)
        val enabled = prefs.loadSettings().dndDuringFocus
        // Cheap exit for the common case: feature unused and nothing to restore.
        if (!enabled && !applied) return

        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        val granted = nm.isNotificationPolicyAccessGranted

        val shouldBeOn = enabled &&
            granted &&
            prefs.isTimerRunning() &&
            prefs.loadSavedSessionType() == SessionType.FOCUS

        try {
            if (shouldBeOn && !applied) {
                val current = nm.currentInterruptionFilter
                // Only take over when Do Not Disturb is currently off; never override the user's own mode.
                if (current == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    nm.setInterruptionFilter(FOCUS_FILTER)
                    state.edit()
                        .putBoolean(KEY_APPLIED, true)
                        .putInt(KEY_PREVIOUS_FILTER, current)
                        .putInt(KEY_APPLIED_FILTER, FOCUS_FILTER)
                        .apply()
                }
            } else if (!shouldBeOn && applied) {
                if (granted) {
                    val previous = state.getInt(KEY_PREVIOUS_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
                    val appliedFilter = state.getInt(KEY_APPLIED_FILTER, FOCUS_FILTER)
                    // If the user changed the mode themselves in the meantime, leave it alone.
                    if (nm.currentInterruptionFilter == appliedFilter) {
                        nm.setInterruptionFilter(previous)
                    }
                }
                state.edit().putBoolean(KEY_APPLIED, false).apply()
            }
        } catch (_: SecurityException) {
            // Access was revoked between the check and the call; nothing more we can do.
            state.edit().putBoolean(KEY_APPLIED, false).apply()
        }
    }
}
