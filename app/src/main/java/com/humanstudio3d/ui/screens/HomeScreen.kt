package com.humanstudio3d.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.humanstudio3d.R
import com.humanstudio3d.domain.ImageErrorKind
import com.humanstudio3d.domain.ModelErrorKind
import com.humanstudio3d.ui.CharacterViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: CharacterViewModel,
    onOpenPrivacy: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.image.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        vm.onImagePicked(it)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        vm.onCaptureResult(it)
    }

    // Any file type is offered because file managers report .glb inconsistently;
    // the content is validated after copying.
    val glbPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        vm.onGlbPicked(it)
    }
    val modelState by vm.model.collectAsStateWithLifecycle()
    val busy = state.busy || modelState.busy

    val cameraUnavailable = stringResource(R.string.error_camera_unavailable)
    val modelError = modelState.error
    if (modelError != null) {
        val message = stringResource(modelError.messageRes())
        LaunchedEffect(modelError) {
            snackbar.showSnackbar(message)
            vm.clearModelError()
        }
    }
    val errorKind = state.error
    if (errorKind != null) {
        val message = stringResource(errorKind.messageRes())
        LaunchedEffect(errorKind) {
            snackbar.showSnackbar(message)
            vm.clearImageError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Large area reserved for the 3D viewer. It only shows a hint until a real
            // model exists; a 2D picture is never shown as if it were a 3D model.
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.home_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.home_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_pick)) }

            OutlinedButton(
                onClick = {
                    try {
                        camera.launch(vm.prepareCaptureUri())
                    } catch (e: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar(cameraUnavailable) }
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_capture)) }

            OutlinedButton(
                onClick = { glbPicker.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_open_glb)) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_settings)) }
                TextButton(onClick = onOpenHelp) { Text(stringResource(R.string.action_help)) }
                TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.action_privacy)) }
            }
        }
    }
}

private fun ImageErrorKind.messageRes(): Int = when (this) {
    ImageErrorKind.UNSUPPORTED -> R.string.error_unsupported
    ImageErrorKind.TOO_LARGE -> R.string.error_too_large
    ImageErrorKind.IO -> R.string.error_io
}

private fun ModelErrorKind.messageRes(): Int = when (this) {
    ModelErrorKind.INVALID -> R.string.error_glb_invalid
    ModelErrorKind.TOO_LARGE -> R.string.error_glb_too_large
    ModelErrorKind.IO -> R.string.error_glb_io
}
