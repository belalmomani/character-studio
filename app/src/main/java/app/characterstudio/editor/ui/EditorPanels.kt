package app.characterstudio.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.characterstudio.R
import app.characterstudio.editor.model.Layer
import app.characterstudio.editor.model.LayerKind
import app.characterstudio.editor.model.Scene
import kotlin.math.roundToInt

val PaletteColors = listOf(
    0xFF3F5BA9, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
    0xFF00ACC1, 0xFF8E24AA, 0xFF6D4C41, 0xFF212121, 0xFFFFFFFF,
    0xFFF1C27D, 0xFFC68642, 0xFF8D5524,
)

@Composable
private fun ColorRow(selected: Long?, onPick: (Long) -> Unit, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        extra?.invoke()
        PaletteColors.forEachIndexed { i, c ->
            val desc = stringResource(R.string.color_desc, i + 1)
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                    .border(if (selected == c) 3.dp else 1.dp, if (selected == c) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                    .clickable(onClickLabel = desc) { onPick(c) },
            )
        }
    }
}

@Composable
private fun Modifier.hScroll(): Modifier = this.horizontalScroll(rememberScrollState())

@Composable
fun AddPanel(onAdd: (LayerKind) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAdd(LayerKind.Ellipse) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_ellipse)) }
        Button(onClick = { onAdd(LayerKind.Rect) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_rect)) }
        Text(stringResource(R.string.add_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LayersPanel(
    scene: Scene,
    selectedId: String?,
    vm: EditorViewModel,
    onRename: (Layer) -> Unit,
    onDelete: (Layer) -> Unit,
    onDuplicate: (Layer) -> Unit,
) {
    if (scene.layers.isEmpty()) {
        Text(stringResource(R.string.layers_empty), Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // الأعلى في القائمة = الأمامي في الرسم
        items(scene.layers.asReversed(), key = { it.id }) { l ->
            val sel = l.id == selectedId
            var menu by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(l.colorArgb)).border(1.dp, Color.Gray, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).clickable { vm.select(l.id) }.padding(vertical = 8.dp)) {
                            Text(l.name, style = MaterialTheme.typography.titleSmall)
                            val badges = buildList {
                                if (l.locked) add(stringResource(R.string.layer_locked_badge))
                                if (!l.visible) add(stringResource(R.string.layer_hidden_badge))
                            }
                            if (badges.isNotEmpty()) Text(badges.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                        }
                        Box {
                            TextButton(onClick = { menu = true }) { Text("⋮") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.menu_rename)) }, onClick = { menu = false; onRename(l) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.menu_duplicate)) }, onClick = { menu = false; onDuplicate(l) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.menu_delete)) }, onClick = { menu = false; onDelete(l) })
                            }
                        }
                    }
                    Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallBtn(if (l.visible) R.string.layer_hide else R.string.layer_show) { vm.setVisible(l.id, !l.visible) }
                        SmallBtn(if (l.locked) R.string.layer_unlock else R.string.layer_lock) { vm.setLocked(l.id, !l.locked) }
                        SmallBtn(R.string.layer_forward) { vm.moveLayer(l.id, +1) }
                        SmallBtn(R.string.layer_backward) { vm.moveLayer(l.id, -1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallBtn(label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PropertiesPanel(scene: Scene, selectedId: String?, vm: EditorViewModel, onDelete: (Layer) -> Unit, onDuplicate: (Layer) -> Unit) {
    val l = scene.layers.firstOrNull { it.id == selectedId }
    if (l == null) {
        Text(stringResource(R.string.props_none), Modifier.padding(16.dp))
        return
    }
    val t = l.transform
    val enabled = !l.locked
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(l.name, style = MaterialTheme.typography.titleMedium)
        if (l.locked) Text(stringResource(R.string.props_locked), style = MaterialTheme.typography.bodySmall)

        Text(stringResource(R.string.prop_width, t.width.roundToInt()))
        Slider(t.width, { v -> vm.liveTransform(l.id) { it.copy(width = v) } }, enabled = enabled,
            valueRange = 16f..1024f, onValueChangeFinished = vm::endLive)
        Text(stringResource(R.string.prop_height, t.height.roundToInt()))
        Slider(t.height, { v -> vm.liveTransform(l.id) { it.copy(height = v) } }, enabled = enabled,
            valueRange = 16f..1024f, onValueChangeFinished = vm::endLive)
        Text(stringResource(R.string.prop_rotation, t.rotation.roundToInt()))
        Slider(t.rotation, { v -> vm.liveTransform(l.id) { it.copy(rotation = v) } }, enabled = enabled,
            valueRange = -180f..180f, onValueChangeFinished = vm::endLive)
        Text(stringResource(R.string.prop_opacity, (l.opacity * 100).roundToInt()))
        Slider(l.opacity, { v -> vm.liveOpacity(l.id, v) }, enabled = enabled,
            valueRange = 0f..1f, onValueChangeFinished = vm::endLive)

        Text(stringResource(R.string.prop_color), style = MaterialTheme.typography.titleSmall)
        if (enabled) ColorRow(selected = l.colorArgb, onPick = { vm.setColor(l.id, it) })

        l.attachedAnchorId?.let { aid ->
            val label = scene.anchors.firstOrNull { it.id == aid }?.label ?: aid
            Text(stringResource(R.string.prop_attached, label), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.snapToAnchor(l.id) }, enabled = enabled) { Text(stringResource(R.string.prop_snap)) }
            OutlinedButton(onClick = { vm.resetLayer(l.id) }, enabled = enabled) { Text(stringResource(R.string.prop_reset_layer)) }
            OutlinedButton(onClick = { onDuplicate(l) }) { Text(stringResource(R.string.menu_duplicate)) }
            OutlinedButton(onClick = { onDelete(l) }) { Text(stringResource(R.string.menu_delete)) }
        }
    }
}

@Composable
fun BoardPanel(scene: Scene, anchorMode: Boolean, vm: EditorViewModel, onResetAll: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.board_size, scene.canvasWidth, scene.canvasHeight), style = MaterialTheme.typography.bodySmall)
        SwitchRow(R.string.board_skeleton, scene.showSkeleton) { vm.setShowSkeleton(it) }
        SwitchRow(R.string.board_anchors, scene.showAnchors) { vm.setShowAnchors(it) }
        SwitchRow(R.string.board_anchor_mode, anchorMode) { vm.setAnchorMode(it) }
        Text(stringResource(R.string.board_background), style = MaterialTheme.typography.titleSmall)
        ColorRow(scene.backgroundArgb, { vm.setBackground(it) }) {
            OutlinedButton(onClick = { vm.setBackground(null) }, border = BorderStroke(if (scene.backgroundArgb == null) 3.dp else 1.dp, MaterialTheme.colorScheme.primary)) {
                Text(stringResource(R.string.board_bg_transparent))
            }
        }
        Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::resetAnchors) { Text(stringResource(R.string.board_reset_anchors)) }
            OutlinedButton(onClick = vm::resetView) { Text(stringResource(R.string.board_reset_view)) }
            OutlinedButton(onClick = onResetAll) { Text(stringResource(R.string.board_reset_all)) }
        }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
