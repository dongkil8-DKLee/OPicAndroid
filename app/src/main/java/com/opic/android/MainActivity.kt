package com.opic.android

import android.Manifest
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.opic.android.data.prefs.AppPreferences
import com.opic.android.ui.navigation.BottomNavState
import com.opic.android.ui.navigation.LocalBottomNavState
import com.opic.android.ui.navigation.OPicBottomBar
import com.opic.android.ui.navigation.OPicNavGraph
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
            val themeMode = appPrefs.themeMode
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> false // system default → light for now
            }
            OPicTheme(darkTheme = darkTheme) {
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
