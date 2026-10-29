package app.characterstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.characterstudio.ui.nav.AppNav
import app.characterstudio.ui.theme.CharacterStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CharacterStudioTheme { AppNav() } }
    }
}
