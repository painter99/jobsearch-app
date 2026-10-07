package io.github.painter99.jobsearch.ui.dossier

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.painter99.jobsearch.core.model.Benefit
import io.github.painter99.jobsearch.core.model.DossierVerdict
import io.github.painter99.jobsearch.core.model.Employer
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.data.ares.AresClient
import io.github.painter99.jobsearch.data.mpsv.MpsvAgencyClient
import io.github.painter99.jobsearch.data.mpsv.MpsvCiselnikyClient
import io.github.painter99.jobsearch.data.mpsv.MunicipalityRepository
import io.github.painter99.jobsearch.data.mpsv.FileOfferStore
import io.github.painter99.jobsearch.data.mpsv.OfferStore
import io.github.painter99.jobsearch.data.storage.CriteriaProvider
import io.github.painter99.jobsearch.data.storage.LocationProfile
import io.github.painter99.jobsearch.data.storage.ProfileProvider
import io.github.painter99.jobsearch.db.ChecklistDao
import io.github.painter99.jobsearch.db.DossierDao
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

    /** Reálný FileOfferStore nad temp JSON Lines souborem (lekce run #10). */
    private fun offerStore(vararg offers: JobOffer): FileOfferStore {
        val file = File("/tmp/offers-dossier-test-${System.nanoTime()}.json")
        if (offers.isNotEmpty()) {
            file.writeText(offers.joinToString("\n") { o ->
                """{"portalId": ${o.portalId}}"""
            })
        }
        return FileOfferStore(file)
    }

    private fun viewModel(
        store: OfferStore,
        criteria: io.github.painter99.jobsearch.core.filter.UserCriteria =
            io.github.painter99.jobsearch.core.filter.UserCriteria(40_000, true),
        profile: LocationProfile = LocationProfile(municipalityIds = setOf("Obec/503657")),
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
        val store = offerStore(offer(4L, employerIco = "17181879"))
        val vm = viewModel(store)
        vm.load("mpsv/4")
        awaitSettled(vm)
        vm.setVerdict(DossierVerdict.CONDITIONAL)
        vm.setNotes("Zavolat na pracoviště")
        advanceUntilIdle()
        val dossier = db.dossierDao().byOfferKey("mpsv/4")!!
        assertEquals(DossierVerdict.CONDITIONAL.name, dossier.verdict)
        assertEquals("Zavolat na pracoviště", dossier.notes)
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

}