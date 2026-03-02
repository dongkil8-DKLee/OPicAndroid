package com.opic.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private val BarBackground = Color(0xFFCCFFFF)
private val ActiveTabColor = Color(0xFF0033FF)
private val DefaultTabColor = Color.Black
private val ArrowEnabledColor = Color.Black
private val ArrowDisabledColor = Color.Gray

/** Survey / SelfAssessment / Test 라우트에서는 하단 탭바 대신 Back/Home/Next 표시 */
private val hiddenRoutes = setOf(
    Screen.Survey.route,
    Screen.SelfAssessment.route,
    Screen.Test.route
)

private fun isHiddenRoute(route: String?): Boolean {
    if (route == null) return false
    return hiddenRoutes.any { pattern ->
        route == pattern || route.startsWith(pattern.substringBefore("{"))
    }
}

@Composable
fun OPicBottomBar(navController: NavHostController) {
    // visibleEntries: 전환 애니메이션 중 보이는 모든 화면 포함
    val visibleEntries by navController.visibleEntries.collectAsState()
    val hasHiddenVisible = visibleEntries.any { isHiddenRoute(it.destination.route) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isHidden = hasHiddenVisible || isHiddenRoute(currentRoute)

    if (isHidden) {
        // ===== 테스트 플로우: < Back | Home | Next > =====
        TestFlowBottomBar()
    } else {
        // ===== 일반: ← Report Study Test → =====
        TabBottomBar(navController, currentRoute)
    }
}

// ==================== 테스트 플로우 하단바 ====================

@Composable
private fun TestFlowBottomBar() {
    val bottomNavState = LocalBottomNavState.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(BarBackground),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // < Back
        Button(
            onClick = { bottomNavState.backAction?.invoke() },
            enabled = bottomNavState.backAction != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = OPicColors.Primary,
                contentColor = OPicColors.PrimaryText
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Text("< Back", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // Home (homeAction이 있을 때만 표시)
        if (bottomNavState.homeAction != null) {
            Button(
                onClick = { bottomNavState.homeAction?.invoke() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OPicColors.Secondary,
                    contentColor = OPicColors.TextOnLight
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(Icons.Filled.Home, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Home", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Next >
        Button(
            onClick = { bottomNavState.nextAction?.invoke() },
            enabled = bottomNavState.nextAction != null && bottomNavState.nextEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = OPicColors.Primary,
                contentColor = OPicColors.PrimaryText
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Text("Next >", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// ==================== 일반 탭 하단바 ====================

@Composable
private fun TabBottomBar(navController: NavHostController, currentRoute: String?) {
    val bottomNavState = LocalBottomNavState.current

    data class TabItem(val label: String, val route: String, val matchPrefix: String = route)

    val tabs = listOf(
        TabItem("Report", Screen.Report.route),
        TabItem("Study", Screen.Study.createRoute(), matchPrefix = "StudyScreen"),
        TabItem("Test", Screen.Survey.route)
    )

    // ← 가능 여부: Report(시작화면)이 아니면 뒤로 갈 수 있음
    val canGoBack = currentRoute != null && currentRoute != Screen.Report.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(BarBackground),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // ← 버튼
        TextButton(
            onClick = { navController.popBackStack() },
            enabled = canGoBack,
            modifier = Modifier.weight(0.8f)
        ) {
            Text(
                text = "←",
                fontSize = 16.sp,
                fontWeight = if (canGoBack) FontWeight.Bold else FontWeight.Normal,
                color = if (canGoBack) ArrowEnabledColor else ArrowDisabledColor
            )
        }

        // 탭 버튼들
        tabs.forEach { tab ->
            val isActive = currentRoute == tab.route ||
                    (currentRoute != null && currentRoute.startsWith(tab.matchPrefix))
            TextButton(
                onClick = {
                    if (!isActive) {
                        navController.navigate(tab.route) {
                            popUpTo(Screen.Report.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tab.label,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) ActiveTabColor else DefaultTabColor
                )
            }
        }

        // → 버튼
        val hasForward = bottomNavState.forwardAction != null
        TextButton(
            onClick = { bottomNavState.forwardAction?.invoke() },
            enabled = hasForward,
            modifier = Modifier.weight(0.8f)
        ) {
            Text(
                text = "→",
                fontSize = 16.sp,
                fontWeight = if (hasForward) FontWeight.Bold else FontWeight.Normal,
                color = if (hasForward) ArrowEnabledColor else ArrowDisabledColor
            )
        }
    }
}
