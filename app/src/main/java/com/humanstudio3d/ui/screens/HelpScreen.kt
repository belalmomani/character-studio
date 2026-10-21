package com.humanstudio3d.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.humanstudio3d.R
import com.humanstudio3d.ui.components.StudioTopBar

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(topBar = { StudioTopBar(stringResource(R.string.help_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.help_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
