package app.characterstudio.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.characterstudio.CharacterStudioApp
import app.characterstudio.R
import app.characterstudio.data.ProjectEntity
import app.characterstudio.data.ProjectRepository
import app.characterstudio.data.ProjectSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val query: String = "",
    val sort: ProjectSort = ProjectSort.UpdatedDesc,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModel(private val repo: ProjectRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(ProjectSort.UpdatedDesc)
    private val _errors = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val errors: SharedFlow<Int> = _errors

    val state: StateFlow<ProjectsUiState> = combine(query, sort) { q, s -> q to s }
        .flatMapLatest { (q, s) -> repo.observe(q, s).map { ProjectsUiState(it, q, s, loading = false) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    fun setQuery(value: String) { query.value = value }
    fun setSort(value: ProjectSort) { sort.value = value }

    fun rename(id: String, name: String) = run { repo.rename(id, name) }
    fun duplicate(id: String, suffix: String) = run { repo.duplicate(id, suffix) }
    fun delete(id: String) = run { repo.delete(id) }

    private fun run(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            block().onFailure {
                _errors.tryEmit(
                    if (it is IllegalArgumentException) R.string.error_invalid_name else R.string.error_generic
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ProjectsViewModel((this[APPLICATION_KEY] as CharacterStudioApp).container.projects)
            }
        }
    }
}
