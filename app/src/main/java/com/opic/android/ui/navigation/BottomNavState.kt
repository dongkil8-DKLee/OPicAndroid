package com.opic.android.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 하단 네비게이션 바 상태 홀더.
 * 모든 화면에서 공통 5버튼: < Back | Study | Home | Test | Next >
 * backAction/nextAction은 화면별 커스텀 동작, homeAction은 테스트 플로우에서 확인 다이얼로그용.
 */
class BottomNavState {
    var backAction: (() -> Unit)? by mutableStateOf(null)
    var homeAction: (() -> Unit)? by mutableStateOf(null)
    var nextAction: (() -> Unit)? by mutableStateOf(null)
    var nextEnabled: Boolean by mutableStateOf(true)
}

val LocalBottomNavState = compositionLocalOf { BottomNavState() }
