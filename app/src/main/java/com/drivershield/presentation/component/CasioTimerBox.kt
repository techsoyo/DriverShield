package com.drivershield.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CasioTimerBox(
    label: String,
    time: String,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(4.dp).fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
                .padding(vertical = 14.dp)
        ) {
            Text(
                text = "88:88:88",
                color = Color(0xFF111111),
                fontSize = 34.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = time,
                color = activeColor,
                fontSize = 34.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}