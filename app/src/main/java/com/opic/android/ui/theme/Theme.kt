package com.opic.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OPIc brand colors
val OPicBlue = Color(0xFF1565C0)
val OPicLightBlue = Color(0xFF1976D2)
val OPicAccent = Color(0xFF00B0FF)
val OPicGreen = Color(0xFF4CAF50)
val OPicRed = Color(0xFFE53935)

// StudyDetail dark panel colors
val StudyDarkBg = Color(0xFF1A1A2E)
val StudyDarkSurface = Color(0xFF16213E)
val StudyDarkCard = Color(0xFF1F2940)
val StudyGold = Color(0xFFFFD54F)
val StudyGoldDim = Color(0xFFBFA136)
val StudyMicBlue = Color(0xFF42A5F5)
val StudyMicGlow = Color(0xFF1E88E5)
val StudyTextPrimary = Color(0xFFE0E0E0)
val StudyTextSecondary = Color(0xFFA0A0B0)
val StudySeekbarActive = Color(0xFF42A5F5)
val StudySeekbarTrack = Color(0xFF2C3E5A)

private val LightColorScheme = lightColorScheme(
    primary = OPicBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF545F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    background = Color(0xFFF8FAFF),
    surface = Color(0xFFF8FAFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    error = OPicRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color(0xFFF1F5F9),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF1F5F9),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFF1F5F9),
    background = Color(0xFF0F172A),
    surface = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    error = Color(0xFFF87171),
    onError = Color(0xFF0F172A)
)

@Composable
fun OPicTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,  // 커스텀 색상 우선 적용 — dynamic color 사용 안 함
    content: @Composable () -> Unit
) {
    val bgColor    = if (darkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFF)
    val onBgColor  = if (darkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)

    val colorScheme = (if (darkTheme) DarkColorScheme else LightColorScheme).copy(
        background   = bgColor,
        surface      = bgColor,
        onBackground = onBgColor,
        onSurface    = onBgColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
