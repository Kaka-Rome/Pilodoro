package com.pomodorofocus.app.ui.components

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodorofocus.app.R
import com.pomodorofocus.app.model.AppearanceSettings
import com.pomodorofocus.app.model.AppFont
import com.pomodorofocus.app.model.ColorPalette
import com.pomodorofocus.app.model.PomodoroSettings
import com.pomodorofocus.app.model.ThemeMode
import com.pomodorofocus.app.model.TimerFont
import com.pomodorofocus.app.ui.theme.AmberPrimaryLight
import com.pomodorofocus.app.ui.theme.BluePrimaryLight
import com.pomodorofocus.app.ui.theme.SagePrimaryLight
import com.pomodorofocus.app.ui.theme.TomatoRed
import com.pomodorofocus.app.ui.theme.VioletPrimaryLight

// Floating settings navbar: its size, how far it sits above the system navigation bar, and the
// scroll space reserved under the content so the last item can always be scrolled fully above it.
private val FloatingNavHeight = 68.dp
private val FloatingNavBottomMargin = 24.dp
private val FloatingNavReservedSpace = FloatingNavHeight + FloatingNavBottomMargin + 16.dp

@Composable
fun SettingsDialog(
    currentSettings: PomodoroSettings,
    currentAppearance: AppearanceSettings,
    customBackgroundBitmap: Bitmap?,
    backgroundErrorMessage: String?,
    onSaveDurations: (PomodoroSettings) -> Unit,
    onSaveAppearance: (AppearanceSettings) -> Unit,
    onPickCustomBackground: () -> Unit,
    onRemoveCustomBackground: () -> Unit,
    onDismiss: () -> Unit,
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    onResetHistory: () -> Unit = {},
    dndAccessGranted: Boolean = true,
    onRequestDndAccess: () -> Unit = {}
) {
    BackHandler { onDismiss() }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Durations state
    var focusMinutes by remember { mutableIntStateOf(currentSettings.focusMinutes) }
    var shortBreakMinutes by remember { mutableIntStateOf(currentSettings.shortBreakMinutes) }
    var longBreakMinutes by remember { mutableIntStateOf(currentSettings.longBreakMinutes) }
    var sessionsBeforeLongBreak by remember { mutableIntStateOf(currentSettings.sessionsBeforeLongBreak) }
    var autoStartBreaks by remember { mutableStateOf(currentSettings.autoStartBreaks) }
    var autoStartFocus by remember { mutableStateOf(currentSettings.autoStartFocus) }
    var keepScreenOn by remember { mutableStateOf(currentSettings.keepScreenOn) }
    var dailyGoal by remember { mutableIntStateOf(currentSettings.dailyGoal) }
    var dndDuringFocus by remember { mutableStateOf(currentSettings.dndDuringFocus) }

    // Appearance state
    var selectedThemeMode by remember { mutableStateOf(currentAppearance.themeMode) }
    var selectedPalette by remember { mutableStateOf(currentAppearance.colorPalette) }
    var selectedFont by remember { mutableStateOf(currentAppearance.appFont) }
    var selectedTimerFont by remember { mutableStateOf(currentAppearance.timerFont) }
    var backgroundDimPercent by remember(currentAppearance.backgroundDimPercent) {
        mutableIntStateOf(currentAppearance.backgroundDimPercent)
    }

    val hasCustomBg = (customBackgroundBitmap != null) && currentAppearance.hasCustomBackground

    fun persistDurations(
        focus: Int = focusMinutes,
        shortBreak: Int = shortBreakMinutes,
        longBreak: Int = longBreakMinutes,
        interval: Int = sessionsBeforeLongBreak,
        autoBreaks: Boolean = autoStartBreaks,
        autoFocus: Boolean = autoStartFocus,
        screenOn: Boolean = keepScreenOn,
        goal: Int = dailyGoal,
        dnd: Boolean = dndDuringFocus
    ) {
        onSaveDurations(
            PomodoroSettings(focus, shortBreak, longBreak, interval, autoBreaks, autoFocus, screenOn, goal, dnd)
        )
    }

    fun persistAppearance(
        theme: ThemeMode = selectedThemeMode,
        palette: ColorPalette = selectedPalette,
        font: AppFont = selectedFont,
        dim: Int = backgroundDimPercent,
        timerFont: TimerFont = selectedTimerFont
    ) {
        onSaveAppearance(
            AppearanceSettings(theme, palette, font, currentAppearance.isMinimalMode, hasCustomBg, dim, timerFont)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                    Text(stringResource(R.string.settings_main_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    // Content layer: fills the whole area and scrolls behind the floating navbar.
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 560.dp).padding(horizontal = 16.dp)) {
              Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Bottom padding scrolls with the content, so the last item ends just above the navbar.
                    .padding(top = 4.dp, bottom = FloatingNavReservedSpace)
              ) {
                if (selectedTab == 0) {
                    // DURATIONS TAB
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DurationValueCard(
                                label = stringResource(R.string.settings_focus_duration), value = focusMinutes,
                                minimum = 1, maximum = 120, onChange = { focusMinutes = it; persistDurations(focus = it) }, modifier = Modifier.weight(1f)
                            )
                            DurationValueCard(
                                label = stringResource(R.string.settings_short_break_duration), value = shortBreakMinutes,
                                minimum = 1, maximum = 30, onChange = { shortBreakMinutes = it; persistDurations(shortBreak = it) }, modifier = Modifier.weight(1f)
                            )
                            DurationValueCard(
                                label = stringResource(R.string.settings_long_break_duration), value = longBreakMinutes,
                                minimum = 1, maximum = 60, onChange = { longBreakMinutes = it; persistDurations(longBreak = it) }, modifier = Modifier.weight(1f)
                            )
                        }

                        SettingSliderCard(
                            title = stringResource(R.string.settings_sessions_before_long_break),
                            valueLabel = sessionsBeforeLongBreak.toString(),
                            value = sessionsBeforeLongBreak.toFloat(),
                            valueRange = 1f..10f,
                            steps = 8,
                            onValueChange = { sessionsBeforeLongBreak = it.toInt(); persistDurations(interval = it.toInt()) }
                        )
                        SettingSliderCard(
                            title = stringResource(R.string.settings_daily_goal),
                            valueLabel = dailyGoal.toString(),
                            value = dailyGoal.toFloat(),
                            valueRange = 0f..10f,
                            steps = 9,
                            onValueChange = { dailyGoal = it.toInt(); persistDurations(goal = it.toInt()) }
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_auto_start_breaks),
                            description = stringResource(R.string.settings_auto_start_breaks_desc),
                            checked = autoStartBreaks,
                            onCheckedChange = { autoStartBreaks = it; persistDurations(autoBreaks = it) },
                            modifier = Modifier.testTag("auto_start_breaks_switch")
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_auto_start_focus),
                            description = stringResource(R.string.settings_auto_start_focus_desc),
                            checked = autoStartFocus,
                            onCheckedChange = { autoStartFocus = it; persistDurations(autoFocus = it) },
                            modifier = Modifier.testTag("auto_start_focus_switch")
                        )

                    }
                } else if (selectedTab == 1) {
                    // APPEARANCE TAB
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // 1. Theme Mode Selection
                        Column {
                            Text(
                                text = stringResource(R.string.appearance_theme_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("theme_mode_selector"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    val isSelected = mode == selectedThemeMode
                                    val tag = when (mode) {
                                        ThemeMode.SYSTEM -> "theme_mode_system"
                                        ThemeMode.LIGHT -> "theme_mode_light"
                                        ThemeMode.DARK -> "theme_mode_dark"
                                    }
                                    val icon = when (mode) {
                                        ThemeMode.SYSTEM -> Icons.Default.PhoneAndroid
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedThemeMode = mode; persistAppearance(theme = mode) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(stringResource(mode.labelRes), fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag(tag)
                                    )
                                }
                            }
                        }

                        // 2. Color Palette Selection
                        Column {
                            Text(
                                text = stringResource(R.string.appearance_palette_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .testTag("color_palette_selector"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ColorPalette.entries.forEach { palette ->
                                    val isSelected = palette == selectedPalette
                                    val swatchColor = when (palette) {
                                        ColorPalette.TOMATO -> TomatoRed
                                        ColorPalette.SAGE -> SagePrimaryLight
                                        ColorPalette.BLUE -> BluePrimaryLight
                                        ColorPalette.VIOLET -> VioletPrimaryLight
                                        ColorPalette.AMBER -> AmberPrimaryLight
                                    }
                                    val tag = "palette_${palette.name.lowercase()}"

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedPalette = palette; persistAppearance(palette = palette) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                            )
                                        },
                                        label = { Text(stringResource(palette.labelRes)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag(tag)
                                    )
                                }
                            }
                        }

                        // 3. App Font Selection
                        Column {
                            Text(
                                text = stringResource(R.string.appearance_font_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .testTag("app_font_selector"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppFont.entries.forEach { font ->
                                    val isSelected = font == selectedFont
                                    val tag = "font_${font.name.lowercase()}"

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedFont = font; persistAppearance(font = font) },
                                        label = {
                                            Text(
                                                text = stringResource(font.labelRes),
                                                fontFamily = font.toFontFamily()
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag(tag)
                                    )
                                }
                            }
                        }

                        // 4. Timer Font Selection (digits only)
                        Column {
                            Text(
                                text = stringResource(R.string.appearance_timer_font_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .testTag("timer_font_selector"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TimerFont.entries.forEach { timerFont ->
                                    FilterChip(
                                        selected = timerFont == selectedTimerFont,
                                        onClick = {
                                            selectedTimerFont = timerFont
                                            persistAppearance(timerFont = timerFont)
                                        },
                                        label = {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "25:00",
                                                    fontFamily = timerFont.toFontFamily(selectedFont),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = (22f * timerFont.sizeScale).sp
                                                )
                                                Text(
                                                    text = stringResource(timerFont.labelRes),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 64.dp)
                                            .testTag("timer_font_${timerFont.name.lowercase()}"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                    )
                                }
                            }
                        }

                        // Custom Background Section
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.appearance_background_section_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.appearance_background_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Error message banner if background selection/processing failed
                            if (!backgroundErrorMessage.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("background_error_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = backgroundErrorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            if (customBackgroundBitmap != null && hasCustomBg) {
                                // Background preview card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        .testTag("background_preview_box")
                                ) {
                                    Image(
                                        bitmap = customBackgroundBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.appearance_background_preview_label),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Dimming overlay preview
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = backgroundDimPercent / 100f))
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.appearance_background_preview_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Action buttons: Change / Remove
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onPickCustomBackground,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("change_background_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.appearance_background_change_btn), fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = onRemoveCustomBackground,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("remove_background_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.appearance_background_remove_btn), fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Dimming slider
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.appearance_background_dim_label, backgroundDimPercent),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = backgroundDimPercent.toFloat(),
                                        onValueChange = { backgroundDimPercent = it.toInt(); persistAppearance(dim = it.toInt()) },
                                        valueRange = 0f..80f,
                                        steps = 15,
                                        modifier = Modifier.testTag("background_dim_slider")
                                    )
                                }
                            } else {
                                // Empty state button to pick image
                                FilledTonalButton(
                                    onClick = onPickCustomBackground,
                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pick_background_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.appearance_background_choose_btn))
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_behavior_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_keep_screen_on),
                            description = stringResource(R.string.settings_keep_screen_on_desc),
                            checked = keepScreenOn,
                            onCheckedChange = { keepScreenOn = it; persistDurations(screenOn = it) },
                            modifier = Modifier.testTag("keep_screen_on_switch")
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_dnd_focus),
                            description = stringResource(R.string.settings_dnd_focus_desc),
                            checked = dndDuringFocus,
                            onCheckedChange = {
                                dndDuringFocus = it
                                persistDurations(dnd = it)
                                if (it && !dndAccessGranted) onRequestDndAccess()
                            },
                            modifier = Modifier.testTag("dnd_focus_switch")
                        )
                        if (dndDuringFocus && !dndAccessGranted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().testTag("dnd_access_row")
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_dnd_access_needed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onRequestDndAccess,
                                    modifier = Modifier.testTag("dnd_grant_access_button")
                                ) { Text(stringResource(R.string.settings_dnd_access_action)) }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DataManagementSection(
                            onExport = onExportData,
                            onImport = onImportData,
                            onReset = onResetHistory
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AboutInfoSection()
                    }
                }
              }
                    }
                    // Soft fade so scrolled content doesn't show harshly around the navbar (touches pass through).
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(FloatingNavReservedSpace)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                    // Floating navbar (same pill shape as before, not full width), overlaid above the content.
                    AnimatedSettingsNavigation(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .padding(bottom = FloatingNavBottomMargin)
                    )
                }
            }
        }
}

