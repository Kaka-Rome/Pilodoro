package com.pomodorofocus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodorofocus.app.model.PomodoroUiState
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.model.TimerState

@Composable
fun TimerDisplay(
    uiState: PomodoroUiState,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = tween(durationMillis = 350),
        label = "timer_progress"
    )
    val wavePhase = if (uiState.timerState == TimerState.RUNNING) {
        val waveTransition = rememberInfiniteTransition(label = "timer_wave")
        val phase by waveTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600),
                repeatMode = RepeatMode.Restart
            ),
            label = "timer_wave_phase"
        )
        phase
    } else 0f
    val hasCustomBg = uiState.customBackgroundBitmap != null && uiState.appearanceSettings.hasCustomBackground
    // Use the selected accent consistently for Focus and both break sessions.
    val primaryColor = MaterialTheme.colorScheme.primary
    val timeColor = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurface
    val emptyColor = MaterialTheme.colorScheme.surface
    val status = when (uiState.timerState) {
        TimerState.IDLE -> "Ready to begin"
        TimerState.RUNNING -> if (uiState.sessionType == SessionType.FOCUS) "Stay focused" else "Rest & recharge"
        TimerState.PAUSED -> "Paused"
        TimerState.COMPLETED -> "Session completed!"
    }
    val remainingFraction = if (uiState.totalDurationMillis <= 0L) 0f
        else (uiState.remainingMillis.toFloat() / uiState.totalDurationMillis).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("timer_display_container")
    ) {
        Text(
            text = uiState.formattedTime,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = (54f * uiState.appearanceSettings.timerFont.sizeScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = uiState.appearanceSettings.timerFont
                    .toFontFamily(uiState.appearanceSettings.appFont),
                letterSpacing = uiState.appearanceSettings.timerFont.letterSpacingSp.sp
            ),
            color = timeColor,
            modifier = Modifier.graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }.testTag("remaining_time_text")
        )
        Spacer(Modifier.height(32.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(142.dp)
                    .height(264.dp)
                    .clip(RoundedCornerShape(72.dp))
                    .semantics { contentDescription = "Timer progress ${(remainingFraction * 100).toInt()} percent remaining" }
              ) {
                Canvas(Modifier.matchParentSize()) {
                    val capsule = androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f)
                    )
                    val capsulePath = Path().apply { addRoundRect(capsule) }
                    drawPath(capsulePath, color = primaryColor)
                    val boundaryY = size.height * (1f - remainingFraction)
                    val wave = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, boundaryY)
                        val steps = 48
                        for (i in steps downTo 0) {
                            val x = size.width * i / steps
                            val y = boundaryY + kotlin.math.sin((x / size.width * 2.0 * Math.PI) + wavePhase) * 5.dp.toPx()
                            lineTo(x, y.toFloat())
                        }
                        close()
                    }
                    drawPath(wave, color = emptyColor)
                    drawPath(capsulePath, color = primaryColor, style = Stroke(width = 6.dp.toPx()))
                }
              }
              Spacer(Modifier.height(22.dp))
              Surface(
                  shape = RoundedCornerShape(50.dp),
                  color = if (hasCustomBg) Color.Black.copy(alpha = 0.5f)
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
              ) {
                  Text(
                      text = status,
                      style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                      color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                  )
              }
            Spacer(Modifier.height(26.dp))
            actions()
        }
    }
}
