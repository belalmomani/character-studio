package com.humanstudio3d.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.humanstudio3d.R
import com.humanstudio3d.domain.ExportErrorKind
import com.humanstudio3d.domain.FileNames
import com.humanstudio3d.ui.CharacterViewModel
import com.humanstudio3d.ui.ExportState
import com.humanstudio3d.ui.components.StudioTopBar
import java.util.Locale

@Composable
fun ExportScreen(vm: CharacterViewModel, onBack: () -> Unit) {
    val model by vm.model.collectAsStateWithLifecycle()
    val state by vm.export.collectAsStateWithLifecycle()
    val file = model.file
    val report = model.report

    var name by rememberSaveable { mutableStateOf("character") }
    val fileName = FileNames.glbFileName(name)

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("model/gltf-binary"),
    ) { vm.exportTo(it) }

    val leave = {
        vm.resetExport()
        onBack()
    }
    BackHandler(onBack = leave)

    val materials = stringResource(R.string.feature_materials)
    val skeleton = stringResource(R.string.feature_skeleton)
    val animations = stringResource(R.string.feature_animations)

    Scaffold(topBar = { StudioTopBar(stringResource(R.string.export_title), leave) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (file == null || report == null) {
                Text(stringResource(R.string.export_no_model))
                return@Column
            }

            Text(stringResource(R.string.export_format))
            val megabytes = String.format(Locale.US, "%.2f", file.length() / (1024.0 * 1024.0))
            Text(stringResource(R.string.export_size, megabytes))

            CheckLine(report.hasMaterials, materials)
            CheckLine(report.hasSkin, skeleton)
            CheckLine(report.hasAnimations, animations)

            if (!report.hasSkin) {
                Text(
                    text = stringResource(R.string.export_static_warning),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.export_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.export_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.export_will_save_as, fileName),
                style = MaterialTheme.typography.bodySmall,
            )

            when (val s = state) {
                ExportState.Saving -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.export_saving))
                }
                is ExportState.Success -> {
                    val saved = String.format(Locale.US, "%.2f", s.bytes / (1024.0 * 1024.0))
                    Text(
                        text = stringResource(R.string.export_success, saved),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                is ExportState.Failed -> Text(
                    text = stringResource(
                        when (s.kind) {
                            ExportErrorKind.IO -> R.string.export_failed_io
                            ExportErrorKind.VERIFY_FAILED -> R.string.export_failed_verify
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                ExportState.Idle -> Unit
            }

            Button(
                onClick = { saver.launch(fileName) },
                enabled = state !is ExportState.Saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    stringResource(
                        if (state is ExportState.Failed) R.string.action_save_retry else R.string.action_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CheckLine(present: Boolean, label: String) {
    Text(
        text = if (present) {
            stringResource(R.string.export_check_present, label)
        } else {
            stringResource(R.string.export_check_absent, label)
        },
    )
}
