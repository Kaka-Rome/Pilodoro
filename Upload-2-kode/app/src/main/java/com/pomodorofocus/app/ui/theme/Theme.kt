package com.pomodorofocus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pomodorofocus.app.model.AppearanceSettings
import com.pomodorofocus.app.model.AppFont
import com.pomodorofocus.app.model.ColorPalette
import com.pomodorofocus.app.model.ThemeMode

fun getColorScheme(palette: ColorPalette, isDark: Boolean): ColorScheme {
    return if (isDark) {
        when (palette) {
            ColorPalette.TOMATO -> darkColorScheme(
                primary = TomatoCoral,
                onPrimary = Color.Black,
                primaryContainer = TomatoContainerDark,
                onPrimaryContainer = Color(0xFFFFDAD5),
                secondary = TomatoCoral,
                onSecondary = Color.Black,
                secondaryContainer = TomatoContainerDark,
                onSecondaryContainer = Color(0xFFFFDAD5),
                background = DarkBackground,
                surface = DarkSurface,
                surfaceVariant = DarkSurfaceVariant,
                onBackground = DarkOnSurface,
                onSurface = DarkOnSurface,
                onSurfaceVariant = Color(0xFFC7C5D0)
            )
            ColorPalette.SAGE -> darkColorScheme(
                primary = SagePrimaryDark,
                onPrimary = Color(0xFF00390E),
                primaryContainer = SageContainerDark,
                onPrimaryContainer = Color(0xFFA6EDAA),
                secondary = SageSecondaryDark,
                onSecondary = Color(0xFF003731),
                secondaryContainer = Color(0xFF004D40),
                onSecondaryContainer = Color(0xFF80CBC4),
                background = DarkBackground,
                surface = DarkSurface,
                surfaceVariant = DarkSurfaceVariant,
                onBackground = DarkOnSurface,
                onSurface = DarkOnSurface,
                onSurfaceVariant = Color(0xFFC7C5D0)
            )
            ColorPalette.BLUE -> darkColorScheme(
                primary = BluePrimaryDark,
                onPrimary = Color(0xFF00325B),
                primaryContainer = BlueContainerDark,
                onPrimaryContainer = Color(0xFFD1E4FF),
                secondary = BlueSecondaryDark,
                onSecondary = Color(0xFF00344F),
                secondaryContainer = Color(0xFF01579B),
                onSecondaryContainer = Color(0xFFBCE9FF),
                background = DarkBackground,
                surface = DarkSurface,
                surfaceVariant = DarkSurfaceVariant,
                onBackground = DarkOnSurface,
                onSurface = DarkOnSurface,
                onSurfaceVariant = Color(0xFFC7C5D0)
            )
            ColorPalette.VIOLET -> darkColorScheme(
                primary = VioletPrimaryDark,
                onPrimary = Color(0xFF4A0072),
                primaryContainer = VioletContainerDark,
                onPrimaryContainer = Color(0xFFF2DAFF),
                secondary = VioletSecondaryDark,
                onSecondary = Color(0xFF280068),
                secondaryContainer = Color(0xFF311B92),
                onSecondaryContainer = Color(0xFFE2D6FF),
                background = DarkBackground,
                surface = DarkSurface,
                surfaceVariant = DarkSurfaceVariant,
                onBackground = DarkOnSurface,
                onSurface = DarkOnSurface,
                onSurfaceVariant = Color(0xFFC7C5D0)
            )
            ColorPalette.AMBER -> darkColorScheme(
                primary = AmberPrimaryDark,
                onPrimary = Color(0xFF4A1800),
                primaryContainer = AmberContainerDark,
                onPrimaryContainer = Color(0xFFFFDCC1),
                secondary = AmberSecondaryDark,
                onSecondary = Color(0xFF4E2600),
                secondaryContainer = Color(0xFF6B3500),
                onSecondaryContainer = Color(0xFFFFDDB8),
                background = DarkBackground,
                surface = DarkSurface,
                surfaceVariant = DarkSurfaceVariant,
                onBackground = DarkOnSurface,
                onSurface = DarkOnSurface,
                onSurfaceVariant = Color(0xFFC7C5D0)
            )
        }
    } else {
        when (palette) {
            ColorPalette.TOMATO -> lightColorScheme(
                primary = TomatoRed,
                onPrimary = Color.White,
                primaryContainer = TomatoLight,
                onPrimaryContainer = TomatoRedDark,
                secondary = TomatoRed,
                onSecondary = Color.White,
                secondaryContainer = TomatoLight,
                onSecondaryContainer = TomatoRedDark,
                background = LightBackground,
                surface = LightSurface,
                surfaceVariant = LightSurfaceVariant,
                onBackground = LightOnSurface,
                onSurface = LightOnSurface,
                onSurfaceVariant = Color(0xFF49454F)
            )
            ColorPalette.SAGE -> lightColorScheme(
                primary = SagePrimaryLight,
                onPrimary = Color.White,
                primaryContainer = SageContainerLight,
                onPrimaryContainer = Color(0xFF00390E),
                secondary = SageSecondaryLight,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE0F2F1),
                onSecondaryContainer = Color(0xFF004D40),
                background = LightBackground,
                surface = LightSurface,
                surfaceVariant = LightSurfaceVariant,
                onBackground = LightOnSurface,
                onSurface = LightOnSurface,
                onSurfaceVariant = Color(0xFF49454F)
            )
            ColorPalette.BLUE -> lightColorScheme(
                primary = BluePrimaryLight,
                onPrimary = Color.White,
                primaryContainer = BlueContainerLight,
                onPrimaryContainer = Color(0xFF00325B),
                secondary = BlueSecondaryLight,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE1F5FE),
                onSecondaryContainer = Color(0xFF00344F),
                background = LightBackground,
                surface = LightSurface,
                surfaceVariant = LightSurfaceVariant,
                onBackground = LightOnSurface,
                onSurface = LightOnSurface,
                onSurfaceVariant = Color(0xFF49454F)
            )
            ColorPalette.VIOLET -> lightColorScheme(
                primary = VioletPrimaryLight,
                onPrimary = Color.White,
                primaryContainer = VioletContainerLight,
                onPrimaryContainer = Color(0xFF4A0072),
                secondary = VioletSecondaryLight,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFEDE7F6),
                onSecondaryContainer = Color(0xFF280068),
                background = LightBackground,
                surface = LightSurface,
                surfaceVariant = LightSurfaceVariant,
                onBackground = LightOnSurface,
                onSurface = LightOnSurface,
                onSurfaceVariant = Color(0xFF49454F)
            )
            ColorPalette.AMBER -> lightColorScheme(
                primary = AmberPrimaryLight,
                // Amber/orange surfaces are too bright for white button labels.
                onPrimary = Color(0xFF4A1800),
                primaryContainer = AmberContainerLight,
                onPrimaryContainer = Color(0xFF4A1800),
                secondary = AmberSecondaryLight,
                onSecondary = Color(0xFF4A1800),
                secondaryContainer = Color(0xFFFFF8E1),
                onSecondaryContainer = Color(0xFF4E2600),
                background = LightBackground,
                surface = LightSurface,
                surfaceVariant = LightSurfaceVariant,
                onBackground = LightOnSurface,
                onSurface = LightOnSurface,
                onSurfaceVariant = Color(0xFF49454F)
            )
        }
    }
}

fun createAppTypography(appFont: AppFont): Typography {
    val fontFamily = appFont.toFontFamily()
    return Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp
        ),
        displayMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}

@Composable
fun PomodoroFocusTheme(
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (appearanceSettings.themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = getColorScheme(appearanceSettings.colorPalette, isDark)
    val typography = createAppTypography(appearanceSettings.appFont)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
