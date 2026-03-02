package com.opic.android.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 하단 네비게이션 바 상태 홀더.
 * - 일반 화면: forwardAction (→ 버튼)
 * - Survey/SelfAssessment/Test: backAction, homeAction, nextAction (< Back | Home | Next >)
 */
class BottomNavState {
    var forwardAction: (() -> Unit)? by mutableStateOf(null)

    // 테스트 플로우 화면용 액션
    var backAction: (() -> Unit)? by mutableStateOf(null)
    var homeAction: (() -> Unit)? by mutableStateOf(null)
    var nextAction: (() -> Unit)? by mutableStateOf(null)
    var nextEnabled: Boolean by mutableStateOf(true)
}

val LocalBottomNavState = compositionLocalOf { BottomNavState() }
