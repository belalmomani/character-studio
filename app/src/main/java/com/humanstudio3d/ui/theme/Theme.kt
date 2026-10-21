package com.humanstudio3d.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDark = darkColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0B1330),
    secondary = Color(0xFF9FD8C8),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF222633),
    onSurfaceVariant = Color(0xFFB4BAC8),
    error = Color(0xFFFF8A80),
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StudioDark, content = content)
}
