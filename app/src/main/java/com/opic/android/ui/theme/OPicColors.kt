package com.opic.android.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 앱 색상 팔레트 — 어학 학습 앱 전문 디자인 (Indigo 기반)
 * mutableStateOf → 테마 전환 시 Compose 자동 recompose
 */
object OPicColors {
    // === Primary ===
    var Primary      by mutableStateOf(Color(0xFF4F46E5))  // Vivid Indigo
    var PrimaryText  by mutableStateOf(Color.White)

    // === Secondary / Selection ===
    var Secondary    by mutableStateOf(Color(0xFFEEF2FF))  // Indigo 50
    var Surface      by mutableStateOf(Color(0xFFFFFFFF))  // 카드 배경

    // === 기능별 텍스트 ===
    var StudyText    by mutableStateOf(Color(0xFF4F46E5))
    var ReviewText   by mutableStateOf(Color(0xFFEF4444))

    // === Audio Controls ===
    var PlayButton   by mutableStateOf(Color(0xFF0891B2))  // Cyan 600
    var StopButton   by mutableStateOf(Color(0xFFE0F2FE))  // Cyan 50
    var RecordActive by mutableStateOf(Color(0xFFEF4444))

    // === Progress & Timer ===
    var LevelGauge   by mutableStateOf(Color(0xFF6366F1))
    var TimerGreen   by mutableStateOf(Color(0xFF10B981))
    var TimerOrange  by mutableStateOf(Color(0xFFF59E0B))
    var TimerRed     by mutableStateOf(Color(0xFFEF4444))

    // === UI Surface ===
    var LightBg      by mutableStateOf(Color(0xFFEEF2FF))  // 행 선택, 패널 배경
    var DarkBg       by mutableStateOf(Color(0xFF1E1B4B))
    var DisabledBg   by mutableStateOf(Color(0xFFC7D2FE))  // Indigo 200
    var Border       by mutableStateOf(Color(0xFFE0E7FF))  // Indigo 100
    var TextOnLight  by mutableStateOf(Color(0xFF1E1B4B))  // Deep Indigo
    var TextOnDark   by mutableStateOf(Color(0xFFE2E8F0))

    // === Question Grid ===
    var GridCurrent  by mutableStateOf(Color(0xFF4F46E5))
    var GridDefault  by mutableStateOf(Color(0xFFEEF2FF))
    var GridAnswered by mutableStateOf(Color(0xFF10B981))

    // === 하단 네비게이션 ===
    var NavBarBg        by mutableStateOf(Color(0xFF4F46E5))
    var NavTextSelected by mutableStateOf(Color(0xFFFDE68A))  // Warm Gold
    var NavTextNormal   by mutableStateOf(Color.White)
    var NavDisabledBg   by mutableStateOf(Color(0xFF818CF8))  // Indigo 400

    fun applyLight() {
        Primary = Color(0xFF4F46E5); PrimaryText = Color.White
        Secondary = Color(0xFFEEF2FF); Surface = Color(0xFFFFFFFF)
        StudyText = Color(0xFF4F46E5); ReviewText = Color(0xFFEF4444)
        PlayButton = Color(0xFF0891B2); StopButton = Color(0xFFE0F2FE)
        RecordActive = Color(0xFFEF4444)
        LevelGauge = Color(0xFF6366F1)
        TimerGreen = Color(0xFF10B981); TimerOrange = Color(0xFFF59E0B)
        TimerRed = Color(0xFFEF4444)
        LightBg = Color(0xFFEEF2FF); DarkBg = Color(0xFF1E1B4B)
        DisabledBg = Color(0xFFC7D2FE); Border = Color(0xFFE0E7FF)
        TextOnLight = Color(0xFF1E1B4B); TextOnDark = Color(0xFFE2E8F0)
        GridCurrent = Color(0xFF4F46E5); GridDefault = Color(0xFFEEF2FF)
        GridAnswered = Color(0xFF10B981)
        NavBarBg = Color(0xFF4F46E5); NavTextSelected = Color(0xFFFDE68A)
        NavTextNormal = Color.White; NavDisabledBg = Color(0xFF818CF8)
    }

    fun applyDark() {
        Primary = Color(0xFF818CF8); PrimaryText = Color(0xFFE2E8F0)
        Secondary = Color(0xFF1E293B); Surface = Color(0xFF1E293B)
        StudyText = Color(0xFFA5B4FC); ReviewText = Color(0xFFF87171)
        PlayButton = Color(0xFF22D3EE); StopButton = Color(0xFF0F2D3D)
        RecordActive = Color(0xFFF87171)
        LevelGauge = Color(0xFF818CF8)
        TimerGreen = Color(0xFF34D399); TimerOrange = Color(0xFFFBBF24)
        TimerRed = Color(0xFFF87171)
        LightBg = Color(0xFF263450); DarkBg = Color(0xFF0F172A)   // 선택 강조: 배경과 구분
        DisabledBg = Color(0xFF475569); Border = Color(0xFF334155)
        TextOnLight = Color(0xFFE2E8F0); TextOnDark = Color(0xFFE2E8F0)
        GridCurrent = Color(0xFF818CF8); GridDefault = Color(0xFF1E293B)
        GridAnswered = Color(0xFF34D399)
        NavBarBg = Color(0xFF1E293B); NavTextSelected = Color(0xFFFDE68A)
        NavTextNormal = Color(0xFF94A3B8); NavDisabledBg = Color(0xFF334155)
    }
}
