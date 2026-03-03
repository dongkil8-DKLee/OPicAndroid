package com.opic.android.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 앱 색상 팔레트.
 * mutableStateOf → 테마 전환 시 Compose 자동 recompose (기존 화면 파일 변경 불필요).
 */
object OPicColors {
    // === Primary Actions ===
    var Primary      by mutableStateOf(Color(0xFF1D4ED8))
    var PrimaryText  by mutableStateOf(Color.White)

    // === Secondary Actions ===
    var Secondary    by mutableStateOf(Color(0xFFEFF6FF))
    var StudyText    by mutableStateOf(Color(0xFF1D4ED8))
    var ReviewText   by mutableStateOf(Color(0xFFDC2626))

    // === Audio Controls ===
    var PlayButton   by mutableStateOf(Color(0xFF2563EB))
    var StopButton   by mutableStateOf(Color(0xFFBFDBFE))
    var RecordActive by mutableStateOf(Color(0xFFDC2626))

    // === Progress & Timer ===
    var LevelGauge   by mutableStateOf(Color(0xFF60A5FA))
    var TimerGreen   by mutableStateOf(Color(0xFF16A34A))
    var TimerOrange  by mutableStateOf(Color(0xFFD97706))
    var TimerRed     by mutableStateOf(Color(0xFFDC2626))

    // === UI Surface ===
    var LightBg      by mutableStateOf(Color(0xFFF1F5F9))
    var DarkBg       by mutableStateOf(Color(0xFF1E293B))
    var DisabledBg   by mutableStateOf(Color(0xFFCBD5E1))
    var Border       by mutableStateOf(Color(0xFFE2E8F0))
    var TextOnLight  by mutableStateOf(Color(0xFF0F172A))
    var TextOnDark   by mutableStateOf(Color(0xFFF1F5F9))

    // === Question Grid (TestScreen) ===
    var GridCurrent  by mutableStateOf(Color(0xFF1D4ED8))
    var GridDefault  by mutableStateOf(Color(0xFFEFF6FF))
    var GridAnswered by mutableStateOf(Color(0xFF16A34A))

    // === 하단 네비게이션 전용 ===
    var NavBarBg        by mutableStateOf(Color(0xFF1D4ED8))
    var NavTextSelected by mutableStateOf(Color(0xFF000000))
    var NavTextNormal   by mutableStateOf(Color.White)
    var NavDisabledBg   by mutableStateOf(Color(0xFFBFDBFE))

    fun applyLight() {
        Primary = Color(0xFF1D4ED8); PrimaryText = Color.White
        Secondary = Color(0xFFEFF6FF); StudyText = Color(0xFF1D4ED8)
        ReviewText = Color(0xFFDC2626); PlayButton = Color(0xFF2563EB)
        StopButton = Color(0xFFBFDBFE); RecordActive = Color(0xFFDC2626)
        LevelGauge = Color(0xFF60A5FA); TimerGreen = Color(0xFF16A34A)
        TimerOrange = Color(0xFFD97706); TimerRed = Color(0xFFDC2626)
        LightBg = Color(0xFFF1F5F9); DarkBg = Color(0xFF1E293B)
        DisabledBg = Color(0xFFCBD5E1); Border = Color(0xFFE2E8F0)
        TextOnLight = Color(0xFF0F172A); TextOnDark = Color(0xFFF1F5F9)
        GridCurrent = Color(0xFF1D4ED8); GridDefault = Color(0xFFEFF6FF)
        GridAnswered = Color(0xFF16A34A)
        NavBarBg = Color(0xFF1D4ED8); NavTextSelected = Color.Black
        NavTextNormal = Color.White; NavDisabledBg = Color(0xFFBFDBFE)
    }

    fun applyDark() {
        Primary = Color(0xFF3B82F6); PrimaryText = Color(0xFFF1F5F9)
        Secondary = Color(0xFF1E293B); StudyText = Color(0xFF93C5FD)
        ReviewText = Color(0xFFF87171); PlayButton = Color(0xFF3B82F6)
        StopButton = Color(0xFF1E293B); RecordActive = Color(0xFFEF4444)
        LevelGauge = Color(0xFF60A5FA); TimerGreen = Color(0xFF4ADE80)
        TimerOrange = Color(0xFFFBBF24); TimerRed = Color(0xFFF87171)
        LightBg = Color(0xFF1E293B); DarkBg = Color(0xFF0F172A)
        DisabledBg = Color(0xFF475569); Border = Color(0xFF334155)
        TextOnLight = Color(0xFFF1F5F9); TextOnDark = Color(0xFFF1F5F9)
        GridCurrent = Color(0xFF3B82F6); GridDefault = Color(0xFF1E293B)
        GridAnswered = Color(0xFF4ADE80)
        NavBarBg = Color(0xFF1E293B); NavTextSelected = Color(0xFFFBBF24)
        NavTextNormal = Color(0xFF94A3B8); NavDisabledBg = Color(0xFF334155)
    }
}
