package app.characterstudio.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.characterstudio.CharacterStudioApp
import app.characterstudio.assets.AssetEntity
import app.characterstudio.assets.AssetFilter
import app.characterstudio.assets.AssetRepository
import app.characterstudio.assets.CategoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SyncUi {
    data object Idle : SyncUi
    data object Syncing : SyncUi
    data class Done(val received: Int) : SyncUi
    data object Failed : SyncUi
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repo: AssetRepository,
    val configured: Boolean,
    val cloudName: String,
) : ViewModel() {
    private val _filter = MutableStateFlow(AssetFilter())
    val filter: StateFlow<AssetFilter> = _filter

    val assets: StateFlow<List<AssetEntity>> = _filter
        .flatMapLatest { repo.browse(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repo.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unlocked: StateFlow<Set<String>> = repo.unlockedIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _sync = MutableStateFlow<SyncUi>(SyncUi.Idle)
    val sync: StateFlow<SyncUi> = _sync

    init { if (configured) sync() }

    fun setQuery(q: String) { _filter.value = _filter.value.copy(query = q) }
    fun setCategory(id: String?) { _filter.value = _filter.value.copy(categoryId = id) }
    fun setStyle(style: String?) { _filter.value = _filter.value.copy(artStyle = style) }
    fun canUse(a: AssetEntity): Boolean = repo.canUse(a, unlocked.value)

    fun sync() {
        if (!configured || _sync.value == SyncUi.Syncing) return
        _sync.value = SyncUi.Syncing
        viewModelScope.launch {
            _sync.value = repo.sync().fold({ SyncUi.Done(it.assetsReceived) }, { SyncUi.Failed })
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as CharacterStudioApp).container
                LibraryViewModel(c.assets, c.libraryConfigured, c.cloudName)
            }
        }
    }
}
