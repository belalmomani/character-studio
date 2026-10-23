package app.characterstudio.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.characterstudio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(kind: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember(ctx) {
        @Suppress("DEPRECATION")
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val (title, body) = when (kind) {
        "help" -> R.string.info_help_title to stringResource(R.string.info_help_body)
        "privacy" -> R.string.info_privacy_title to stringResource(R.string.info_privacy_body)
        "terms" -> R.string.info_terms_title to stringResource(R.string.info_terms_body)
        else -> R.string.info_about_title to stringResource(R.string.info_about_body, version)
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) { Text(body) }
    }
}
