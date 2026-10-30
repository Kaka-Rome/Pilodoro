package com.pomodorofocus.app.model

import androidx.annotation.StringRes

/** One-off result of an export / import / reset action, shown to the user as a short message. */
data class DataManagementMessage(
    @StringRes val messageRes: Int,
    /** Optional number inserted into the message (e.g. how many sessions were restored). */
    val count: Int? = null
)
