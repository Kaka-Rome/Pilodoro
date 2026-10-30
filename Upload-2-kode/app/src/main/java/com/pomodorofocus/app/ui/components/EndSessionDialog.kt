package com.pomodorofocus.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pomodorofocus.app.R

/**
 * Confirmation for "End Session". With "Mark as completed" ticked the session counts (Focus time goes
 * into stats); unticked, the session is cancelled and nothing is saved to stats.
 */
@Composable
fun EndSessionDialog(
    isFocusSession: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (markCompleted: Boolean) -> Unit
) {
    var markCompleted by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.end_session_title)) },
        text = {
            Column {
                Text(stringResource(R.string.end_session_message))
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = markCompleted,
                            role = Role.Checkbox,
                            onValueChange = { markCompleted = it }
                        )
                        .testTag("end_session_mark_completed_row")
                ) {
                    Checkbox(
                        checked = markCompleted,
                        onCheckedChange = null,
                        modifier = Modifier.testTag("end_session_mark_completed_checkbox")
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(R.string.end_session_mark_completed),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (isFocusSession) {
                            Text(
                                text = stringResource(R.string.end_session_mark_completed_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(markCompleted) },
                modifier = Modifier.testTag("end_session_confirm_button")
            ) { Text(stringResource(R.string.end_session_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
