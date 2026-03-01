package com.opic.android.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 하단 네비게이션 바에서 → 버튼의 동작을 화면별로 지정하기 위한 상태 홀더.
 * 각 화면이 자신의 forwardAction을 설정하면, OPicBottomBar의 → 버튼이 이를 호출.
 */
class BottomNavState {
    var forwardAction: (() -> Unit)? by mutableStateOf(null)
}

val LocalBottomNavState = compositionLocalOf { BottomNavState() }
