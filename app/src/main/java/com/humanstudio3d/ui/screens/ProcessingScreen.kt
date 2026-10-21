package com.humanstudio3d.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.humanstudio3d.R
import com.humanstudio3d.domain.FailureReason
import com.humanstudio3d.domain.ProcessingState
import com.humanstudio3d.ui.CharacterViewModel
import com.humanstudio3d.ui.components.StudioTopBar

@Composable
fun ProcessingScreen(
    vm: CharacterViewModel,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.processing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.startProcessing() }
    BackHandler(onBack = onBack)

    Scaffold(topBar = { StudioTopBar(stringResource(R.string.processing_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val stage = state.stageLabelRes()
            if (stage != null) {
                // Real stage label plus an indeterminate indicator: no invented percentage.
                CircularProgressIndicator()
                Text(stringResource(stage), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = vm::cancelProcessing) {
                    Text(stringResource(R.string.action_cancel))
                }
            }

            when (val s = state) {
                is ProcessingState.Failed -> {
                    Text(
                        stringResource(R.string.state_failed),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(s.reason.messageRes()),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Settings problems need the settings screen, not a blind retry.
                    val needsSettings = s.reason == FailureReason.PROVIDER_NOT_CONFIGURED ||
                        s.reason == FailureReason.AUTH
                    if (needsSettings) {
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) { Text(stringResource(R.string.action_open_settings)) }
                    } else {
                        Button(
                            onClick = {
                                vm.resetProcessing()
                                vm.startProcessing()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) { Text(stringResource(R.string.action_retry)) }
                    }
                    OutlinedButton(
                        onClick = onPickAnother,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.action_pick_another)) }
                }

                is ProcessingState.Completed -> {
                    Text(stringResource(R.string.state_completed), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.state_completed_note),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ProcessingState.Cancelled -> {
                    Text(stringResource(R.string.state_cancelled), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(
                        onClick = onPickAnother,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.action_pick_another)) }
                }

                else -> Unit
            }
        }
    }
}

private fun ProcessingState.stageLabelRes(): Int? = when (this) {
    ProcessingState.Idle, ProcessingState.ReadyToUpload -> R.string.state_ready
    ProcessingState.Uploading -> R.string.state_uploading
    ProcessingState.Generating -> R.string.state_generating
    ProcessingState.Downloading -> R.string.state_downloading
    ProcessingState.Validating -> R.string.state_validating
    is ProcessingState.Completed, is ProcessingState.Failed, ProcessingState.Cancelled -> null
}

private fun FailureReason.messageRes(): Int = when (this) {
    FailureReason.PROVIDER_NOT_CONFIGURED -> R.string.failure_not_configured
    FailureReason.NETWORK -> R.string.failure_network
    FailureReason.TIMEOUT -> R.string.failure_timeout
    FailureReason.QUOTA_EXHAUSTED -> R.string.failure_quota
    FailureReason.AUTH -> R.string.failure_auth
    FailureReason.INVALID_MODEL -> R.string.failure_invalid_model
    FailureReason.UNKNOWN -> R.string.failure_unknown
}
