package com.drivershield.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivershield.presentation.theme.CasioColors
import com.drivershield.presentation.theme.Digital7

/**
 * Display de 7 segmentos estilo Casio.
 *
 * Técnica de doble capa:
 *  - Capa inferior: [ghostMask] en [lcdTextOff] — simula segmentos siempre encendidos al mínimo
 *  - Capa superior: [value] en [lcdTextOn]       — segmentos activos (números reales)
 *
 * Los dos puntos ':' parpadean en binario cada 500 ms cuando [isRunning] = true.
 * La transición de color del fondo (Illuminator) es suave (300 ms).
 *
 * @param value      Cadena a mostrar, ej. "14:30:00". Debe tener la misma longitud que [ghostMask].
 * @param ghostMask  Máscara de segmentos fantasma, por defecto "88:88:88".
 * @param isRunning  Si es true, los ':' parpadean. En pausa/stop quedan fijos.
 * @param illuminatorOn  Activa el fondo azul cian del iluminador nocturno.
 * @param fontSize   Tamaño del dígito LCD.
 */
@Composable
fun DigitalDisplay(
    value: String,
    ghostMask: String = "88:88:88",
    isRunning: Boolean = false,
    illuminatorOn: Boolean = false,
    fontSize: TextUnit = 38.sp,
    modifier: Modifier = Modifier
) {
    // Color de fondo — transición suave entre LCD normal e iluminador
    val backgroundColor by animateColorAsState(
        targetValue = if (illuminatorOn) CasioColors.illuminatorNight else CasioColors.lcdBackground,
        animationSpec = tween(300),
        label = "lcdBg"
    )
    val textOnColor by animateColorAsState(
        targetValue = if (illuminatorOn) Color(0xFF00222F) else CasioColors.lcdTextOn,
        animationSpec = tween(300),
        label = "lcdOn"
    )
    val textOffColor by animateColorAsState(
        targetValue = if (illuminatorOn) Color(0xFF009FBF) else CasioColors.lcdTextOff,
        animationSpec = tween(300),
        label = "lcdOff"
    )

    // Parpadeo binario de los ':' — paso duro en 500 ms, sin interpolación alfa
    val infiniteTransition = rememberInfiniteTransition(label = "colonBlink")
    val colonAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "colonAlpha"
    )

    Box(
        modifier = modifier
            .background(backgroundColor)
            .drawBehind {
                // Sombra interior para simular cristal hundido en el marco
                val shadow = Color(0xFF000000).copy(alpha = 0.30f)
                val s = 4.dp.toPx()
                drawRect(shadow, topLeft = Offset.Zero,            size = Size(size.width, s))
                drawRect(shadow, topLeft = Offset(0f, size.height - s), size = Size(size.width, s))
                drawRect(shadow, topLeft = Offset.Zero,            size = Size(s, size.height))
                drawRect(shadow, topLeft = Offset(size.width - s, 0f), size = Size(s, size.height))
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Capa 1 — segmentos fantasma siempre visibles
        Text(
            text       = ghostMask,
            color      = textOffColor,
            fontSize   = fontSize,
            fontFamily = Digital7
        )

        // Capa 2 — valor real con parpadeo binario en los dos puntos
        Text(
            text = buildAnnotatedString {
                value.forEach { char ->
                    val alpha = if (char == ':' && isRunning) colonAlpha else 1f
                    withStyle(SpanStyle(color = textOnColor.copy(alpha = alpha))) {
                        append(char)
                    }
                }
            },
            fontSize   = fontSize,
            fontFamily = Digital7
        )
    }
}
