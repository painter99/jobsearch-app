package io.github.painter99.jobsearch.ui.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.painter99.jobsearch.core.filter.LocationFilter
import io.github.painter99.jobsearch.core.filter.UserCriteria
import io.github.painter99.jobsearch.core.filter.matchesCriteria
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.mpsv.MpsvOffersClient
import io.github.painter99.jobsearch.data.mpsv.OfferStore
import io.github.painter99.jobsearch.data.storage.CriteriaProvider
import io.github.painter99.jobsearch.data.storage.LocationProfile
import io.github.painter99.jobsearch.data.storage.ProfileProvider
import io.github.painter99.jobsearch.db.SeenDao
import io.github.painter99.jobsearch.db.SeenOfferEntity
import io.github.painter99.jobsearch.pipeline.OfferSyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stav obrazovky Seznam (M1.6 vlna A). Filtr = latka + lokalitní profil
 * (kombinace [matchesCriteria] + [LocationFilter.matches]).
 */
data class OffersUiState(
    val loading: Boolean = true,
    val offers: List<JobOffer> = emptyList(),
    val totalCount: Int = 0,
    val seenKeys: Set<String> = emptySet(),
    val profile: LocationProfile = LocationProfile(),
    val criteriaSummary: String = "",
    val syncMessage: String? = null,
    val syncing: Boolean = false,
    val bootstrapNeeded: Boolean = false,
    val downloading: Boolean = false,
)

/**
 * ViewModel obrazovky Seznam (M1.6 vlna A): načte nabídky z OfferStore,
 * aplikuje latku + profil, drží seen set (dedup sweepů), spouští denní
 * sync a bootstrap download (Wi-Fi potvrzení řeší UI — R5).
 */
@HiltViewModel
class OffersViewModel @Inject constructor(
    private val offerStore: OfferStore,
    private val criteriaProvider: CriteriaProvider,
    private val profileProvider: ProfileProvider,
    private val seenDao: SeenDao,
    private val syncEngine: OfferSyncEngine,
    private val offersClient: MpsvOffersClient,
    @io.github.painter99.jobsearch.db.OffersFile private val offersFile: File,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(OffersUiState())
    val state: StateFlow<OffersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Načte store + filtry + seen; volá se po syncu i po změně profilu/latky. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val loaded = offerStore.load()
            val criteria = criteriaProvider.load()
            val profile = profileProvider.load()
            val seen = seenDao.seenKeys().toSet()
            val all = offerStore.all()
            val filtered = all.filter { offer ->
                offer.matchesCriteria(criteria) &&
                    LocationFilter.matches(
                        offer = offer,
                        profileMunicipalities = profile.municipalityIds,
                        profileDistricts = profile.districtIds,
                    )
            }
            _state.update {
                it.copy(
                    loading = false,
                    offers = filtered,
                    totalCount = loaded,
                    seenKeys = seen,
                    profile = profile,
                    criteriaSummary = criteriaSummary(criteria),
                    bootstrapNeeded = loaded == 0,
                )
            }
        }
    }

    /** Denní sync (přírůstky od kotvy). 404 dny se přeskočí (normální stav MPSV). */
    fun syncDaily() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, syncMessage = null) }
            val results = syncEngine.syncDaily(todayExclusive = LocalDate.now(clock))
            val applied = results.sumOf { it.applied }
            val skipped = results.count { it.skipped }
            val message = syncMessage(results.size, applied, skipped)
            _state.update { it.copy(syncing = false, syncMessage = message) }
            if (applied > 0) refresh()
        }
    }

    /**
     * Bootstrap download (187 MB) — volá UI JEN po Wi-Fi potvrzení (R5).
     * Po stažení proběhne import do OfferStore (streaming, bez OOM) a refresh.
     */
    fun downloadBootstrap() {
        if (_state.value.downloading) return
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, syncMessage = null) }
            val result = offersClient.downloadBootstrap(offersFile)
            _state.update { it.copy(downloading = false) }
            when (result) {
                is FetchResult.Success -> {
                    offerStore.importBootstrap(result.data)
                    refresh()
                }
                is FetchResult.HttpError -> _state.update { it.copy(syncMessage = "Stahování selhalo (HTTP ${result.code})") }
                is FetchResult.NetworkError -> _state.update { it.copy(syncMessage = "Stahování selhalo: ${result.message}") }
                is FetchResult.ParseError -> _state.update { it.copy(syncMessage = "Neplatná data: ${result.detail}") }
            }
        }
    }

    /** Označí nabídku jako viděnou (dedup sweepů — znovu se nezobrazuje jako nová). */
    fun markSeen(offerKey: String) {
        viewModelScope.launch {
            seenDao.markSeen(SeenOfferEntity(offerKey, seenAtEpochMs = clock.millis()))
            _state.update { it.copy(seenKeys = it.seenKeys + offerKey) }
        }
    }

    private fun criteriaSummary(criteria: UserCriteria): String {
        val salary = if (criteria.minSalaryKc > 0) "≥ ${criteria.minSalaryKc} Kč" else "bez mzdového minima"
        val shift = if (criteria.singleShiftOnly) "jednosměnné" else "všechny směny"
        return "$salary, $shift"
    }

    private fun syncMessage(days: Int, applied: Int, skipped: Int): String? = when {
        days == 0 -> null
        applied == 0 && skipped > 0 -> "Žádné nové přírůstky (${skippedDaysLabel(skipped)} bez publikace)"
        applied == 0 -> "Žádné změny"
        else -> "Sync: +$applied změn"
    }

    /** České množné číslo: 1 den, 2–4 dny, 5+ dní. */
    private fun skippedDaysLabel(skipped: Int): String = when {
        skipped == 1 -> "1 den"
        skipped in 2..4 -> "$skipped dny"
        else -> "$skipped dní"
    }
}