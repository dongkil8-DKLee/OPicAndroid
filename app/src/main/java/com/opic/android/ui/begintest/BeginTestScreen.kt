package com.opic.android.ui.begintest

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opic.android.R
import com.opic.android.ui.theme.OPicColors

/**
 * Python BeginTestPage 1:1 이식.
 * 안내 이미지(banner_begin_test.png) + Back/Start Test 버튼.
 */
@Composable
fun BeginTestScreen(
    onBack: () -> Unit,
    onStartTest: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 안내 이미지 ---
            Image(
                painter = painterResource(id = R.drawable.banner_begin_test),
                contentDescription = "Begin Test Guide",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            // --- 하단: Back / Start Test ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OPicColors.Primary,
                        contentColor = OPicColors.PrimaryText
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("< Back", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onStartTest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OPicColors.Primary,
                        contentColor = OPicColors.PrimaryText
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Start Test >", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
