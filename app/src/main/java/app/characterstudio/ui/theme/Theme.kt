package app.characterstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Light = lightColorScheme(
    primary = Color(0xFF3F5BA9), onPrimary = Color.White,
    secondary = Color(0xFF5B6B8C), background = Color(0xFFF7F8FC), surface = Color(0xFFFFFFFF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF0F2B6B),
    secondary = Color(0xFFBFC8E6), background = Color(0xFF12141B), surface = Color(0xFF1B1E27),
)

/** الواجهة عربية بالكامل حاليًا، لذلك نفرض RTL بغض النظر عن لغة الجهاز. */
@Composable
fun CharacterStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
