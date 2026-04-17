package com.drivershield.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Panel de contador estilo Casio.
 *
 * Muestra una etiqueta funcional ([label] en [activeColor]) sobre un [DigitalDisplay]
 * con efecto de doble capa de segmentos (fantasma + real) y parpadeo binario de ':'.
 *
 * @param label        Texto de la etiqueta, ej. "TRABAJO PROG."
 * @param time         Hora formateada "HH:MM:SS"
 * @param activeColor  Color de la etiqueta (verde=trabajo, rojo=descanso, blanco=semanal)
 * @param isRunning    Activa el parpadeo de los dos puntos
 * @param illuminatorOn Activa el fondo de iluminador nocturno en el LCD
 */
@Composable
fun CasioTimerBox(
    label: String,
    time: String,
    activeColor: Color,
    modifier: Modifier = Modifier,
    isRunning: Boolean = false,
    illuminatorOn: Boolean = false
) {
    Column(modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            text          = label.uppercase(),
            color         = activeColor,
            fontSize      = 9.sp,
            fontWeight    = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            fontFamily    = FontFamily.Monospace,
            modifier      = Modifier.padding(start = 2.dp, bottom = 4.dp)
        )
        DigitalDisplay(
            value         = time,
            ghostMask     = "88:88:88",
            isRunning     = isRunning,
            illuminatorOn = illuminatorOn,
            fontSize      = 32.sp,
            modifier      = Modifier.fillMaxWidth()
        )
    }
}
