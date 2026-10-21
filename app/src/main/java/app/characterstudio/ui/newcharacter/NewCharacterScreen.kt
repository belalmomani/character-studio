package app.characterstudio.ui.newcharacter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.characterstudio.CharacterStudioApp
import app.characterstudio.R
import app.characterstudio.data.ArtStyle
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewCharacterScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val repo = (LocalContext.current.applicationContext as CharacterStudioApp).container.projects
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(ArtStyle.Cartoon) }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.new_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text(stringResource(R.string.field_project_name)) },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(stringResource(it)) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.field_art_style), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleChip(ArtStyle.Realistic2d, R.string.style_realistic_2d, style) { style = it }
                StyleChip(ArtStyle.Cartoon, R.string.style_cartoon, style) { style = it }
                StyleChip(ArtStyle.Anime, R.string.style_anime, style) { style = it }
                StyleChip(ArtStyle.SimpleGame, R.string.style_simple_game, style) { style = it }
            }
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    scope.launch {
                        repo.create(name, style)
                            .onSuccess { onCreated(it.id) }
                            .onFailure {
                                error = if (it is IllegalArgumentException) R.string.error_invalid_name else R.string.error_generic
                                busy = false
                            }
                    }
                },
            ) { Text(stringResource(R.string.create_project)) }
        }
    }
}

@Composable
private fun StyleChip(value: ArtStyle, label: Int, selected: ArtStyle, onSelect: (ArtStyle) -> Unit) {
    FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
}
