package io.github.painter99.jobsearch.ui.dossier

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.painter99.jobsearch.core.model.Benefit
import io.github.painter99.jobsearch.core.model.DossierVerdict
import io.github.painter99.jobsearch.core.model.Employer
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.ai.AiCandidateRanker
import io.github.painter99.jobsearch.ai.DossierSummarizer
import io.github.painter99.jobsearch.ai.PromptLoader
import io.github.painter99.jobsearch.data.ai.OpenRouterClient
import io.github.painter99.jobsearch.data.ares.AresClient
import io.github.painter99.jobsearch.data.mpsv.MpsvAgencyClient
import io.github.painter99.jobsearch.data.mpsv.MpsvCiselnikyClient
import io.github.painter99.jobsearch.data.mpsv.MunicipalityRepository
import io.github.painter99.jobsearch.data.mpsv.ChangeType
import io.github.painter99.jobsearch.data.mpsv.FileOfferStore
import io.github.painter99.jobsearch.data.mpsv.IncrementRecord
import io.github.painter99.jobsearch.data.mpsv.OfferStore
import io.github.painter99.jobsearch.data.storage.AiSettings
import io.github.painter99.jobsearch.data.storage.AiSettingsProvider
import io.github.painter99.jobsearch.data.storage.CriteriaProvider
import io.github.painter99.jobsearch.data.storage.LocationProfile
import io.github.painter99.jobsearch.data.storage.ProfileProvider
import io.github.painter99.jobsearch.db.ChecklistDao
import io.github.painter99.jobsearch.db.DossierDao
import io.github.painter99.jobsearch.db.DossierEntity
import io.github.painter99.jobsearch.db.JobsearchDatabase
import io.github.painter99.jobsearch.pipeline.AgencyDetector
import io.github.painter99.jobsearch.pipeline.AgencyStatus
import io.github.painter99.jobsearch.pipeline.EndEmployerResolver
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T5: DossierViewModel (US4, vlna B) — reálné komponenty dle lekcí vlny A:
 * [FileOfferStore] nad temp souborem, reálná Room DB (Robolectric, in-memory),
 * [AgencyDetector]+[EndEmployerResolver]+[AresClient] nad MockWebServerem
 * s živými fixturami (lekce run #10 — final třídy se nedají fakovat).
 * Latka/profil = lambdy nad interfacema (CriteriaProvider/ProfileProvider).
 *
 * Síť + Room na reálných vláknech → [awaitSettled] polluje se sleepem
 * (vzor OffersViewModelTest).
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class DossierViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var server: MockWebServer

    private lateinit var db: JobsearchDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, JobsearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        server.shutdown()
    }

    // --- Fixtury / helpery ---

    private fun enqueueAgentury() {
        val body = javaClass.getResourceAsStream("/agentury-prace-sample.json")!!
            .readBytes().toString(Charsets.UTF_8)
        server.enqueue(MockResponse().setBody(body))
    }

    private fun enqueueAresDetail(ico: String) {
        val body = javaClass.getResourceAsStream("/ares-detail-$ico.json")!!
            .readBytes().toString(Charsets.UTF_8)
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))
    }

    private fun enqueueAresVr(ico: String) {
        val body = javaClass.getResourceAsStream("/ares-vr-$ico.json")!!
            .readBytes().toString(Charsets.UTF_8)
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))
    }

    private fun enqueueAresVyhledat() {
        val body = javaClass.getResourceAsStream("/ares-vyhledat-28130.json")!!
            .readBytes().toString(Charsets.UTF_8)
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))
    }

    private fun enqueueObce() {
        val body = javaClass.getResourceAsStream("/obce-sample.json")!!
            .readBytes().toString(Charsets.UTF_8)
        server.enqueue(MockResponse().setBody(body))
    }

    private fun offer(
        portalId: Long,
        employerIco: String? = "17181879",
        benefits: List<io.github.painter99.jobsearch.core.model.Benefit> = emptyList(),
        worksiteMunicipalityIds: List<String> = emptyList(),
    ) = JobOffer(
        portalId = portalId,
        referenceNumber = "ref-$portalId",
        profession = "Profese $portalId",
        shiftPattern = ShiftPattern.SINGLE_SHIFT,
        salaryFrom = 45_000,
        salaryTo = null,
        hoursPerWeek = null,
        employer = employerIco?.let { Employer(ico = it, name = "Agentura $it") },
        location = WorkLocation(
            type = WorkLocation.LocationType.MUNICIPALITY,
            municipalityId = "Obec/503657",
            districts = emptyList(),
            addressText = null,
            worksiteMunicipalityIds = worksiteMunicipalityIds,
        ),
        benefits = benefits,
        url = null,
        agencyConsent = null,
        userConsent = null,
    )

    /**
     * Reálný FileOfferStore nad temp JSON Lines souborem (lekce run #10).
     * Seed přes [FileOfferStore.applyIncrement] — řádek serializuje samotný
     * store (offerToLine), žádné duplikování formátu v testu (lekce run #29/#30:
     * ruční `{"portalId": X}` řádky parser neumí → store prázdný → loadError).
     */
    private suspend fun offerStore(vararg offers: JobOffer): FileOfferStore {
        val file = File("/tmp/offers-dossier-test-${System.nanoTime()}.json")
        val store = FileOfferStore(file)
        if (offers.isNotEmpty()) {
            store.applyIncrement(offers.map { IncrementRecord(it.portalId, ChangeType.NEW, it) })
        }
        return store
    }

    private fun viewModel(
        store: OfferStore,
        criteria: io.github.painter99.jobsearch.core.filter.UserCriteria =
            io.github.painter99.jobsearch.core.filter.UserCriteria(40_000, true),
        profile: LocationProfile = LocationProfile(municipalityIds = setOf("Obec/503657")),
        aiSettings: AiSettings = AiSettings(),
    ): DossierViewModel {
        val ok = OkHttpClient()
        val ares = AresClient(
            client = ok,
            baseUrl = server.url("/ekonomicke-subjekty-v-be/rest").toString(),
        )
        val agencyClient = MpsvAgencyClient(
            client = ok,
            baseUrl = server.url("/od/soubory/agentury-prace/agentury-prace.json").toString(),
        )
        val ciselniky = MpsvCiselnikyClient(
            client = ok,
            obceUrl = server.url("/od/soubory/ciselniky/obce.json").toString(),
        )
        // Fake loader pro VM test: vrací jen hodnoty parametrů (VM test neověřuje
        // kontrakt promptu — ten pokrývá PromptSubstitutionTest; lekce CI #37:
        // šablona s cizími placeholdery vyhodila IllegalStateException uvnitř
        // viewModelScope a test uvízl v aiRankingLoading).
        val promptLoader = object : PromptLoader {
            override fun load(name: String, params: Map<String, String>): String =
                params.values.joinToString(" | ")
        }
        val openRouter = OpenRouterClient(
            client = ok,
            baseUrl = server.url("/api/v1").toString(),
        )
        return DossierViewModel(
            offerStore = store,
            dossierDao = db.dossierDao(),
            checklistDao = db.checklistDao(),
            criteriaProvider = object : CriteriaProvider {
                override suspend fun load() = criteria
            },
            profileProvider = object : ProfileProvider {
                override suspend fun load() = profile
            },
            agencyDetector = AgencyDetector(agencyClient, ares),
            resolver = EndEmployerResolver(ares),
            aresClient = ares,
            municipalityRepository = MunicipalityRepository(ciselniky),
            clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneId.of("Europe/Prague")),
            aiSettingsProvider = object : AiSettingsProvider {
                override suspend fun load() = aiSettings
            },
            aiCandidateRanker = AiCandidateRanker(openRouter, promptLoader),
            dossierSummarizer = DossierSummarizer(openRouter, promptLoader),
        )
    }


    private suspend fun TestScope.awaitSettled(vm: DossierViewModel, timeoutMs: Long = 10_000): DossierUiState {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            advanceUntilIdle()
            val s = vm.state.value
            if (!s.loading && !s.resolverLoading) return s
            Thread.sleep(20)
        }
        return vm.state.value
    }

    // --- Testy ---

    @Test
    fun `load - vytvoří dossier s OPEN verdiktem a předvyplní auto položky`() = runTest(dispatcher) {
        enqueueAgentury()          // 1. volání — detekce agentury (MPSV seznam)
        enqueueAresDetail("17181879") // 2. volání — NACE stopy pro resolver
        enqueueAresVyhledat()      // 3. volání — resolver vyhledat
        val store = offerStore(offer(1L))
        val vm = viewModel(store)
        vm.load("mpsv/1")
        val state = awaitSettled(vm)

        assertFalse(state.loadError)
        assertEquals(DossierVerdict.OPEN, state.verdict)
        assertNotNull(state.offer)
        // auto-fill: mzda 45k ≥ 40k + jednosměnná + obec v profilu
        val byKey = state.checklist.associateBy { it.item.key }
        assertTrue(byKey.getValue("salary").checked)
        assertTrue(byKey.getValue("single_shift").checked)
        assertTrue(byKey.getValue("location").checked)
        // ruční položky nezaškrtnuté
        assertFalse(byKey.getValue("reviews").checked)
        // agentura dle MPSV seznamu → resolver kandidáti načteni
        assertEquals(AgencyStatus.AGENCY_MPSV, state.agencyStatus)
        assertTrue(state.resolverCandidates.isNotEmpty())
        // dossier je v DB
        assertNotNull(db.dossierDao().byOfferKey("mpsv/1"))
    }

    @Test
    fun `load - neagenturní nabídka nemá kandidáty resolveru (N7 sekce jen u agentur)`() =
        runTest(dispatcher) {
            enqueueAgentury()          // 1. — MPSV seznam (IČO 64608212 v něm není)
            enqueueAresVr("64608212")  // 2. — VR doplněk (SIGMA = zpracování, ne agentura)
            val store = offerStore(offer(2L, employerIco = "64608212"))
            val vm = viewModel(store)
            vm.load("mpsv/2")
            val state = awaitSettled(vm)

            assertEquals(AgencyStatus.NOT_AGENCY, state.agencyStatus)
            assertTrue(state.resolverCandidates.isEmpty())
            assertFalse(state.resolverLoading)
        }

    @Test
    fun `load - nabídka bez IČO → UNDETERMINED, žádná síťová sekce`() = runTest(dispatcher) {
        val store = offerStore(offer(3L, employerIco = null))
        val vm = viewModel(store)
        vm.load("mpsv/3")
        val state = awaitSettled(vm)
        assertEquals(AgencyStatus.UNDETERMINED, state.agencyStatus)
        assertTrue(state.resolverCandidates.isEmpty())
    }

    @Test
    fun `setVerdict a setNotes se uloží do DB`() = runTest(dispatcher) {
        enqueueAgentury()          // detekce agentury (17181879 v MPSV seznamu)
        enqueueAresDetail("17181879") // NACE stopy pro resolver
        enqueueAresVyhledat()      // resolver vyhledat — jinak 2× 10s socket timeout
        val store = offerStore(offer(4L, employerIco = "17181879"))
        val vm = viewModel(store)
        vm.load("mpsv/4")
        awaitSettled(vm)
        vm.setVerdict(DossierVerdict.CONDITIONAL)
        vm.setNotes("Zavolat na pracoviště")
        // Room píše na vlastním executoru — advanceUntilIdle na něj nečeká
        // (lekce run #31): polluje se, dokud se zápis neobjeví v DB.
        val deadline = System.nanoTime() + 10_000_000_000
        var dossier: DossierEntity? = null
        while (System.nanoTime() < deadline) {
            advanceUntilIdle()
            dossier = db.dossierDao().byOfferKey("mpsv/4")
            if (dossier != null &&
                dossier.verdict == DossierVerdict.CONDITIONAL.name &&
                dossier.notes == "Zavolat na pracoviště"
            ) break
            Thread.sleep(20)
        }
        advanceUntilIdle()
        assertNotNull(dossier)
        assertEquals(DossierVerdict.CONDITIONAL.name, dossier?.verdict)
        assertEquals("Zavolat na pracoviště", dossier?.notes)
        assertEquals(DossierVerdict.CONDITIONAL, vm.state.value.verdict)
        assertEquals("Zavolat na pracoviště", vm.state.value.notes)
    }

    @Test
    fun `toggleChecklist - odškrtnutí auto položky přepíše stav v DB`() = runTest(dispatcher) {
        enqueueAgentury()
        enqueueAresDetail("17181879")
        enqueueAresVyhledat()
        val store = offerStore(offer(5L))
        val vm = viewModel(store)
        vm.load("mpsv/5")
        awaitSettled(vm)
        // salary byla auto-zaškrtnutá → uživatel ji odškrtne
        vm.toggleChecklist("salary")
        // Room píše na vlastním executoru — polluje se (lekce run #31).
        val deadline = System.nanoTime() + 10_000_000_000
        while (System.nanoTime() < deadline) {
            advanceUntilIdle()
            if ("salary" !in db.checklistDao().checkedKeys("mpsv/5")) break
            Thread.sleep(20)
        }
        advanceUntilIdle()
        assertFalse("salary" in db.checklistDao().checkedKeys("mpsv/5"))
        assertFalse(vm.state.value.checklist.first { it.item.key == "salary" }.checked)
    }

    @Test
    fun `load - neznámý offerKey → loadError bez pádu`() = runTest(dispatcher) {
        val store = offerStore(offer(6L))
        val vm = viewModel(store)
        vm.load("mpsv/999")
        val state = awaitSettled(vm)
        assertTrue(state.loadError)
        assertNull(state.offer)
    }

    // --- M1.7 AI (T3/T5) ---

    @Test
    fun `load - bez klíče AI sekce skrytá (AC4)`() = runTest(dispatcher) {
        enqueueAgentury()
        enqueueAresDetail("17181879")
        enqueueAresVyhledat()
        val store = offerStore(offer(7L))
        val vm = viewModel(store, aiSettings = AiSettings(apiKey = null, tosConsent = true))
        vm.load("mpsv/7")
        val state = awaitSettled(vm)
        assertFalse(state.aiAvailable)
        assertTrue(state.aiRanking.isEmpty())
        assertEquals(null, state.aiSummary)
    }

    @Test
    fun `load - klíč bez souhlasu s ToS → AI sekce skrytá (souhlas chybí)`() = runTest(dispatcher) {
        enqueueAgentury()
        enqueueAresDetail("17181879")
        enqueueAresVyhledat()
        val store = offerStore(offer(8L))
        val vm = viewModel(store, aiSettings = AiSettings(apiKey = "sk-test", tosConsent = false))
        vm.load("mpsv/8")
        val state = awaitSettled(vm)
        assertFalse(state.aiAvailable)
    }

    @Test
    fun `rankWithAi - přeřadí kandidáty (D6 jen na tlačítko)`() = runTest(dispatcher) {
        enqueueAgentury()
        enqueueAresDetail("17181879")
        enqueueAresVyhledat()
        // AI volání (MockWebServer) — IČO skutečného top-5 kandidáta resolveru
        // (AISIN 26052377, score 3.0 — NACE shoda + rodinná právní forma)
        val ranked = org.json.JSONObject()
            .put("ranked", org.json.JSONArray()
                .put(org.json.JSONObject().put("ico", "26052377").put("reason", "obor i lokalita")))
            .toString()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":${org.json.JSONObject.quote(ranked)}}}]}""",
            ).setHeader("Content-Type", "application/json"),
        )
        val store = offerStore(offer(9L))
        val vm = viewModel(
            store,
            aiSettings = AiSettings(apiKey = "sk-test", tosConsent = true, rankerModel = "test/model"),
        )
        vm.load("mpsv/9")
        awaitSettled(vm)
        assertTrue(vm.state.value.aiAvailable)
        assertTrue(vm.state.value.resolverCandidates.isNotEmpty())

        vm.rankWithAi()
        // D6: volání běží na viewModelScope (Main=test dispatcher) + reálné IO
        // síti — čeká se na start (aiRankingLoading) a pak na konec (výsledek).
        val deadline = System.nanoTime() + 10_000_000_000
        var state = vm.state.value
        while (System.nanoTime() < deadline && !state.aiRankingLoading) {
            advanceUntilIdle()
            state = vm.state.value
            Thread.sleep(20)
        }
        while (System.nanoTime() < deadline && state.aiRankingLoading) {
            advanceUntilIdle()
            state = vm.state.value
            Thread.sleep(20)
        }
        advanceUntilIdle()
        state = vm.state.value
        assertFalse(state.aiRankingError)
        assertEquals(listOf("26052377"), state.aiRanking.map { it.candidate.company.ico })
        assertEquals("obor i lokalita", state.aiRanking[0].aiReason)
    }

    @Test
    fun `rankWithAi - AI selže → error, deterministický seznam zůstává`() = runTest(dispatcher) {
        enqueueAgentury()
        enqueueAresDetail("17181879")
        enqueueAresVyhledat()
        server.enqueue(MockResponse().setResponseCode(429)) // AI rate limit
        val store = offerStore(offer(10L))
        val vm = viewModel(
            store,
            aiSettings = AiSettings(apiKey = "sk-test", tosConsent = true, rankerModel = "test/model"),
        )
        vm.load("mpsv/10")
        awaitSettled(vm)
        vm.rankWithAi()
        val deadline = System.nanoTime() + 10_000_000_000
        var state = vm.state.value
        while (System.nanoTime() < deadline && !state.aiRankingLoading) {
            advanceUntilIdle()
            state = vm.state.value
            Thread.sleep(20)
        }
        while (System.nanoTime() < deadline && state.aiRankingLoading) {
            advanceUntilIdle()
            state = vm.state.value
            Thread.sleep(20)
        }
        advanceUntilIdle()
        state = vm.state.value
        assertTrue(state.aiRankingError)
        assertTrue(state.aiRanking.isEmpty())
        assertTrue(state.resolverCandidates.isNotEmpty()) // jádro dál funkční
    }

    @Test
    fun `summarizeWithAi - vrátí summary s disclaimerem v UI stavu`() = runTest(dispatcher) {
        enqueueAgentury()
        enqueueAresDetail("17181879")
        enqueueAresVyhledat()
        val summary = org.json.JSONObject().put("summary", "• Mzda sedí\n• Ověřit recenze").toString()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":${org.json.JSONObject.quote(summary)}}}]}""",
            ).setHeader("Content-Type", "application/json"),
        )
        val store = offerStore(offer(11L))
        val vm = viewModel(
            store,
            aiSettings = AiSettings(apiKey = "sk-test", tosConsent = true, summarizerModel = "test/model"),
        )
        vm.load("mpsv/11")
        awaitSettled(vm)
        vm.summarizeWithAi()
        val deadline = System.nanoTime() + 10_000_000_000
        var state = vm.state.value
        while (System.nanoTime() < deadline && !state.aiSummaryLoading) {
            advanceUntilIdle()
            state = vm.state.value
            Thread.sleep(20)
        }
        while (System.nanoTime() < deadline && state.aiSummaryLoading) {
            advanceUntilIdle()
            state = vm.state.value
            Thread.sleep(20)
        }
        advanceUntilIdle()
        state = vm.state.value
        assertFalse(state.aiSummaryError)
        assertEquals("• Mzda sedí\n• Ověřit recenze", state.aiSummary)
    }

}