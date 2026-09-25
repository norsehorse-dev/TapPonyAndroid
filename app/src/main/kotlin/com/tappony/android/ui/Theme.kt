package com.tappony.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Signal blue on charcoal, the TapPony family color. */
object TapColors {
    val Charcoal = Color(0xFF15181D)
    val Surface = Color(0xFF1E2229)
    val SurfaceHigh = Color(0xFF272C35)
    val Blue = Color(0xFF2F7BFF)
    val BlueLight = Color(0xFF7FB0FF)
    val Ok = Color(0xFF46D17F)
    val Fail = Color(0xFFF0575D)
    val Warn = Color(0xFFE2A54A)
    val Text = Color(0xFFE8EAED)
    val Muted = Color(0xFF9AA3AE)
}

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun TapPonyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TapColors.Blue,
            onPrimary = Color.White,
            secondary = TapColors.BlueLight,
            background = TapColors.Charcoal,
            onBackground = TapColors.Text,
            surface = TapColors.Surface,
            onSurface = TapColors.Text,
            surfaceVariant = TapColors.SurfaceHigh,
            onSurfaceVariant = TapColors.Muted,
            error = TapColors.Fail,
        ),
        typography = Typography(),
        content = content,
    )
}
