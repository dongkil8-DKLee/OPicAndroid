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

    // stale 액션 방지용: 현재 액션의 "소유 화면(route)" 기록
    var ownerRoute: String? by mutableStateOf(null)

    private fun routeBase(route: String?): String? {
        if (route.isNullOrBlank()) return null
        return route.substringBefore("{")
    }

    fun setOwnerActions(
        ownerRoute: String,
        back: (() -> Unit)?,
        home: (() -> Unit)?,
        next: (() -> Unit)?,
        nextEnabled: Boolean = true
    ) {
        val base = routeBase(ownerRoute)
        this.ownerRoute = base
        this.backAction = back
        this.homeAction = home
        this.nextAction = next
        this.nextEnabled = nextEnabled
    }

    fun isOwnerRoute(currentRoute: String?): Boolean {
        val baseOwner = routeBase(ownerRoute) ?: return false
        val baseCurrent = routeBase(currentRoute) ?: return false
        return baseCurrent.startsWith(baseOwner)
    }

    // 화면 전환 애니메이션 레이스 방지:
    // "내가 owner일 때만" clear 해서 새 화면 액션을 지우지 않도록 함
    fun clearOwnerActions(ownerRoute: String) {
        val base = routeBase(ownerRoute) ?: return
        val currentOwner = routeBase(this.ownerRoute) ?: return
        if (currentOwner != base) return

        this.ownerRoute = null
        this.backAction = null
        this.homeAction = null
        this.nextAction = null
        this.nextEnabled = true
    }
}

val LocalBottomNavState = compositionLocalOf { BottomNavState() }
