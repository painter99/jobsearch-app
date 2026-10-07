package io.github.painter99.jobsearch.ui.dossier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.painter99.jobsearch.core.model.DossierVerdict
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.ares.AresClient
import io.github.painter99.jobsearch.data.mpsv.MunicipalityRepository
import io.github.painter99.jobsearch.data.mpsv.OfferStore
import io.github.painter99.jobsearch.data.storage.CriteriaProvider
import io.github.painter99.jobsearch.data.storage.ProfileProvider
import io.github.painter99.jobsearch.db.ChecklistDao
import io.github.painter99.jobsearch.db.ChecklistEntryEntity
import io.github.painter99.jobsearch.db.DossierDao
import io.github.painter99.jobsearch.db.DossierEntity
import io.github.painter99.jobsearch.pipeline.AgencyDetector
import io.github.painter99.jobsearch.pipeline.AgencyStatus
import io.github.painter99.jobsearch.pipeline.EndEmployerResolver
import io.github.painter99.jobsearch.pipeline.EmployerHints
import io.github.painter99.jobsearch.pipeline.ResolverCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stav detailu dossieru (US4, M1.6 vlna B).
 *
 * checklistChecked = zdroj pravdy je DB; auto položky se při PRVNÍM otevření
 * předvyplní ze šablony (auto-fill) a dál se přepisují jen uživatelskou akcí.
 */
data class DossierUiState(
    val loading: Boolean = true,
    val offer: JobOffer? = null,
    val verdict: DossierVerdict = DossierVerdict.OPEN,
    val notes: String = "",
    val checklist: List<ChecklistRow> = emptyList(),
    val agencyStatus: AgencyStatus? = null,
    val agencyName: String? = null,
    val resolverCandidates: List<ResolverCandidate> = emptyList(),
    val resolverLoading: Boolean = false,
    val resolverError: Boolean = false,
    /** Proč kandidáty nenabízíme (bez oborové stopy) — null = důvod není. */
    val resolverNote: String? = null,
    val loadError: Boolean = false,
)

/** Jedna řádka checklistu v UI (stav z DB + informace o auto-fillu). */
data class ChecklistRow(
    val item: ChecklistTemplateItem,
    val checked: Boolean,
    val autoFilled: Boolean,
)

/**
 * ViewModel detailu dossieru (US4, M1.6 vlna B):
 * - načte nabídku z OfferStore + dossier/checklist z Room,
 * - při PRVNÍM otevření vytvoří dossier a předvyplní checklist ze šablony,
 * - u agenturních nabídek načte kandidáty resolveru (sekce jen u agentur, N7).
 */
