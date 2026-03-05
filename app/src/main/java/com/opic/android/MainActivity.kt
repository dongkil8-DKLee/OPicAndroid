package com.opic.android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.opic.android.data.prefs.AppPreferences
import com.opic.android.ui.navigation.BottomNavState
import com.opic.android.ui.navigation.LocalBottomNavState
import com.opic.android.ui.navigation.OPicBottomBar
import com.opic.android.ui.navigation.OPicNavGraph
import com.opic.android.ui.theme.OPicColors
import com.opic.android.ui.theme.OPicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 단일 Activity — NavHost로 모든 화면 관리.
 * Python OPIcApp.show_frame() → NavController.navigate() 대응.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPrefs: AppPreferences

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 권한 결과 처리 — 앱은 권한 없어도 SAF로 대체 동작 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissionsIfNeeded()
        enableEdgeToEdge()
        setContent {
            val themeMode by appPrefs.themeModeFlow.collectAsState()
            val isDark = themeMode == "dark"

            // 테마 색상 즉시 적용 (recompose 트리거)
            if (isDark) OPicColors.applyDark() else OPicColors.applyLight()

            // ── 상태바 아이콘 색상 — 테마 전환 시 자동 반영 ──────────────
            // Light 테마: 배경이 밝음 → isAppearanceLightStatusBars=true  → 아이콘 검정
            // Dark  테마: 배경이 어두움 → isAppearanceLightStatusBars=false → 아이콘 흰색
            // enableEdgeToEdge()로 상태바 배경은 앱 배경색이 그대로 투과됨
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view)
                        .isAppearanceLightStatusBars = !isDark
                }
            }

            OPicTheme(darkTheme = isDark) {
                val navController = rememberNavController()
                val bottomNavState = remember { BottomNavState() }

                CompositionLocalProvider(LocalBottomNavState provides bottomNavState) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WindowInsets.systemBars.asPaddingValues()),
                        bottomBar = { OPicBottomBar(navController) }
                    ) { innerPadding ->
                        OPicNavGraph(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    private fun requestStoragePermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            requestPermissionsLauncher.launch(notGranted.toTypedArray())
        }
    }
}
