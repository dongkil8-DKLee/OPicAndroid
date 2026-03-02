package com.opic.android.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.opic.android.ui.theme.OPicColors

/** 활성 주황색 */
private val EnabledBg = OPicColors.Primary          // 0xFFFF5733
/** 비활성 연한 주황색 */
private val DisabledBg = Color(0xFFFFAB91)
private val ButtonTextWhite = Color.White
private val ButtonTextBlack = Color.Black

/** 테스트 플로우 라우트 (Survey, SelfAssessment, Test) */
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
 * 모든 화면 공통 하단바: < Back | Study | Home | Test | Next >
 *
 * - < Back, Next > 항상 좌/우 고정
 * - 테스트 플로우: Study, Home, Test 비활성화 (연한 주황색)
 * - 일반 화면: 현재 탭 = 주황 배경 + 검은 글씨, 나머지 = 주황 배경 + 흰 글씨
 * - 비활성화 = 연한 주황 배경
 */
@Composable
fun OPicBottomBar(navController: NavHostController) {
    val bottomNavState = LocalBottomNavState.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 테스트 플로우 감지 (전환 애니메이션 중에도 감지)
    val visibleEntries by navController.visibleEntries.collectAsState()
    val isTestFlow = visibleEntries.any { isTestFlowRoute(it.destination.route) }
            || isTestFlowRoute(currentRoute)

    val isOnHome = currentRoute == Screen.Report.route

    // ★ stale 액션 방지: 테스트 플로우에서만 BottomNavState의 backAction/nextAction 사용
    // 테스트 플로우 밖에서는 이전 화면의 잔존 액션이 실행되지 않도록 무시
    val effectiveBackAction = if (isTestFlow) bottomNavState.backAction else null
    val effectiveNextAction = if (isTestFlow) bottomNavState.nextAction else null

    // < Back 활성 여부
    val canGoBack = effectiveBackAction != null ||
            (!isOnHome && !isTestFlow && navController.previousBackStackEntry != null)

    // Next > 활성 여부
    val canGoNext = effectiveNextAction != null && bottomNavState.nextEnabled

    // 현재 화면이 어떤 탭인지 (현재 탭 = 검은 글씨)
    val isOnStudy = currentRoute?.startsWith("StudyScreen") == true
    val isOnTest = currentRoute == Screen.Survey.route  // Test 탭 = Survey 진입점

    // 테스트 플로우에서는 Study/Home/Test 비활성화
    val middleEnabled = !isTestFlow

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ===== < Back (좌측 고정) =====
        BarButton(
            text = "< Back",
            enabled = canGoBack,
            isCurrentTab = false,
            onClick = {
                effectiveBackAction?.invoke()
                    ?: navController.popBackStack()
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Study =====
        BarButton(
            text = "Study",
            enabled = middleEnabled,
            isCurrentTab = isOnStudy && middleEnabled,
            onClick = {
                navController.navigate(Screen.Study.createRoute()) {
                    popUpTo(Screen.Report.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Home (= 이전 Report 탭, 항상 Report로 직접 네비게이션) =====
        BarButton(
            text = "Home",
            enabled = middleEnabled,
            isCurrentTab = isOnHome && middleEnabled,
            onClick = {
                navController.popBackStack(Screen.Report.route, inclusive = false)
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Test =====
        BarButton(
            text = "Test",
            enabled = middleEnabled,
            isCurrentTab = isOnTest && middleEnabled,
            onClick = {
                navController.navigate(Screen.Survey.route) {
                    popUpTo(Screen.Report.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Next > (우측 고정) =====
        BarButton(
            text = "Next >",
            enabled = canGoNext,
            isCurrentTab = false,
            onClick = { effectiveNextAction?.invoke() },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BarButton(
    text: String,
    enabled: Boolean,
    isCurrentTab: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = EnabledBg,
            contentColor = if (isCurrentTab) ButtonTextBlack else ButtonTextWhite,
            disabledContainerColor = DisabledBg,
            disabledContentColor = ButtonTextWhite.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = modifier.height(40.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
