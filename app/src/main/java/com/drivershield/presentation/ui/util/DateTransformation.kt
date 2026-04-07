package com.drivershield.presentation.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscara visual DD/MM/AAAA para campos de fecha numéricos.
 *
 * El estado almacenado es siempre solo dígitos (máx. 8 caracteres, ej. "06042026").
 * Esta transformación inserta '/' en las posiciones visuales 2 y 5 para mostrar "06/04/2026".
 *
 * ## Mapa de offsets (cursor)
 * ```
 * raw (índice):     0  1  2  3  4  5  6  7
 * visual (índice):  0  1  /  3  4  /  6  7  8  9
 *                   d  d     M  M     y  y  y  y
 * ```
 * - `originalToTransformed`: ≤1 → offset | ≤3 → offset+1 | >3 → offset+2
 * - `transformedToOriginal`: ≤2 → offset | ≤5 → offset−1 | >5 → offset−2
 *
 * Ambas funciones aplican `.coerceAtMost` para strings parciales (< 8 dígitos).
 */
class DateTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = StringBuilder()

        for (i in digits.indices) {
            out.append(digits[i])
            if (i == 1 || i == 3) out.append('/')
        }

        val offsetMapping = object : OffsetMapping {

            override fun originalToTransformed(offset: Int): Int {
                val shifted = when {
                    offset <= 1 -> offset
                    offset <= 3 -> offset + 1
                    else        -> offset + 2
                }
                return shifted.coerceAtMost(out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val shifted = when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    else        -> offset - 2
                }
                return shifted.coerceIn(0, digits.length)
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}
