package com.opic.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

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
    background = Color(0xFFFAFAFF),
    surface = Color(0xFFFAFAFF),
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
    error = OPicRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA3C4FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00469B),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3C4858),
    onSecondaryContainer = Color(0xFFD8E3F8),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun OPicTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
