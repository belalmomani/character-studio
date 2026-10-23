package app.characterstudio.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.characterstudio.R
import app.characterstudio.assets.AssetEntity
import app.characterstudio.assets.CloudinaryUrls
import app.characterstudio.assets.ThumbnailLoader

private val STYLE_LABELS = listOf(
    "realistic_2d" to R.string.style_realistic_2d, "cartoon" to R.string.style_cartoon,
    "anime" to R.string.style_anime, "simple_game" to R.string.style_simple_game,
)

/** onPick = null يعني وضع التصفح فقط (من الصفحة الرئيسية). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetLibraryContent(vm: LibraryViewModel, onClose: () -> Unit, onPick: ((AssetEntity) -> Unit)?) {
    val assets by vm.assets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val paidMsg = stringResource(R.string.library_paid_needs_account)
    var pendingMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingMsg) { pendingMsg?.let { snackbar.showSnackbar(it); pendingMsg = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = vm::sync, enabled = vm.configured && sync != SyncUi.Syncing) {
                        Text(stringResource(R.string.library_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 12.dp)) {
            if (!vm.configured) {
                Text(stringResource(R.string.library_not_configured), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            OutlinedTextField(
                value = filter.query, onValueChange = vm::setQuery, singleLine = true,
                label = { Text(stringResource(R.string.search_hint)) }, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter.artStyle == null, onClick = { vm.setStyle(null) }, label = { Text(stringResource(R.string.library_all)) })
                STYLE_LABELS.forEach { (key, label) ->
                    FilterChip(selected = filter.artStyle == key, onClick = { vm.setStyle(key) }, label = { Text(stringResource(label)) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter.categoryId == null, onClick = { vm.setCategory(null) }, label = { Text(stringResource(R.string.library_all_categories)) })
                categories.filter { it.parentId == null }.forEach { c ->
                    FilterChip(selected = filter.categoryId == c.id, onClick = { vm.setCategory(c.id) }, label = { Text(c.nameAr) })
                }
            }
            Text(
                when (val s = sync) {
                    SyncUi.Idle -> ""
                    SyncUi.Syncing -> stringResource(R.string.library_syncing)
                    is SyncUi.Done -> stringResource(R.string.library_synced, s.received)
                    SyncUi.Failed -> stringResource(R.string.library_sync_failed)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (sync == SyncUi.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (assets.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_empty))
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(assets, key = { it.assetId }) { a ->
                        val usable = a.accessLevel == "free" || a.assetId in unlocked
                        Card(onClick = { when {
                            onPick == null -> Unit
                            !usable -> pendingMsg = paidMsg
                            else -> onPick?.invoke(a)
                        } }) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AssetThumb(vm.cloudName, a, Modifier.size(96.dp))
                                Text(a.nameAr, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                Text(
                                    if (a.accessLevel == "free") stringResource(R.string.library_free)
                                    else if (usable) stringResource(R.string.library_unlocked)
                                    else stringResource(R.string.library_paid, a.pointsCost),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetThumb(cloud: String, a: AssetEntity, modifier: Modifier) {
    val ctx = LocalContext.current
    val url = remember(cloud, a.thumbPublicId) {
        if (cloud.isEmpty()) null else runCatching { CloudinaryUrls.thumb(cloud, a.thumbPublicId) }.getOrNull()
    }
    val bmp by produceState<ImageBitmap?>(null, url) { value = url?.let { ThumbnailLoader.load(ctx, it) } }
    val shown = bmp
    if (shown != null) {
        Image(shown, a.nameAr, modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
