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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.opic.android.ui.theme.OPicColors

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
 * - 테스트 플로우: Study, Home, Test 비활성화
 * - 현재 탭 = NavTextSelected(Light:검정/Dark:골드), 나머지 = NavTextNormal
 * - Study 탭: StudyScreen + PracticeScreen
 * - Test 탭: SurveyPage + SelfAssessmentPage + BeginTestPage + TestScreen
 * - Home 탭: ReportScreen
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

    val isOnHome  = currentRoute == Screen.Report.route
    val isOnStudy = currentRoute?.startsWith("StudyScreen") == true
            || currentRoute?.startsWith("PracticeScreen") == true
    val isOnTest  = isTestFlowRoute(currentRoute)
            || currentRoute?.startsWith("TestScreen") == true
            || currentRoute?.startsWith("BeginTestPage") == true
            || currentRoute == Screen.SelfAssessment.route

    // ★ stale 액션 방지: ownerRoute가 현재 화면과 일치할 때만 액션 사용
    val isOwner = bottomNavState.isOwnerRoute(currentRoute)
    val effectiveBackAction = if (isOwner) bottomNavState.backAction else null
    val effectiveNextAction = if (isOwner) bottomNavState.nextAction else null

    // < Back 활성 여부
    val canGoBack = effectiveBackAction != null ||
            (!isOnHome && !isTestFlow && navController.previousBackStackEntry != null)

    // Next > 활성 여부
    val canGoNext = effectiveNextAction != null && bottomNavState.nextEnabled

    // 테스트 플로우에서는 Study/Home/Test 비활성화
    val middleEnabled = !isTestFlow

    // 빠른 연속 클릭 방지 (300ms 디바운스)
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
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ===== < Back (좌측 고정) =====
        BarButton(
            text = "< Back",
            enabled = canGoBack,
            isCurrentTab = false,
            onClick = debounced {
                if (effectiveBackAction != null) {
                    effectiveBackAction.invoke()
                } else if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Study =====
        BarButton(
            text = "Study",
            enabled = middleEnabled,
            isCurrentTab = isOnStudy && middleEnabled,
            onClick = debounced {
                navController.navigate(Screen.Study.createRoute()) {
                    popUpTo(Screen.Report.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Home =====
        BarButton(
            text = "Home",
            enabled = middleEnabled,
            isCurrentTab = isOnHome && middleEnabled,
            onClick = debounced {
                navController.popBackStack(Screen.Report.route, inclusive = false)
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Test =====
        BarButton(
            text = "Test",
            enabled = middleEnabled,
            isCurrentTab = isOnTest && middleEnabled,
            onClick = debounced {
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
            onClick = debounced { effectiveNextAction?.invoke() },
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
            containerColor        = OPicColors.NavBarBg,
            contentColor          = if (isCurrentTab) OPicColors.NavTextSelected else OPicColors.NavTextNormal,
            disabledContainerColor = OPicColors.NavDisabledBg,
            disabledContentColor  = OPicColors.NavTextNormal.copy(alpha = 0.5f)
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
