package com.pomodorofocus.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pomodorofocus.app.ui.components.EndSessionDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodorofocus.app.R
import com.pomodorofocus.app.model.AppearanceSettings
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.PomodoroUiState
import com.pomodorofocus.app.model.SessionTag
import com.pomodorofocus.app.model.SessionType
import com.pomodorofocus.app.model.TimerState
import com.pomodorofocus.app.ui.components.SessionTitleButton
import com.pomodorofocus.app.ui.components.SessionTitleDialog
import com.pomodorofocus.app.ui.components.SessionTitleSlotHeight
import com.pomodorofocus.app.ui.components.NotificationPermissionBanner
import com.pomodorofocus.app.ui.components.SettingsDialog
import com.pomodorofocus.app.ui.components.TimerControls
import com.pomodorofocus.app.ui.components.TimerDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroMainScreen(
    uiState: PomodoroUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSaveSettings: (PomodoroSettings) -> Unit,
    onSaveAppearanceSettings: (AppearanceSettings) -> Unit,
    onPickCustomBackground: () -> Unit,
    onRemoveCustomBackground: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDismissPermissionRationale: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    onResetHistory: () -> Unit = {},
    onRequestDndAccess: () -> Unit = {},
    onEndSession: (markCompleted: Boolean) -> Unit = {},
    resetEvents: Flow<Unit> = emptyFlow(),
    onUndoReset: () -> Unit = {},
    onSetSessionMeta: (title: String, tag: SessionTag?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val hasCustomBg = uiState.customBackgroundBitmap != null && uiState.appearanceSettings.hasCustomBackground
    val dimAlpha = if (hasCustomBg) {
        (uiState.appearanceSettings.backgroundDimPercent / 100f).coerceIn(0.0f, 0.9f)
    } else 0f
    // Same rule the Next button uses, so the info below the controls and the button always agree.
    val nextSession = uiState.sessionType.upcoming(
        completedSessions = uiState.completedSessions,
        sessionsBeforeLongBreak = uiState.settings.sessionsBeforeLongBreak
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var showEndSessionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.sessionType) {
        if (uiState.sessionType != SessionType.FOCUS) showEndSessionDialog = false
    }
    // "Add Session Title" exists only for a Focus session that has been started (running or paused).
    val canEditSessionTitle = uiState.sessionType == SessionType.FOCUS &&
        (uiState.timerState == TimerState.RUNNING || uiState.timerState == TimerState.PAUSED)
    var showSessionTitleDialog by remember { mutableStateOf(false) }
    LaunchedEffect(canEditSessionTitle) {
        if (!canEditSessionTitle) showSessionTitleDialog = false
    }
    val resetMessage = stringResource(R.string.timer_reset_message)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(resetEvents) {
        // collectLatest: a newer reset replaces the message that is still showing
        resetEvents.collectLatest {
            val result = snackbarHostState.showSnackbar(
                message = resetMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) onUndoReset()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCustomBg && uiState.customBackgroundBitmap != null) {
            Image(
                bitmap = uiState.customBackgroundBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag("custom_background_image")
            )
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
                    .testTag("background_dim_overlay")
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(uiState.sessionType.labelRes).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateToStatistics, modifier = Modifier.testTag("statistics_nav_button")) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = stringResource(R.string.action_statistics),
                                tint = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings_button")) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                                tint = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (hasCustomBg) Color.Transparent else MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = if (hasCustomBg) Color.Transparent else MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            BoxWithConstraints(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding()
            ) {
              val availableHeight = this.maxHeight
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.widthIn(max = 480.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
              ) {
                if (uiState.showPermissionRationale && !uiState.hasNotificationPermission) {
                    NotificationPermissionBanner(
                        onRequestPermission = onRequestNotificationPermission,
                        onDismiss = onDismissPermissionRationale
                    )
                    Spacer(Modifier.height(8.dp))
                }

                TimerDisplay(
                    uiState = uiState,
                    actions = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TimerControls(
                                timerState = uiState.timerState,
                                onStart = onStart,
                                onPause = onPause,
                                onResume = onResume,
                                onReset = onReset,
                                onSkip = onSkip,
                                onEndSession = { showEndSessionDialog = true },
                                onWallpaper = hasCustomBg,
                                isFocusSession = uiState.sessionType == SessionType.FOCUS
                            )
                            SessionTitleButton(
                                visible = canEditSessionTitle,
                                title = uiState.sessionTitle,
                                tag = uiState.sessionTag,
                                onClick = { showSessionTitleDialog = true },
                                onWallpaper = hasCustomBg
                            )
                        }
                    }
                )

                Spacer(Modifier.height((availableHeight - 650.dp - SessionTitleSlotHeight).coerceAtLeast(16.dp)))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("next_session_summary")
                ) {
                    Text(
                        text = stringResource(R.string.next_session_label),
                        color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "%02d:00".format(nextSession.getDurationMinutes(uiState.settings)),
                        color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(nextSession.labelRes),
                        color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp)
                    )
                }
              }
            }
        }
    }

    if (showEndSessionDialog && uiState.sessionType == SessionType.FOCUS) {
        EndSessionDialog(
            isFocusSession = uiState.sessionType == SessionType.FOCUS,
            onDismiss = { showEndSessionDialog = false },
            onConfirm = { markCompleted ->
                showEndSessionDialog = false
                onEndSession(markCompleted)
            }
        )
    }

    if (showSessionTitleDialog && canEditSessionTitle) {
        SessionTitleDialog(
            initialTitle = uiState.sessionTitle,
            initialTag = uiState.sessionTag,
            onDismiss = { showSessionTitleDialog = false },
            onSave = { title, tag ->
                showSessionTitleDialog = false
                onSetSessionMeta(title, tag)
            }
        )
    }

    if (uiState.isSettingsOpen) {
        SettingsDialog(
            currentSettings = uiState.settings,
            currentAppearance = uiState.appearanceSettings,
            customBackgroundBitmap = uiState.customBackgroundBitmap,
            backgroundErrorMessage = uiState.backgroundErrorMessage,
            onSaveDurations = onSaveSettings,
            onSaveAppearance = onSaveAppearanceSettings,
            onPickCustomBackground = onPickCustomBackground,
            onRemoveCustomBackground = onRemoveCustomBackground,
            onDismiss = onCloseSettings,
            onExportData = onExportData,
            onImportData = onImportData,
            onResetHistory = onResetHistory,
            dndAccessGranted = uiState.isDndAccessGranted,
            onRequestDndAccess = onRequestDndAccess
        )
    }
}
