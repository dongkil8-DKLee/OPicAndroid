package com.opic.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Midnight Academy" 타이포그래피 스케일
 * 시스템 폰트 기반 — 별도 폰트 다운로드 없음
 */
private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        lineHeight  = 28.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 18.sp,
        lineHeight  = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 16.sp,
        lineHeight  = 22.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight  = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 13.sp,
        lineHeight  = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight  = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// OPIc brand colors (StudyDetail 다크 패널 전용)
val OPicBlue = Color(0xFF4F46E5)
val OPicLightBlue = Color(0xFF6366F1)
val OPicAccent = Color(0xFF0891B2)
val OPicGreen = Color(0xFF10B981)
val OPicRed = Color(0xFFEF4444)

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
    primary             = Color(0xFF2D2B8F),
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFFEEEDF8),
    onPrimaryContainer  = Color(0xFF1A1740),
    secondary           = Color(0xFF0369A1),
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0D1929),
    background          = Color(0xFFF8F9FE),
    surface             = Color(0xFFFFFFFF),
    surfaceVariant      = Color(0xFFEEEDF8),
    onBackground        = Color(0xFF1A1740),
    onSurface           = Color(0xFF1A1740),
    onSurfaceVariant    = Color(0xFF2D2B8F),
    error               = Color(0xFFDC2626),
    onError             = Color.White,
    outline             = Color(0xFFDDE0F0)
)

private val DarkColorScheme = darkColorScheme(
    primary             = Color(0xFF7C74E8),
    onPrimary           = Color(0xFF080C18),
    primaryContainer    = Color(0xFF1C2438),
    onPrimaryContainer  = Color(0xFFE8EAF6),
    secondary           = Color(0xFF38BDF8),
    onSecondary         = Color(0xFF080C18),
    secondaryContainer  = Color(0xFF0D1929),
    onSecondaryContainer = Color(0xFFE8EAF6),
    background          = Color(0xFF080C18),
    surface             = Color(0xFF111827),
    surfaceVariant      = Color(0xFF1C2438),
    onBackground        = Color(0xFFE8EAF6),
    onSurface           = Color(0xFFE8EAF6),
    onSurfaceVariant    = Color(0xFF9E98F0),
    error               = Color(0xFFF87171),
    onError             = Color(0xFF080C18),
    outline             = Color(0xFF242B45)
)

@Composable
fun OPicTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,  // 커스텀 색상 우선 — dynamic color 사용 안 함
    content: @Composable () -> Unit
) {
    val bgColor   = if (darkTheme) Color(0xFF080C18) else Color(0xFFF8F9FE)
    val onBgColor = if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF1E1B4B)

    val colorScheme = (if (darkTheme) DarkColorScheme else LightColorScheme).copy(
        background   = bgColor,
        onBackground = onBgColor,
        onSurface    = onBgColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
