package com.pomodorofocus.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pomodorofocus.app.data.BackupManager
import com.pomodorofocus.app.model.AppScreen
import com.pomodorofocus.app.model.TimerState
import com.pomodorofocus.app.notification.NotificationHelper
import com.pomodorofocus.app.ui.PomodoroMainScreen
import com.pomodorofocus.app.ui.PomodoroViewModel
import com.pomodorofocus.app.ui.components.WallpaperEditorDialog
import com.pomodorofocus.app.ui.statistics.StatisticsScreen
import com.pomodorofocus.app.ui.theme.PomodoroFocusTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PomodoroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channel early
        NotificationHelper.createNotificationChannel(this)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val editingBackground by viewModel.editingBackground.collectAsStateWithLifecycle()

            PomodoroFocusTheme(appearanceSettings = uiState.appearanceSettings) {
                val lifecycleOwner = LocalLifecycleOwner.current

                // Jaga layar tetap menyala hanya saat timer berjalan (jika opsinya aktif)
                val hostView = LocalView.current
                val isDarkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
                SideEffect {
                    val systemWindow = this@MainActivity.window
                    systemWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                    systemWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        systemWindow.isStatusBarContrastEnforced = false
                        systemWindow.isNavigationBarContrastEnforced = false
                    }
                    WindowCompat.getInsetsController(systemWindow, hostView).apply {
                        isAppearanceLightStatusBars = !isDarkTheme
                        isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }
                val keepScreenOn = uiState.settings.keepScreenOn &&
                    uiState.timerState == TimerState.RUNNING
                DisposableEffect(hostView, keepScreenOn) {
                    hostView.keepScreenOn = keepScreenOn
                    onDispose { hostView.keepScreenOn = false }
                }

                // Check notification permission on API 33+
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    viewModel.setNotificationPermission(isGranted)
                }

                // Photo picker launcher directly attached to Activity lifecycle
                val photoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        // Buka editor dulu (zoom/geser/putar/crop) sebelum disimpan
                        viewModel.startBackgroundEdit(uri)
                    }
                }

                // Backup export/import via the system file picker (no storage permission needed, works offline)
                val exportBackupLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) viewModel.exportBackup(uri)
                }
                val importBackupLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) viewModel.importBackup(uri)
                }

                // Short result messages for export / import / reset
                LaunchedEffect(Unit) {
                    viewModel.dataMessages.collect { message ->
                        val text = if (message.count != null) {
                            getString(message.messageRes, message.count)
                        } else {
                            getString(message.messageRes)
                        }
                        Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
                    }
                }

                // Check initial permission state
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val isGranted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        viewModel.setNotificationPermission(isGranted)
                    } else {
                        viewModel.setNotificationPermission(true)
                    }
                }

                // Synchronize elapsed time whenever app returns to foreground
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.syncWithElapsedTime()
                            viewModel.refreshDndAccess()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                when (uiState.currentScreen) {
                    AppScreen.TIMER -> {
                        PomodoroMainScreen(
                            uiState = uiState,
                            onStart = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.hasNotificationPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.startTimer()
                            },
                            onPause = viewModel::pauseTimer,
                            onResume = viewModel::resumeTimer,
                            onReset = viewModel::resetTimer,
                            onSkip = viewModel::skipToNextSession,
                            onOpenSettings = viewModel::openSettings,
                            onCloseSettings = viewModel::closeSettings,
                            onSaveSettings = viewModel::saveSettings,
                            onSaveAppearanceSettings = viewModel::saveAppearanceSettings,
                            onPickCustomBackground = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRemoveCustomBackground = viewModel::removeCustomBackground,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onDismissPermissionRationale = viewModel::dismissPermissionRationale,
                            onNavigateToStatistics = { viewModel.navigateTo(AppScreen.STATISTICS) },
                            onExportData = {
                                exportBackupLauncher.launch(BackupManager.suggestedFileName())
                            },
                            onImportData = { importBackupLauncher.launch(arrayOf("*/*")) },
                            onResetHistory = viewModel::resetFocusHistory,
                            onEndSession = viewModel::endSession,
                            resetEvents = viewModel.resetEvents,
                            onUndoReset = viewModel::undoResetTimer,
                            onSetSessionMeta = viewModel::setSessionMeta,
                            onRequestDndAccess = {
                                // Do Not Disturb access is granted by the user in system settings
                                try {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                } catch (_: Exception) {
                                    // Settings screen not available on this device
                                }
                            }
                        )
                    }
                    AppScreen.STATISTICS -> {
                        StatisticsScreen(
                            statsData = uiState.statsPeriodData,
                            selectedPeriod = uiState.statsPeriod,
                            onSelectPeriod = viewModel::setStatsPeriod,
                            onNavigatePeriod = viewModel::navigateStatsPeriod,
                            onNavigateBack = { viewModel.navigateTo(AppScreen.TIMER) },
                            todayCount = uiState.todayFocusSessions,
                            dailyGoal = uiState.settings.dailyGoal
                        )
                    }
                }

                // Editor wallpaper sederhana (muncul di atas semua layar, termasuk dialog pengaturan)
                editingBackground?.let { bitmap ->
                    WallpaperEditorDialog(
                        bitmap = bitmap,
                        onCancel = viewModel::cancelBackgroundEdit,
                        onConfirm = viewModel::confirmBackgroundEdit,
                        contentPadding = WindowInsets.systemBars.asPaddingValues()
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncWithElapsedTime()
    }
}
