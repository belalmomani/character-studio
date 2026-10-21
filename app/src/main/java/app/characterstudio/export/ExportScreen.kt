package app.characterstudio.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.characterstudio.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit,
    vm: ExportViewModel = viewModel(key = "export-$projectId", factory = ExportViewModel.factory(projectId)),
) {
    val ctx = LocalContext.current
    val load by vm.load.collectAsStateWithLifecycle()
    val o by vm.options.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(o.format.mime)) { uri ->
        if (uri != null) vm.saveTo(ctx, uri)
    }

    val savedMsg = stringResource(R.string.export_saved)
    val failedMsg = stringResource(R.string.export_failed)
    val oomMsg = stringResource(R.string.export_oom)
    LaunchedEffect(result) {
        when (result) {
            is ExportResult.Saved -> { snackbar.showSnackbar(savedMsg); vm.clearResult() }
            ExportResult.Failed -> { snackbar.showSnackbar(failedMsg); vm.clearResult() }
            ExportResult.OutOfMemory -> { snackbar.showSnackbar(oomMsg); vm.clearResult() }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (load) {
            ExportLoad.Loading -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            ExportLoad.Failed -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.editor_corrupt), Modifier.padding(24.dp))
            }
            ExportLoad.Ready -> Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f), Alignment.Center) {
                    preview?.let { Image(it.asImageBitmap(), stringResource(R.string.export_preview), Modifier.fillMaxSize()) }
                }
                Text(stringResource(R.string.export_format), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { f ->
                        FilterChip(selected = o.format == f, onClick = { vm.update { it.copy(format = f) } },
                            label = { Text(f.extension.uppercase()) })
                    }
                }
                Text(stringResource(R.string.export_size), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.supportedSizes.forEach { sz ->
                        FilterChip(selected = o.size == sz, onClick = { vm.update { it.copy(size = sz) } },
                            label = { Text("$sz × $sz") })
                    }
                }
                if (o.format.supportsAlpha) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.export_transparent), Modifier.weight(1f))
                        Switch(o.transparent, { v -> vm.update { it.copy(transparent = v) } })
                    }
                } else {
                    Text(stringResource(R.string.export_jpeg_note), style = MaterialTheme.typography.bodySmall)
                }
                if (o.format.hasQuality) {
                    Text(stringResource(R.string.export_quality, o.quality))
                    Slider(o.quality.toFloat(), { v -> vm.update { it.copy(quality = v.toInt()) } }, valueRange = 10f..100f)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.export_skeleton), Modifier.weight(1f))
                    Switch(o.includeSkeleton, { v -> vm.update { it.copy(includeSkeleton = v) } })
                }
                Button(
                    onClick = { picker.launch(vm.suggestedName()) },
                    enabled = result != ExportResult.Working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_save)) }
                OutlinedButton(
                    onClick = { vm.share(ctx) { intent: Intent -> ctx.startActivity(intent) } },
                    enabled = result != ExportResult.Working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_share)) }
                if (result == ExportResult.Working) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
