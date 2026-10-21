package com.humanstudio3d.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.humanstudio3d.R
import com.humanstudio3d.data.HealthResult
import com.humanstudio3d.ui.SettingsStatus
import com.humanstudio3d.ui.SettingsViewModel
import com.humanstudio3d.ui.components.StudioTopBar

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    var url by rememberSaveable { mutableStateOf(vm.savedUrl) }
    var token by rememberSaveable { mutableStateOf("") }
    val hasToken by vm.hasToken.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    Scaffold(topBar = { StudioTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.settings_url_label)) },
                placeholder = { Text("https://example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.settings_token_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (hasToken) R.string.settings_token_saved else R.string.settings_token_none,
                        ),
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasToken) {
                TextButton(onClick = vm::clearToken) { Text(stringResource(R.string.settings_token_clear)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        vm.save(url, token)
                        token = ""
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.settings_save)) }
                OutlinedButton(
                    onClick = { vm.test(url, token) },
                    enabled = status !is SettingsStatus.Testing,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.settings_test)) }
            }

            when (val s = status) {
                SettingsStatus.Testing -> CircularProgressIndicator()
                SettingsStatus.Saved -> Text(stringResource(R.string.settings_saved))
                SettingsStatus.InvalidUrl -> Text(
                    text = stringResource(R.string.settings_invalid_url),
                    color = MaterialTheme.colorScheme.error,
                )
                is SettingsStatus.Tested -> {
                    val ok = s.result == HealthResult.OK
                    Text(
                        text = stringResource(
                            when (s.result) {
                                HealthResult.OK -> R.string.settings_test_ok
                                HealthResult.AUTH -> R.string.settings_test_auth
                                HealthResult.TIMEOUT -> R.string.settings_test_timeout
                                HealthResult.NETWORK -> R.string.settings_test_network
                                HealthResult.BAD_RESPONSE -> R.string.settings_test_bad
                            },
                        ),
                        color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    )
                }
                SettingsStatus.Idle -> Unit
            }

            Text(
                text = stringResource(R.string.settings_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
