package app.characterstudio.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.characterstudio.R

@Composable
fun HomeScreen(onProjects: () -> Unit, onNewCharacter: () -> Unit, onSettings: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            HomeCard(R.string.home_new_character, R.string.home_new_character_desc, onNewCharacter)
            HomeCard(R.string.home_my_projects, R.string.home_my_projects_desc, onProjects)
            androidx.compose.material3.OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_settings))
            }
        }
    }
}

@Composable
private fun HomeCard(title: Int, desc: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
