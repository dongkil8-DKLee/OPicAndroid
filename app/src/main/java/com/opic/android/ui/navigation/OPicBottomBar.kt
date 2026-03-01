package com.opic.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

private val BarBackground = Color(0xFFCCFFFF)
private val ActiveTabColor = Color(0xFF0033FF)
private val DefaultTabColor = Color.Black
private val ArrowEnabledColor = Color.Black
private val ArrowDisabledColor = Color.Gray

/** Survey / SelfAssessment / Test 라우트에서는 하단 탭바 숨김 (자체 Back/Home/Next 사용) */
private val hiddenRoutes = setOf(
    Screen.Survey.route,
    Screen.SelfAssessment.route,
    Screen.Test.route
)

private fun shouldHide(currentRoute: String?): Boolean {
    if (currentRoute == null) return false
    return hiddenRoutes.any { pattern ->
        currentRoute == pattern || currentRoute.startsWith(pattern.substringBefore("{"))
    }
}

@Composable
fun OPicBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    if (shouldHide(currentRoute)) return

    val bottomNavState = LocalBottomNavState.current

    data class TabItem(val label: String, val route: String)

    val tabs = listOf(
        TabItem("Report", Screen.Report.route),
        TabItem("Study", Screen.Study.route),
        TabItem("Test", Screen.Survey.route),
        TabItem("Review", Screen.ReviewList.route),
        TabItem("Word", Screen.Vocabulary.route)
    )

    // ← 가능 여부: Report(시작화면)이 아니면 뒤로 갈 수 있음
    val canGoBack = currentRoute != null && currentRoute != Screen.Report.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(BarBackground)
            .padding(horizontal = 2.dp),
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
            val isActive = currentRoute == tab.route
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
