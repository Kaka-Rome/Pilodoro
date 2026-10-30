package com.pomodorofocus.app.model

import androidx.annotation.StringRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pomodorofocus.app.R

/**
 * Supported display theme modes.
 */
enum class ThemeMode(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.appearance_theme_system),
    LIGHT(R.string.appearance_theme_light),
    DARK(R.string.appearance_theme_dark)
}

/**
 * Curated accent color palettes.
 */
enum class ColorPalette(@param:StringRes val labelRes: Int) {
    TOMATO(R.string.appearance_palette_tomato),
    SAGE(R.string.appearance_palette_sage),
    BLUE(R.string.appearance_palette_blue),
    VIOLET(R.string.appearance_palette_violet),
    AMBER(R.string.appearance_palette_amber)
}

/**
 * Built-in typography font families.
 */
enum class AppFont(@param:StringRes val labelRes: Int) {
    SANS_SERIF(R.string.appearance_font_sans_serif),
    MONOSPACE(R.string.appearance_font_monospace),
    SERIF(R.string.appearance_font_serif),
    ROUNDED(R.string.appearance_font_rounded);

    fun toFontFamily(): FontFamily = when (this) {
        SANS_SERIF -> FontFamily.SansSerif
        MONOSPACE -> FontFamily.Monospace
        SERIF -> FontFamily.Serif
        ROUNDED -> FontFamily.Cursive
    }
}

private val OutfitExtraBold = FontFamily(Font(R.font.outfit_extrabold, FontWeight.Bold))
private val JetBrainsMonoExtraBold = FontFamily(Font(R.font.jetbrains_mono_extrabold, FontWeight.Bold))
private val BebasNeue = FontFamily(Font(R.font.bebas_neue_regular, FontWeight.Bold))
private val Dseg7Classic = FontFamily(Font(R.font.dseg7_classic_bold, FontWeight.Bold))

/**
 * Font used only for the big timer digits. The first four options are bundled with the app
 * (res/font), so they work offline, and all of them have equal-width digits so the time doesn't
 * jitter as it ticks. [sizeScale] evens out the visual size of very narrow or very wide designs.
 */
enum class TimerFont(
    @param:StringRes val labelRes: Int,
    val letterSpacingSp: Float,
    val sizeScale: Float
) {
    GEOMETRIC_BOLD(R.string.appearance_timer_font_geometric_bold, 0f, 1f),
    MONO_BOLD(R.string.appearance_timer_font_mono_bold, -0.5f, 1f),
    CONDENSED(R.string.appearance_timer_font_condensed, 1f, 1.35f),
    DIGITAL(R.string.appearance_timer_font_digital, 0f, 0.85f),
    APP_FONT(R.string.appearance_timer_font_app, -1.5f, 1f);

    fun toFontFamily(appFont: AppFont): FontFamily = when (this) {
        GEOMETRIC_BOLD -> OutfitExtraBold
        MONO_BOLD -> JetBrainsMonoExtraBold
        CONDENSED -> BebasNeue
        DIGITAL -> Dseg7Classic
        APP_FONT -> appFont.toFontFamily()
    }
}

/**
 * Complete appearance preferences configuration.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorPalette: ColorPalette = ColorPalette.TOMATO,
    val appFont: AppFont = AppFont.SANS_SERIF,
    val isMinimalMode: Boolean = false,
    val hasCustomBackground: Boolean = false,
    val backgroundDimPercent: Int = 40,
    val timerFont: TimerFont = TimerFont.GEOMETRIC_BOLD
)
