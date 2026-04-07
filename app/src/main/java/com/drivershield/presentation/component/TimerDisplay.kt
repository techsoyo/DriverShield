package com.drivershield.presentation.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun TimerDisplay(
    elapsedMs: Long,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60

    Text(
        text = "%02d:%02d:%02d".format(hours, minutes, seconds),
        style = MaterialTheme.typography.displayLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 64.sp,
            color = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier
    )
}
