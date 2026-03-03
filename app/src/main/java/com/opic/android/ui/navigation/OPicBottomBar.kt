package com.opic.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.opic.android.ui.theme.OPicColors

/** 테스트 플로우 라우트 */
private val testFlowRoutes = setOf(
    Screen.Survey.route,
    Screen.SelfAssessment.route,
    Screen.Test.route
)

private fun isTestFlowRoute(route: String?): Boolean {
    if (route == null) return false
    return testFlowRoutes.any { pattern ->
        route == pattern || route.startsWith(pattern.substringBefore("{"))
    }
}

/**
 * 하단 네비게이션 바 — Pill 스타일
 *
 * 레이아웃: ← Back | Study | Home | Test | Next →
 * - 바 배경: NavBarBg (인디고 / 다크 슬레이트)
 * - 현재 탭: 골드 텍스트 + 반투명 골드 pill 배경
 * - 일반 탭: 흰색/뮤트 텍스트
 * - 비활성: 30% 투명도
 */
@Composable
fun OPicBottomBar(navController: NavHostController) {
    val bottomNavState = LocalBottomNavState.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val visibleEntries by navController.visibleEntries.collectAsState()
    val isTestFlow = visibleEntries.any { isTestFlowRoute(it.destination.route) }
            || isTestFlowRoute(currentRoute)

    val isOnHome  = currentRoute == Screen.Report.route
    val isOnStudy = currentRoute?.startsWith("StudyScreen") == true
            || currentRoute?.startsWith("PracticeScreen") == true
    val isOnTest  = isTestFlowRoute(currentRoute)
            || currentRoute?.startsWith("TestScreen") == true
            || currentRoute?.startsWith("BeginTestPage") == true
            || currentRoute == Screen.SelfAssessment.route

    val isOwner = bottomNavState.isOwnerRoute(currentRoute)
    val effectiveBackAction = if (isOwner) bottomNavState.backAction else null
    val effectiveNextAction = if (isOwner) bottomNavState.nextAction else null

    val canGoBack = effectiveBackAction != null ||
            (!isOnHome && !isTestFlow && navController.previousBackStackEntry != null)
    val canGoNext = effectiveNextAction != null && bottomNavState.nextEnabled

    val middleEnabled = !isTestFlow

    val lastClickTime = remember { longArrayOf(0L) }
    fun debounced(action: () -> Unit): () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastClickTime[0] > 300) {
            lastClickTime[0] = now
            action()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(OPicColors.NavBarBg)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        NavPill(
            text = "← Back",
            enabled = canGoBack,
            isActive = false,
            onClick = debounced {
                if (effectiveBackAction != null) effectiveBackAction.invoke()
                else if (navController.previousBackStackEntry != null) navController.popBackStack()
            },
            modifier = Modifier.weight(1.2f)
        )

        NavPill(
            text = "Study",
            enabled = middleEnabled,
            isActive = isOnStudy && middleEnabled,
            onClick = debounced {
                navController.navigate(Screen.Study.createRoute()) {
                    popUpTo(Screen.Report.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            modifier = Modifier.weight(1f)
        )

        NavPill(
            text = "Home",
            enabled = middleEnabled,
            isActive = isOnHome && middleEnabled,
            onClick = debounced {
                navController.popBackStack(Screen.Report.route, inclusive = false)
            },
            modifier = Modifier.weight(1f)
        )

        NavPill(
            text = "Test",
            enabled = middleEnabled,
            isActive = isOnTest && middleEnabled,
            onClick = debounced {
                navController.navigate(Screen.Survey.route) {
                    popUpTo(Screen.Report.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            modifier = Modifier.weight(1f)
        )

        NavPill(
            text = "Next →",
            enabled = canGoNext,
            isActive = false,
            onClick = debounced { effectiveNextAction?.invoke() },
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun NavPill(
    text: String,
    enabled: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillBg = when {
        isActive  -> OPicColors.NavTextSelected.copy(alpha = 0.18f)
        else      -> Color.Transparent
    }
    val textColor = when {
        !enabled  -> OPicColors.NavTextNormal.copy(alpha = 0.30f)
        isActive  -> OPicColors.NavTextSelected
        else      -> OPicColors.NavTextNormal
    }
    val weight = if (isActive) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50.dp))
            .background(pillBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = weight,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
