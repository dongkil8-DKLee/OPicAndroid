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
private val ButtonText = Color.White

/**
 * 모든 화면 공통 하단바: < Back | Study | Home | Test | Next >
 * - < Back, Next > 는 항상 좌/우 고정
 * - 주황색 배경 + 흰색 글씨, 비활성화 시 연한 주황색
 */
@Composable
fun OPicBottomBar(navController: NavHostController) {
    val bottomNavState = LocalBottomNavState.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isOnHome = currentRoute == Screen.Report.route

    // < Back 활성 여부: 커스텀 backAction이 있거나, Home이 아닌 화면에서 뒤로 갈 수 있을 때
    val canGoBack = bottomNavState.backAction != null ||
            (!isOnHome && navController.previousBackStackEntry != null)

    // Next > 활성 여부
    val canGoNext = bottomNavState.nextAction != null && bottomNavState.nextEnabled

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
            onClick = {
                bottomNavState.backAction?.invoke()
                    ?: navController.popBackStack()
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Study =====
        BarButton(
            text = "Study",
            enabled = true,
            onClick = {
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
            enabled = true,
            onClick = {
                if (bottomNavState.homeAction != null) {
                    bottomNavState.homeAction?.invoke()
                } else {
                    navController.popBackStack(Screen.Report.route, inclusive = false)
                }
            },
            modifier = Modifier.weight(1f)
        )

        // ===== Test =====
        BarButton(
            text = "Test",
            enabled = true,
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
            onClick = { bottomNavState.nextAction?.invoke() },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BarButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = EnabledBg,
            contentColor = ButtonText,
            disabledContainerColor = DisabledBg,
            disabledContentColor = ButtonText.copy(alpha = 0.7f)
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
