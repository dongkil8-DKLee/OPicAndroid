package com.opic.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.opic.android.ui.navigation.OPicNavGraph
import com.opic.android.ui.theme.OPicTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 단일 Activity — NavHost로 모든 화면 관리.
 * Python OPIcApp.show_frame() → NavController.navigate() 대응.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OPicTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                ) {
                    val navController = rememberNavController()
                    OPicNavGraph(navController = navController)
                }
            }
        }
    }
}
