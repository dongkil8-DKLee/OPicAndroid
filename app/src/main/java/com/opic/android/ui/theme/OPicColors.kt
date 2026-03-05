package com.opic.android.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 앱 색상 팔레트 — "Midnight Academy" 프리미엄 테마 (Deep Navy + Warm Gold)
 * mutableStateOf → 테마 전환 시 Compose 자동 recompose (모든 화면 즉시 반영)
 *
 * Light: 딥 네이비 프라이머리 + 쿨 화이트 배경 + 웜 골드 강조
 * Dark : 거의 블랙 네이비 배경 + 소프트 인디고 프라이머리 + 웜 골드 강조
 */
object OPicColors {
    // === Primary ===
    var Primary      by mutableStateOf(Color(0xFF2D2B8F))  // Deep Indigo Navy
    var PrimaryText  by mutableStateOf(Color.White)

    // === Secondary / Selection ===
    var Secondary    by mutableStateOf(Color(0xFFEEEDF8))  // Soft indigo tint
    var Surface      by mutableStateOf(Color(0xFFFFFFFF))  // 카드 배경

    // === 기능별 텍스트 ===
    var StudyText    by mutableStateOf(Color(0xFF2D2B8F))
    var ReviewText   by mutableStateOf(Color(0xFFDC2626))

    // === Audio Controls ===
    var PlayButton   by mutableStateOf(Color(0xFF0369A1))  // Deep Sky Blue
    var StopButton   by mutableStateOf(Color(0xFFE0F2FE))
    var RecordActive by mutableStateOf(Color(0xFFDC2626))

    // === Progress & Timer ===
    var LevelGauge   by mutableStateOf(Color(0xFF4C47C4))
    var TimerGreen   by mutableStateOf(Color(0xFF059669))  // Deep Emerald
    var TimerOrange  by mutableStateOf(Color(0xFFD97706))
    var TimerRed     by mutableStateOf(Color(0xFFDC2626))

    // === UI Surface ===
    var LightBg      by mutableStateOf(Color(0xFFEEEDF8))  // 행 선택, 패널 배경
    var DarkBg       by mutableStateOf(Color(0xFF16143A))  // 다크 패널
    var DisabledBg   by mutableStateOf(Color(0xFFB8BAE8))
    var Border       by mutableStateOf(Color(0xFFDDE0F0))
    var TextOnLight  by mutableStateOf(Color(0xFF1A1740))  // Deep navy-black
    var TextOnDark   by mutableStateOf(Color(0xFFE8EAF6))

    // === Question Grid ===
    var GridCurrent  by mutableStateOf(Color(0xFF2D2B8F))
    var GridDefault  by mutableStateOf(Color(0xFFEEEDF8))
    var GridAnswered by mutableStateOf(Color(0xFF059669))

    // === 하단 네비게이션 ===
    var NavBarBg        by mutableStateOf(Color(0xFF16143A))  // Near-black navy
    var NavTextSelected by mutableStateOf(Color(0xFFFFD166))  // Warm Gold
    var NavTextNormal   by mutableStateOf(Color(0xFFCDD0E8))
    var NavDisabledBg   by mutableStateOf(Color(0xFF3D3A72))

    fun applyLight() {
        Primary = Color(0xFF2D2B8F); PrimaryText = Color.White
        Secondary = Color(0xFFEEEDF8); Surface = Color(0xFFFFFFFF)
        StudyText = Color(0xFF2D2B8F); ReviewText = Color(0xFFDC2626)
        PlayButton = Color(0xFF0369A1); StopButton = Color(0xFFE0F2FE)
        RecordActive = Color(0xFFDC2626)
        LevelGauge = Color(0xFF4C47C4)
        TimerGreen = Color(0xFF059669); TimerOrange = Color(0xFFD97706)
        TimerRed = Color(0xFFDC2626)
        LightBg = Color(0xFFEEEDF8); DarkBg = Color(0xFF16143A)
        DisabledBg = Color(0xFFB8BAE8); Border = Color(0xFFDDE0F0)
        TextOnLight = Color(0xFF1A1740); TextOnDark = Color(0xFFE8EAF6)
        GridCurrent = Color(0xFF2D2B8F); GridDefault = Color(0xFFEEEDF8)
        GridAnswered = Color(0xFF059669)
        NavBarBg = Color(0xFF16143A); NavTextSelected = Color(0xFFFFD166)
        NavTextNormal = Color(0xFFCDD0E8); NavDisabledBg = Color(0xFF3D3A72)
    }

    fun applyDark() {
        Primary = Color(0xFF7C74E8); PrimaryText = Color(0xFFE8EAF6)
        Secondary = Color(0xFF1C2438); Surface = Color(0xFF111827)
        StudyText = Color(0xFF9E98F0); ReviewText = Color(0xFFF87171)
        PlayButton = Color(0xFF38BDF8); StopButton = Color(0xFF0D1929)
        RecordActive = Color(0xFFF87171)
        LevelGauge = Color(0xFF7C74E8)
        TimerGreen = Color(0xFF34D399); TimerOrange = Color(0xFFFBBF24)
        TimerRed = Color(0xFFF87171)
        LightBg = Color(0xFF1C2438); DarkBg = Color(0xFF080C18)
        DisabledBg = Color(0xFF3D4B6A); Border = Color(0xFF242B45)
        TextOnLight = Color(0xFFE8EAF6); TextOnDark = Color(0xFFE8EAF6)
        GridCurrent = Color(0xFF7C74E8); GridDefault = Color(0xFF1C2438)
        GridAnswered = Color(0xFF34D399)
        NavBarBg = Color(0xFF0A0D1A); NavTextSelected = Color(0xFFFFD166)
        NavTextNormal = Color(0xFF8892B0); NavDisabledBg = Color(0xFF242B45)
    }
}