@HiltViewModel
class DossierViewModel @Inject constructor(
    private val offerStore: OfferStore,
    private val dossierDao: DossierDao,
    private val checklistDao: ChecklistDao,
    private val criteriaProvider: CriteriaProvider,
    private val profileProvider: ProfileProvider,
    private val agencyDetector: AgencyDetector,
    private val resolver: EndEmployerResolver,
    private val aresClient: AresClient,
    private val municipalityRepository: MunicipalityRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(DossierUiState())
    val state: StateFlow<DossierUiState> = _state.asStateFlow()

    private var loadedOfferKey: String? = null

    /** Načte dossier pro [offerKey]; volá se z UI při otevření detailu. */
    fun load(offerKey: String) {
        if (loadedOfferKey == offerKey && !_state.value.loading) return
        loadedOfferKey = offerKey
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = false) }
            val portalId = offerKey.removePrefix("mpsv/").toLongOrNull()
            val offer = portalId?.let { offerStore.findByPortalId(it) }
            if (offer == null) {
                _state.update { it.copy(loading = false, loadError = true) }
                return@launch
            }
            val criteria = criteriaProvider.load()
            val profile = profileProvider.load()
            val dossier = ensureDossier(offerKey)
            val entries = checklistDao.forOffer(offerKey)
            val storedChecked = entries.filter { it.checked }.map { it.itemKey }.toSet()
            val rows = ChecklistTemplate.items.map { item ->
                val autoFilled = item.autoFill?.invoke(offer, criteria, profile) == true
                // Auto-fill (US4): při prvním otevření (žádné položky v DB) se
                // auto položky předvyplní do DB — od té chvíle je zdrojem pravdy
                // databáze (toggle přepisuje; odškrtnutá položka zůstává).
                // Detekce prvního otevření = forOffer().isEmpty(), ne prázdné
                // checkedKeys (odškrtnutí všeho nesmí spustit re-prefill).
                if (entries.isEmpty() && autoFilled) {
                    checklistDao.upsert(ChecklistEntryEntity(offerKey, item.key, checked = true))
                }
                ChecklistRow(
                    item = item,
                    checked = if (entries.isEmpty()) autoFilled else item.key in storedChecked,
                    autoFilled = autoFilled,
                )
            }
            _state.update {
                it.copy(
                    offer = offer,
                    verdict = DossierVerdict.fromName(dossier.verdict),
                    notes = dossier.notes,
                    checklist = rows,
                )
            }
            // Agenturní sekce běží ještě pod loading — první načtení je hotové
            // až s ní (UI i testy čekají na loading=false; lekce run #31).
            loadAgencySection(offer)
            _state.update { it.copy(loading = false) }
        }
    }

    /** Přepne verdikt (verdikt = volba uživatele; N7 se týká jen návrhů resolveru). */
    fun setVerdict(verdict: DossierVerdict) {
        val offerKey = loadedOfferKey ?: return
        viewModelScope.launch {
            ensureDossier(offerKey)
            dossierDao.setVerdict(offerKey, verdict.name, clock.millis())
            _state.update { it.copy(verdict = verdict) }
        }
    }

    /** Uloží poznámky (UI volá na „Uložit"; zde vždy plný zápis). */
    fun setNotes(notes: String) {
        val offerKey = loadedOfferKey ?: return
        viewModelScope.launch {
            ensureDossier(offerKey)
            dossierDao.setNotes(offerKey, notes, clock.millis())
            _state.update { it.copy(notes = notes) }
        }
    }

    /** Přepne položku checklistu (zdroj pravdy = DB; auto-fill je jen výchozí stav). */
    fun toggleChecklist(key: String) {
        val offerKey = loadedOfferKey ?: return
        viewModelScope.launch {
            ensureDossier(offerKey)
            val currentlyChecked = key in checklistDao.checkedKeys(offerKey)
            val newValue = !currentlyChecked
            checklistDao.upsert(ChecklistEntryEntity(offerKey, key, checked = newValue))
            _state.update { state ->
                state.copy(
                    checklist = state.checklist.map {
                        if (it.item.key == key) it.copy(checked = newValue) else it
                    },
                )
            }
        }
    }

    /** Dossier vytvoří, jen když neexistuje (idempotentní; OPEN výchozí). */
    private suspend fun ensureDossier(offerKey: String): DossierEntity {
        dossierDao.byOfferKey(offerKey)?.let { return it }
        val now = clock.millis()
        val dossier = DossierEntity(
            offerKey = offerKey,
            verdict = DossierVerdict.OPEN.name,
            notes = "",
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        dossierDao.upsert(dossier)
        return dossier
    }

    /**
     * Agenturní sekce (US1/US2): status detekce vždy; kandidáty resolveru
     * jen když jde o agenturu (N7 — navrhuje, potvrzuje uživatel).
     * ARES chyby = graceful degradation (R3): sekce zůstane prázdná.
     */
    private suspend fun loadAgencySection(offer: JobOffer) {
        val ico = offer.employer?.ico
        if (ico == null) {
            _state.update { it.copy(agencyStatus = AgencyStatus.UNDETERMINED) }
            return
        }
        val status = agencyDetector.isAgency(ico)
        _state.update { it.copy(agencyStatus = status, agencyName = agencyDetector.agencyName(ico)) }
        when (status) {
            AgencyStatus.AGENCY_MPSV, AgencyStatus.AGENCY_VR -> loadResolver(offer, ico)
            else -> Unit
        }
    }

    private suspend fun loadResolver(offer: JobOffer, agentIco: String) {
        _state.update { it.copy(resolverLoading = true, resolverError = false, resolverNote = null) }
        val hints = buildHints(offer, agentIco)
        if (hints == null) {
            // Bez oborové stopy by vyhledávání vrátilo jen jiné agentury — nepředstíráme návrh (N7).
            _state.update {
                it.copy(
                    resolverLoading = false,
                    resolverNote = "Koncovou firmu nelze navrhnout bez oboru — v1 nemá text inzerátu " +
                        "(deep link only). Oborový návrh přibude s AI vrstvou (M1.7).",
                )
            }
            return
        }
        when (val result = resolver.resolve(hints)) {
            is FetchResult.Success ->
                _state.update { it.copy(resolverLoading = false, resolverCandidates = result.data) }
            else -> _state.update { it.copy(resolverLoading = false, resolverError = true) }
        }
    }

    /**
     * Stopy pro resolver: lokalita z pracoviště nabídky (RÚIAN → název přes
     * číselník) + obor z ARES detailu agentury, MÍNUS kódy 78.x (zprostředkování
     * zaměstnání) — hledáme koncovou firmu, ne další agentury. Bez použitelného
     * oboru vrací null (UI ukáže vysvětlující poznámku, žádné kandidáty).
     */
    private suspend fun buildHints(offer: JobOffer, agentIco: String): EmployerHints? {
        val detail = aresClient.detail(agentIco)
        val nace = (detail as? FetchResult.Success)?.data?.czNace.orEmpty()
            .filterNot { it.startsWith("78") }
        if (nace.isEmpty()) return null
        val municipalityName = offer.location.worksiteMunicipalityIds.firstOrNull()
            ?.let { id -> municipalityRepository.nameMap()[id] }
        return EmployerHints(
            naceCodes = nace,
            municipalityName = municipalityName,
        )
    }
}