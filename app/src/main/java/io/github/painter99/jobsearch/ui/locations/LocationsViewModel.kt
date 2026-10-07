package io.github.painter99.jobsearch.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.painter99.jobsearch.core.model.Municipality
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.mpsv.MunicipalityRepository
import io.github.painter99.jobsearch.data.storage.ProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stav obrazovky lokalit (M1.6 vlna A): dotaz autocomplete, výsledky,
 * množina vybraných obcí (ID "Obec/&lt;ruian&gt;").
 */
data class LocationsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val results: List<Municipality> = emptyList(),
    val selected: Set<String> = emptySet(),
    val saving: Boolean = false,
    val loadError: Boolean = false,
)

/**
 * ViewModel obrazovky lokalit (M1.6 vlna A, T2): fetch-once číselník obcí,
 * autocomplete = case-insensitive prefix dřív než obsah, multi-select, uložení profilu.
 */
@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val repository: MunicipalityRepository,
    private val profileStore: ProfileStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationsUiState())
    val state: StateFlow<LocationsUiState> = _state.asStateFlow()

    private var allMunicipalities: List<Municipality> = emptyList()

    init {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = repository.getMunicipalities()) {
                is FetchResult.Success -> {
                    allMunicipalities = result.data
                    _state.update {
                        it.copy(
                            loading = false,
                            selected = profileStore.load().municipalityIds,
                            results = filter(allMunicipalities, it.query),
                        )
                    }
                }
                else -> _state.update { it.copy(loading = false, loadError = true) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, results = filter(allMunicipalities, query)) }
    }

    fun toggle(municipality: Municipality) {
        _state.update {
            val selected = it.selected.toMutableSet()
            if (municipality.id in selected) selected.remove(municipality.id) else selected.add(municipality.id)
            it.copy(selected = selected)
        }
    }

    /** Uloží vybrané obce do profilu (districtIds se zachovávají pro budoucí výběr okresů). */
    fun save() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val current = profileStore.load()
            profileStore.save(current.copy(municipalityIds = _state.value.selected))
            _state.update { it.copy(saving = false) }
        }
    }

    private fun filter(all: List<Municipality>, query: String): List<Municipality> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val starts = mutableListOf<Municipality>()
        val contains = mutableListOf<Municipality>()
        for (municipality in all) {
            val name = municipality.name.lowercase()
            when {
                name.startsWith(q) -> starts.add(municipality)
                name.contains(q) -> contains.add(municipality)
            }
            if (starts.size >= MAX_RESULTS) break
        }
        return (starts + contains).take(MAX_RESULTS)
    }

    private companion object {
        const val MAX_RESULTS = 40
    }
}