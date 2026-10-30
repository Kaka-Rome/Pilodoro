package com.pomodorofocus.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pomodorofocus.app.R
import com.pomodorofocus.app.model.TimerState

/**
 * Timer controls, drawn as plain icons (no button shapes).
 *
 * - Ready / completed: one Play icon.
 * - Running: one Pause icon.
 * - Paused: four icons - Reset, Play (resume), Next, End Session.
 *
 * [onWallpaper] is true when a custom wallpaper is behind the controls: the icons turn white and get a
 * soft drop shadow so they stay readable on any photo.
 */
@Composable
fun TimerControls(
    timerState: TimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onEndSession: () -> Unit = {},
    onWallpaper: Boolean = false,
    isFocusSession: Boolean = true
) {
    val tint = if (onWallpaper) Color.White else MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(ControlsHeight).testTag("timer_controls_row")
    ) {
        when (timerState) {
            TimerState.IDLE, TimerState.COMPLETED -> ControlIconButton(
                imageVector = Icons.Default.PlayArrow,
                description = stringResource(R.string.action_start),
                tag = "start_button",
                onClick = onStart,
                buttonSize = MainButtonSize,
                iconSize = MainIconSize,
                tint = tint,
                withShadow = onWallpaper
            )

            TimerState.RUNNING -> ControlIconButton(
                imageVector = Icons.Default.Pause,
                description = stringResource(R.string.action_pause),
                tag = "pause_button",
                onClick = onPause,
                buttonSize = MainButtonSize,
                iconSize = MainIconSize,
                tint = tint,
                withShadow = onWallpaper
            )

            TimerState.PAUSED -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ControlIconButton(
                    imageVector = Icons.Default.Refresh,
                    description = stringResource(R.string.action_reset),
                    tag = "reset_button",
                    onClick = onReset,
                    buttonSize = SideButtonSize,
                    iconSize = SideIconSize,
                    tint = tint,
                    withShadow = onWallpaper
                )
                ControlIconButton(
                    imageVector = Icons.Default.PlayArrow,
                    description = stringResource(R.string.action_resume),
                    tag = "resume_button",
                    onClick = onResume,
                    buttonSize = MainButtonSize,
                    iconSize = MainIconSize,
                    tint = tint,
                    withShadow = onWallpaper
                )
                ControlIconButton(
                    imageVector = Icons.Default.SkipNext,
                    description = stringResource(R.string.action_skip),
                    tag = "skip_button",
                    onClick = onSkip,
                    buttonSize = SideButtonSize,
                    iconSize = SideIconSize,
                    tint = tint,
                    withShadow = onWallpaper
                )
                if (isFocusSession) ControlIconButton(
                    imageVector = Icons.Default.Stop,
                    description = stringResource(R.string.action_end_session),
                    tag = "end_session_button",
                    onClick = onEndSession,
                    buttonSize = SideButtonSize,
                    iconSize = SideIconSize,
                    tint = tint,
                    withShadow = onWallpaper
                )
            }
        }
    }
}

// Same overall height as the old button row, so the layout below the controls doesn't move.
private val ControlsHeight = 64.dp
private val MainButtonSize = 64.dp
private val MainIconSize = 44.dp
private val SideButtonSize = 52.dp
private val SideIconSize = 30.dp

@Composable
private fun ControlIconButton(
    imageVector: ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
    tint: Color,
    withShadow: Boolean
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize).testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (withShadow) {
                // Soft drop shadow made of a few translucent dark copies of the icon; works on every
                // Android version (Compose has no built-in shadow for icon glyphs).
                SHADOW_LAYERS.forEach { (dx, dy, alpha) ->
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = alpha),
                        modifier = Modifier.size(iconSize).offset(x = dx.dp, y = dy.dp)
                    )
                }
            }
            Icon(
                imageVector = imageVector,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** (x offset dp, y offset dp, alpha) for each shadow copy. */
private val SHADOW_LAYERS = listOf(
    Triple(0f, 3f, 0.16f),
    Triple(1.5f, 2.5f, 0.14f),
    Triple(-1.5f, 2.5f, 0.14f),
    Triple(0f, 1.5f, 0.22f),
    Triple(0f, 0.5f, 0.28f)
)
