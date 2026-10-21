package com.humanstudio3d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.humanstudio3d.ui.AppNav
import com.humanstudio3d.ui.CharacterViewModel
import com.humanstudio3d.ui.theme.StudioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CharacterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudioTheme {
                // The app ships Arabic strings only, so the layout is forced to RTL even when the
                // device language is not Arabic. Revisit when more languages are added.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNav(viewModel)
                    }
                }
            }
        }
    }
}