@Composable
private fun AnimatedSettingsNavigation(selectedTab: Int, onSelectTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf(
        Triple(Icons.Default.Timer, R.string.settings_tab_durations, "tab_settings_durations"),
        Triple(Icons.Default.Palette, R.string.settings_tab_appearance, "tab_settings_appearance"),
        Triple(Icons.Default.MoreHoriz, R.string.settings_tab_more, "tab_settings_more")
    )
    Surface(
        modifier = modifier.fillMaxWidth().height(FloatingNavHeight).testTag("settings_bottom_navigation"),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 10.dp
    ) {
        Row(Modifier.fillMaxSize().padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, (icon, label, tag) ->
                val selected = selectedTab == index
                val weight by animateFloatAsState(if (selected) 1.65f else 1f, label = "settings_tab_weight_$index")
                Surface(
                    onClick = { onSelectTab(index) },
                    modifier = Modifier.weight(weight).fillMaxSize().padding(horizontal = 2.dp).animateContentSize().testTag(tag),
                    shape = RoundedCornerShape(21.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = stringResource(label), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
                        AnimatedVisibility(visible = selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            Text(stringResource(label), modifier = Modifier.padding(start = 7.dp), maxLines = 1, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationValueCard(
    label: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember(value) { mutableStateOf(value.toString()) }
    var wasFocused by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val maxDigits = maximum.toString().length
    val cardColor = if (isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = cardColor,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(76.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(maxDigits)
                        inputText = digits
                        val parsed = digits.toIntOrNull()
                        if (parsed != null && parsed in minimum..maximum) onChange(parsed)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .onFocusChanged { state ->
                            isActive = state.isFocused
                            if (wasFocused && !state.isFocused) {
                                val parsed = inputText.toIntOrNull()
                                if (parsed == null || parsed !in minimum..maximum) inputText = value.toString()
                            }
                            wasFocused = state.isFocused
                        }
                        .testTag("duration_input_${label.lowercase().replace(' ', '_')}"),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(contentColor),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = inputText.toIntOrNull()
                            if (parsed == null || parsed !in minimum..maximum) inputText = value.toString()
                            focusManager.clearFocus()
                        }
                    )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingSliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun DurationStepper(
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    presets: List<Int>,
    onValueChange: (Int) -> Unit,
    unit: String = "min",
    presetSuffix: String = "m"
) {
    var inputText by remember(value) { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalIconButton(
                onClick = { if (value > minValue) onValueChange(value - 1) },
                enabled = value > minValue,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }

            // Kolom input angka: bisa diketik langsung lewat keyboard numerik
            OutlinedTextField(
                value = inputText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(3)
                    if (digits.isEmpty()) {
                        inputText = ""
                    } else {
                        val typed = digits.toInt().coerceAtMost(maxValue)
                        inputText = typed.toString()
                        if (typed >= minValue) onValueChange(typed)
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                suffix = { if (unit.isNotEmpty()) Text(unit) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .onFocusChanged { state ->
                        // Saat selesai mengetik: kosong atau di bawah minimum kembali ke nilai valid terakhir
                        if (!state.isFocused) {
                            val n = inputText.toIntOrNull()
                            if (n == null || n < minValue) inputText = value.toString()
                        }
                    }
            )

            FilledTonalIconButton(
                onClick = { if (value < maxValue) onValueChange(value + 1) },
                enabled = value < maxValue,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Preset Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { preset ->
                SuggestionChip(
                    onClick = { onValueChange(preset) },
                    label = { Text("${preset}${presetSuffix}") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

/** Settings > More: local backup (export / import JSON) and focus-history reset. */
@Composable
private fun DataManagementSection(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showImportConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_data_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_data_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f).testTag("export_data_button")
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_data_export))
            }
            OutlinedButton(
                onClick = { showImportConfirm = true },
                modifier = Modifier.weight(1f).testTag("import_data_button")
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_data_import))
            }
        }

        OutlinedButton(
            onClick = { showResetConfirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().testTag("reset_history_button")
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_data_reset))
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.settings_data_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_data_import_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        onImport()
                    },
                    modifier = Modifier.testTag("import_confirm_button")
                ) { Text(stringResource(R.string.settings_data_import_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_data_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_data_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onReset()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("reset_confirm_button")
                ) { Text(stringResource(R.string.settings_data_reset_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** App attribution and build metadata for Settings > More. */
@Composable
private fun AboutInfoSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth().testTag("about_info_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_about_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_about_version, com.pomodorofocus.app.BuildConfig.VERSION_NAME, com.pomodorofocus.app.BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_about_creator),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.settings_about_github_url)))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("about_github_button")
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(stringResource(R.string.settings_about_github))
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
