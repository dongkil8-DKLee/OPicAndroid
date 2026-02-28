package com.opic.android.ui.common

import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opic.android.ui.theme.OPicColors

@Composable
fun HomeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = OPicColors.Secondary,
            contentColor = OPicColors.TextOnLight
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(44.dp)
    ) {
        Icon(Icons.Filled.Home, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text("Home", fontWeight = FontWeight.Bold)
    }
}
