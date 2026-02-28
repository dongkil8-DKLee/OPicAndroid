package com.opic.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Python PyQt5 앱의 색상 체계 1:1 매핑.
 * UI_STANDARD.md §2.1 참조.
 */
object OPicColors {
    // === Primary Actions ===
    val Primary       = Color(0xFFFF5733)  // Next, Start Test, Back 버튼
    val PrimaryText   = Color.White

    // === Secondary Actions ===
    val Secondary     = Color(0xFFDDDCE9)  // Study, Review 버튼 배경
    val StudyText     = Color(0xFF0000FF)  // Study 버튼 텍스트 (blue)
    val ReviewText    = Color(0xFFFF0000)  // Review 버튼 텍스트 (red)

    // === Audio Controls ===
    val PlayButton    = Color(0xFFFF8C69)
    val StopButton    = Color(0xFFFFCDD2)
    val RecordActive  = Color.Red

    // === Progress & Timer ===
    val LevelGauge    = Color(0xFF05B8CC)
    val TimerGreen    = Color(0xFF2ECC71)
    val TimerOrange   = Color(0xFFF39C12)
    val TimerRed      = Color(0xFFE74C3C)

    // === UI Surface ===
    val LightBg       = Color(0xFFF0F0F0)
    val DarkBg        = Color(0xFF333333)
    val DisabledBg    = Color(0xFFD3D3D3)
    val Border        = Color(0xFFCCCCCC)
    val TextOnLight   = Color(0xFF333333)
    val TextOnDark    = Color(0xFFF5EEDC)

    // === Question Grid (TestScreen) ===
    val GridCurrent   = Color(0xFF333333)
    val GridDefault   = Color(0xFFEEEEEE)
    val GridAnswered  = Color(0xFF2ECC71)
}
