package com.humanstudio3d.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.humanstudio3d.R
import com.humanstudio3d.ui.CharacterViewModel
import com.humanstudio3d.ui.components.StudioTopBar
import com.humanstudio3d.ui.viewer.FilamentGlbViewer

private enum class LoadState { Loading, Loaded, Failed }

private val SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

@Composable
fun ViewerScreen(vm: CharacterViewModel, onBack: () -> Unit, onExport: () -> Unit) {
    val model by vm.model.collectAsStateWithLifecycle()
    val file = model.file
    val report = model.report

    var resetKey by remember { mutableIntStateOf(0) }
    var loadState by remember { mutableStateOf(LoadState.Loading) }
    var animationNames by remember { mutableStateOf(emptyList<String>()) }
    var clipIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var speedIndex by remember { mutableIntStateOf(1) }
    var loop by remember { mutableStateOf(true) }

    val leave = {
        vm.discardModel()
        onBack()
    }
    BackHandler(onBack = leave)

    val materials = stringResource(R.string.feature_materials)
    val skeleton = stringResource(R.string.feature_skeleton)
    val animations = stringResource(R.string.feature_animations)
    val morph = stringResource(R.string.feature_morph)
    val features = listOfNotNull(
        materials.takeIf { report?.hasMaterials == true },
        skeleton.takeIf { report?.hasSkin == true },
        animations.takeIf { report?.hasAnimations == true },
        morph.takeIf { report?.hasMorphTargets == true },
    )

    Scaffold(topBar = { StudioTopBar(stringResource(R.string.viewer_title), leave) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (file == null) {
                    Text(
                        text = stringResource(R.string.viewer_no_model),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    key(resetKey) {
                        FilamentGlbViewer(
                            glbFile = file,
                            clipIndex = clipIndex,
                            playing = playing,
                            speed = SPEEDS[speedIndex],
                            loop = loop,
                            modifier = Modifier.fillMaxSize(),
                            onLoaded = { names ->
                                animationNames = names
                                clipIndex = clipIndex.coerceIn(0, (names.size - 1).coerceAtLeast(0))
                                loadState = LoadState.Loaded
                            },
                            onLoadFailed = { loadState = LoadState.Failed },
                        )
                    }
                    when (loadState) {
                        LoadState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.viewer_loading),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        LoadState.Failed -> Text(
                            text = stringResource(R.string.viewer_failed),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(24.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                        )
                        LoadState.Loaded -> Unit
                    }
                }
            }

            Text(
                text = stringResource(R.string.viewer_gesture_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (features.isEmpty()) {
                    stringResource(R.string.viewer_features_none)
                } else {
                    stringResource(R.string.viewer_features, features.joinToString("، "))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (loadState == LoadState.Loaded) {
                if (animationNames.isEmpty()) {
                    Text(
                        text = stringResource(R.string.viewer_no_animations),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    val count = animationNames.size
                    val name = animationNames[clipIndex].ifBlank { stringResource(R.string.viewer_animation_unnamed) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { clipIndex = (clipIndex - 1 + count) % count }) {
                            Text(stringResource(R.string.action_previous))
                        }
                        Text(
                            text = stringResource(R.string.viewer_animation_label, clipIndex + 1, count, name),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { clipIndex = (clipIndex + 1) % count }) {
                            Text(stringResource(R.string.action_next))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { playing = !playing },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(if (playing) R.string.action_pause else R.string.action_play))
                        }
                        OutlinedButton(
                            onClick = { speedIndex = (speedIndex + 1) % SPEEDS.size },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            val speed = SPEEDS[speedIndex]
                            val label = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
                            Text(stringResource(R.string.viewer_speed, label))
                        }
                        OutlinedButton(
                            onClick = { loop = !loop },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(if (loop) R.string.action_loop_on else R.string.action_loop_off))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        loadState = LoadState.Loading
                        resetKey += 1
                    },
                    enabled = file != null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.viewer_reset_camera)) }
                OutlinedButton(
                    onClick = onExport,
                    enabled = file != null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.action_export)) }
            }
        }
    }
}
