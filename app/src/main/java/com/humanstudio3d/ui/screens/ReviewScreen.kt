package com.humanstudio3d.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.humanstudio3d.R
import com.humanstudio3d.ui.CharacterViewModel
import com.humanstudio3d.ui.components.StudioTopBar

@Composable
fun ReviewScreen(
    vm: CharacterViewModel,
    onBack: () -> Unit,
    onReplaceOrCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val state by vm.image.collectAsStateWithLifecycle()
    val preview = state.preview
    val serverHost = remember { vm.serverHost() }
    var showConsent by remember { mutableStateOf(false) }

    if (showConsent && serverHost != null) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(stringResource(R.string.consent_title)) },
            text = { Text(stringResource(R.string.consent_body, serverHost)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConsent = false
                        vm.grantUploadConsent()
                        onConfirm()
                    },
                ) { Text(stringResource(R.string.consent_agree)) }
            },
            dismissButton = {
                TextButton(onClick = { showConsent = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(topBar = { StudioTopBar(stringResource(R.string.review_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = stringResource(R.string.review_image_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp)),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = stringResource(R.string.review_missing_image),
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.review_tips),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.rotate(-90f) },
                    enabled = preview != null && !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.action_rotate_left)) }
                OutlinedButton(
                    onClick = { vm.rotate(90f) },
                    enabled = preview != null && !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.action_rotate_right)) }
            }

            Button(
                onClick = { if (serverHost != null) showConsent = true else onConfirm() },
                enabled = preview != null && !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_confirm)) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onReplaceOrCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_replace))
                }
                TextButton(onClick = onReplaceOrCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}
