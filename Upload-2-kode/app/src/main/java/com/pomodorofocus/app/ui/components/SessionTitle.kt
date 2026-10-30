package com.pomodorofocus.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pomodorofocus.app.R
import com.pomodorofocus.app.model.SESSION_TITLE_MAX_LENGTH
import com.pomodorofocus.app.model.SessionTag
import com.pomodorofocus.app.model.sanitizeSessionTitle

/** Fixed height reserved under the timer controls, so the layout never jumps when the button appears. */
val SessionTitleSlotHeight: Dp = 44.dp

/**
 * Small, low-key text button under the timer controls. It is optional, so it is deliberately
 * quieter than the rest of the typography. [visible] is true only while a Focus session is
 * running or paused; the slot keeps its height either way.
 */
@Composable
fun SessionTitleButton(
    visible: Boolean,
    title: String,
    tag: SessionTag?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onWallpaper: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(SessionTitleSlotHeight)
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            val tagLabel = tag?.let { stringResource(it.labelRes) }
            val label = when {
                title.isNotEmpty() && tagLabel != null -> "$title · $tagLabel"
                title.isNotEmpty() -> title
                tagLabel != null -> tagLabel
                else -> stringResource(R.string.session_title_add)
            }
            // White + soft shadow on a wallpaper, muted theme colour otherwise; never bold.
            val color = if (onWallpaper) Color.White.copy(alpha = 0.85f)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            Text(
                text = label,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = if (onWallpaper) {
                        Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 6f)
                    } else null
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("add_session_title_button")
            )
        }
    }
}

/**
 * Optional title + one preset tag for the Focus session in progress. Everything may stay empty;
 * tags can only be picked from the fixed [SessionTag] list (no custom tags).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionTitleDialog(
    initialTitle: String,
    initialTag: SessionTag?,
    onDismiss: () -> Unit,
    onSave: (title: String, tag: SessionTag?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var tag by remember { mutableStateOf(initialTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_title_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.replace("\n", " ").take(SESSION_TITLE_MAX_LENGTH) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.session_title_hint)) },
                    supportingText = { Text("${title.length}/$SESSION_TITLE_MAX_LENGTH") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_title_input")
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.session_tag_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SessionTag.entries.forEach { option ->
                        FilterChip(
                            selected = tag == option,
                            // Tapping the selected tag again clears it (tag is optional).
                            onClick = { tag = if (tag == option) null else option },
                            label = { Text(stringResource(option.labelRes)) },
                            modifier = Modifier.testTag("session_tag_${option.name.lowercase()}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(sanitizeSessionTitle(title), tag) },
                modifier = Modifier.testTag("session_title_save_button")
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
