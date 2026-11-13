package com.keal.aspire.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AspireMonochrome = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF080808),
    background = Color(0xFF080808),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF191919),
    onSurfaceVariant = Color(0xFFA6A6A6),
    outline = Color(0xFF353535)
)

@Composable
fun AspireTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AspireMonochrome,
        content = content
    )
}
