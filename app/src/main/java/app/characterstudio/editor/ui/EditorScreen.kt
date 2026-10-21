package app.characterstudio.editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.characterstudio.R
import app.characterstudio.editor.model.Layer
import app.characterstudio.editor.model.LayerKind
import app.characterstudio.editor.model.ViewMapping
import app.characterstudio.editor.render.RenderOptions
import app.characterstudio.editor.render.drawScene
import kotlinx.coroutines.launch

private enum class Tab { None, Add, Layers, Properties, Board }
private enum class DragMode { Pan, Layer, Anchor }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onExit: () -> Unit,
    onExport: (String) -> Unit,
    vm: EditorViewModel = viewModel(key = projectId, factory = EditorViewModel.factory(projectId)),
) {
    val load by vm.loadState.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val scene by vm.scene.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val status by vm.saveStatus.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val anchorMode by vm.anchorMode.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val ellipseName = stringResource(R.string.shape_ellipse)
    val rectName = stringResource(R.string.shape_rect)
    var tab by remember { mutableStateOf(Tab.None) }
    var showExitDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Layer?>(null) }
    var deleting by remember { mutableStateOf<Layer?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }

    fun requestExit() { scope.launch { if (vm.saveNow()) onExit() else showExitDialog = true } }
    BackHandler(onBack = ::requestExit)

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.saveInBackground() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name.ifEmpty { stringResource(R.string.new_title) }, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = ::requestExit) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(
                        onClick = { scope.launch { if (vm.saveNow()) onExport(projectId) else showExitDialog = true } },
                        enabled = load == LoadState.Ready,
                    ) { Text(stringResource(R.string.export_action)) }
                    TextButton(onClick = vm::undo, enabled = canUndo) { Text(stringResource(R.string.undo)) }
                    TextButton(onClick = vm::redo, enabled = canRedo) { Text(stringResource(R.string.redo)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (load) {
                LoadState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                LoadState.NotFound, LoadState.Corrupt, LoadState.NeedsUpdate -> Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(when (load) {
                        LoadState.NotFound -> R.string.editor_not_found
                        LoadState.Corrupt -> R.string.editor_corrupt
                        else -> R.string.editor_needs_update
                    }))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onExit) { Text(stringResource(R.string.editor_back_to_projects)) }
                }
                LoadState.Ready -> {
                    val sc = scene
                    if (sc != null) {
                        // شريط الحالة
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(when (status) {
                                    SaveStatus.Saved -> R.string.status_saved
                                    SaveStatus.Saving -> R.string.status_saving
                                    SaveStatus.Unsaved -> R.string.status_unsaved
                                    SaveStatus.Error -> R.string.status_error
                                }),
                                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                color = if (status == SaveStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = { scope.launch { vm.saveNow() } }) { Text(stringResource(R.string.save_now)) }
                        }

                        EditorCanvas(vm, sc, selectedId, anchorMode, view, Modifier.weight(1f).fillMaxWidth())

                        if (tab != Tab.None) {
                            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                                when (tab) {
                                    Tab.Add -> AddPanel { kind ->
                                        val base = if (kind == LayerKind.Ellipse) ellipseName else rectName
                                        vm.addShape(kind, "$base ${sc.layers.size + 1}")
                                    }
                                    Tab.Layers -> LayersPanel(
                                        sc, selectedId, vm,
                                        onRename = { renaming = it },
                                        onDelete = { deleting = it },
                                        onDuplicate = { vm.duplicateLayer(it.id, "${it.name} ${ctx.getString(R.string.duplicate_suffix)}".take(60)) },
                                    )
                                    Tab.Properties -> PropertiesPanel(
                                        sc, selectedId, vm,
                                        onDelete = { deleting = it },
                                        onDuplicate = { vm.duplicateLayer(it.id, "${it.name} ${ctx.getString(R.string.duplicate_suffix)}".take(60)) },
                                    )
                                    Tab.Board -> BoardPanel(sc, anchorMode, vm) { confirmResetAll = true }
                                    Tab.None -> Unit
                                }
                            }
                        }
                        NavigationBar {
                            listOf(
                                Tab.Add to R.string.tab_add, Tab.Layers to R.string.tab_layers,
                                Tab.Properties to R.string.tab_properties, Tab.Board to R.string.tab_board,
                            ).forEach { (t, label) ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = if (tab == t) Tab.None else t },
                                    icon = {},
                                    label = { Text(stringResource(label)) },
                                    alwaysShowLabel = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) AlertDialog(
        onDismissRequest = { showExitDialog = false },
        title = { Text(stringResource(R.string.exit_unsaved_title)) },
        text = { Text(stringResource(R.string.exit_unsaved_body)) },
        confirmButton = { TextButton(onClick = { showExitDialog = false; onExit() }) { Text(stringResource(R.string.exit_anyway)) } },
        dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.stay)) } },
    )

    renaming?.let { l ->
        var text by remember(l.id) { mutableStateOf(l.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_layer_title)) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(l.id, text); renaming = null }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { l ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_layer_title)) },
            text = { Text(stringResource(R.string.delete_layer_body, l.name)) },
            confirmButton = { TextButton(onClick = { vm.deleteLayer(l.id); deleting = null }) { Text(stringResource(R.string.menu_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmResetAll) AlertDialog(
        onDismissRequest = { confirmResetAll = false },
        title = { Text(stringResource(R.string.reset_all_title)) },
        text = { Text(stringResource(R.string.reset_all_body)) },
        confirmButton = { TextButton(onClick = { vm.resetSession(); confirmResetAll = false }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = { confirmResetAll = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EditorCanvas(
    vm: EditorViewModel,
    scene: app.characterstudio.editor.model.Scene,
    selectedId: String?,
    anchorMode: Boolean,
    view: app.characterstudio.editor.model.Viewport,
    modifier: Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val mapping = remember(size, scene.canvasWidth, scene.canvasHeight, view) {
        ViewMapping.of(size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f), scene.canvasWidth, scene.canvasHeight, view)
    }
    val mappingNow by rememberUpdatedState(mapping)
    val canvasBg = MaterialTheme.colorScheme.background

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val m0 = mappingNow
                    val px = m0.toSceneX(down.position.x)
                    val py = m0.toSceneY(down.position.y)
                    var mode = DragMode.Pan
                    var anchorId: String? = null
                    if (vm.anchorMode.value) {
                        anchorId = vm.anchorAt(px, py, 28f / m0.scale)
                        if (anchorId != null) mode = DragMode.Anchor
                    } else if (vm.selectAt(px, py) != null) {
                        mode = DragMode.Layer
                    }
                    var multi = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            if (!multi) { vm.endLive(); multi = true }
                            val c = event.calculateCentroid()
                            val z = event.calculateZoom()
                            val p = event.calculatePan()
                            vm.transformView(c.x, c.y, size.width / 2f, size.height / 2f, z, p.x, p.y)
                            event.changes.forEach { ch: PointerInputChange -> if (ch.positionChanged()) ch.consume() }
                        } else if (!multi) {
                            val ch = event.changes.first()
                            if (ch.pressed) {
                                val d = ch.positionChange()
                                val s = mappingNow.scale
                                when (mode) {
                                    DragMode.Layer -> vm.moveSelectedBy(d.x / s, d.y / s)
                                    DragMode.Anchor -> anchorId?.let { vm.moveAnchorBy(it, d.x / s, d.y / s) }
                                    DragMode.Pan -> vm.panBy(d.x, d.y)
                                }
                                ch.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    vm.endLive()
                }
            },
    ) {
        drawRect(canvasBg)
        val m = mapping
        withTransform({
            translate(m.ox, m.oy)
            scale(m.scale, m.scale, Offset.Zero)
        }) {
            drawScene(
                scene,
                RenderOptions(
                    showSkeleton = scene.showSkeleton, showAnchors = scene.showAnchors,
                    showChecker = true, selectedLayerId = selectedId, unit = 1f / m.scale,
                ),
            )
        }
    }
}
