package io.github.painter99.jobsearch.ui.offers

import io.github.painter99.jobsearch.core.filter.UserCriteria
import io.github.painter99.jobsearch.core.model.Employer
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.data.mpsv.ChangeType
import io.github.painter99.jobsearch.data.mpsv.IncrementRecord
import io.github.painter99.jobsearch.data.mpsv.MpsvOffersClient
import io.github.painter99.jobsearch.data.mpsv.OfferStore
import io.github.painter99.jobsearch.data.storage.CriteriaProvider
import io.github.painter99.jobsearch.data.storage.LocationProfile
import io.github.painter99.jobsearch.data.storage.ProfileProvider
import io.github.painter99.jobsearch.db.FakeSeenDao
import io.github.painter99.jobsearch.pipeline.InMemorySyncAnchorStore
import io.github.painter99.jobsearch.pipeline.OfferSyncEngine
import io.github.painter99.jobsearch.pipeline.OfferSyncEngine.DayResult
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T3: OffersViewModel — filtrování (latka + profil), seen set, sync zprávy,
 * bootstrap download (úspěch i selhání).
 *
 * Reálné komponenty nad MockWebServerem (lekce run #10 — final třídy se
 * nedají fakovat): [OfferSyncEngine] + [MpsvOffersClient] proti serveru,
 * přírůstky skutečně gzipped (lekce run #17). Latka/profil = jednoduché
 * lambdy nad interfacema (CriteriaProvider/ProfileProvider — fakovat lze),
 * [FakeOfferStore] zůstává (OfferStore je interface).
 *
 * Dispatchers.setMain(testDispatcher) — viewModelScope běží na test
 * dispatcheru; síťové volání běží na reálném Dispatchers.IO, proto
 * [awaitSettled] polluje se skutečným sleepem (advanceUntilIdle sám
 * nepočká na IO).
 */
class OffersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    /** In-memory OfferStore — interface, fakovat lze. Import bootstrapu = řádky s portalId. */
    private class FakeOfferStore(var offers: List<JobOffer>) : OfferStore {
        override suspend fun load(): Int = offers.size

        override suspend fun importBootstrap(dumpFile: File): Int {
            offers = dumpFile.readLines().mapNotNull { line ->
                val portalId = line.trim().toLongOrNull() ?: return@mapNotNull null
                offer(portalId)
            }
            return offers.size
        }

        override suspend fun applyIncrement(records: List<IncrementRecord>): Int {
            var changed = 0
            records.forEach { record ->
                when (record.changeType) {
                    ChangeType.REMOVED -> {
                        if (offers.any { it.portalId == record.portalId }) {
                            offers = offers.filter { it.portalId != record.portalId }
                            changed++
                        }
                    }
                    else -> {
                        val fresh = record.offer
                        if (fresh != null) {
                            offers = offers.filter { it.portalId != fresh.portalId } + fresh
                            changed++
                        }
                    }
                }
            }
            return changed
        }

        override fun all(): List<JobOffer> = offers
        override fun count(): Int = offers.size
        override fun findByPortalId(portalId: Long): JobOffer? = offers.firstOrNull { it.portalId == portalId }
    }

    private companion object {
        fun offer(
            portalId: Long,
            salary: Int? = 45_000,
            shift: ShiftPattern? = ShiftPattern.SINGLE_SHIFT,
            municipalityId: String? = "Obec/503657",
            type: WorkLocation.LocationType? = WorkLocation.LocationType.MUNICIPALITY,
        ) = JobOffer(
            portalId = portalId,
            referenceNumber = "ref-$portalId",
            profession = "Profese $portalId",
            shiftPattern = shift,
            salaryFrom = salary,
            salaryTo = null,
            hoursPerWeek = null,
            employer = Employer(ico = "12345678", name = "Firma $portalId"),
            location = WorkLocation(
                type = type,
                municipalityId = municipalityId,
                districts = emptyList(),
                addressText = null,
                worksiteMunicipalityIds = emptyList(),
            ),
            benefits = emptyList(),
            url = null,
            agencyConsent = null,
            userConsent = null,
        )

        fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneId.of("Europe/Prague"))
    }

    /**
     * Přírůstek na MockWebServer — tělo musí být skutečně gzipped,
     * klient ho gunzipuje (lekce run #17). Vzor = OfferSyncEngineTest.
     */
    private fun enqueueIncrement(vararg portalIds: Long) {
        val items = portalIds.joinToString(",") { id ->
            """
            {"portalId": $id, "typZmenyOpenData": {"id": "TypyZmenOpenData/novy"},
             "pozadovanaProfese": {"cs": "Profese $id"}, "urlAdresa": "https://portal.mpsv.cz/$id"}
            """.trimIndent()
        }
        val body = """{"polozky": [$items]}"""
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bos).use { it.write(body.toByteArray(Charsets.UTF_8)) }
        server.enqueue(MockResponse().setBody(okio.Buffer().apply { write(bos.toByteArray()) }))
    }

    private fun offersClient(): MpsvOffersClient =
        MpsvOffersClient(
            client = OkHttpClient(),
            bootstrapUrl = server.url("/od/soubory/volna-mista/volna-mista.json").toString(),
            incrementUrlBase = server.url("/od/soubory/volna-mista-prirustek").toString(),
        )

    private fun viewModel(
        store: FakeOfferStore,
        criteria: UserCriteria = UserCriteria(40_000, true),
        profile: LocationProfile = LocationProfile(municipalityIds = setOf("Obec/503657")),
        anchor: LocalDate = LocalDate.of(2026, 10, 6),
        client: MpsvOffersClient = offersClient(),
    ): Pair<OffersViewModel, FakeSeenDao> {
        val seenDao = FakeSeenDao()
        val syncEngine = OfferSyncEngine(
            offersClient = client,
            store = store,
            anchorStore = InMemorySyncAnchorStore(anchor),
        )
        val vm = OffersViewModel(
            offerStore = store,
            criteriaProvider = object : CriteriaProvider {
                override suspend fun load(): UserCriteria = criteria
            },
            profileProvider = object : ProfileProvider {
                override suspend fun load(): LocationProfile = profile
            },
            seenDao = seenDao,
            syncEngine = syncEngine,
            offersClient = client,
            offersFile = File("/tmp/offers-test-${System.nanoTime()}.json"),
            clock = fixedClock(),
        )
        return vm to seenDao
    }

    /**
     * Počká, až ViewModel dosedne (žádná běžící operace). Síťové volání běží
     * na reálném Dispatchers.IO — advanceUntilIdle nepočká, proto polluje
     * se skutečným sleepem.
     */
    private suspend fun TestScope.awaitSettled(vm: OffersViewModel, timeoutMs: Long = 10_000): OffersUiState {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            advanceUntilIdle()
            val state = vm.state.value
            if (!state.loading && !state.syncing && !state.downloading) return state
            Thread.sleep(20)
        }
        return vm.state.value
    }

    @Test
    fun `filters by criteria and profile - matching offer visible`() = runTest(dispatcher) {
        val (vm, _) = viewModel(store = FakeOfferStore(listOf(offer(1))))
        val state = awaitSettled(vm)
        assertEquals(listOf(1L), state.offers.map { it.portalId })
        assertFalse(state.bootstrapNeeded)
    }

    @Test
    fun `filters by criteria - salary below minimum rejected`() = runTest(dispatcher) {
        val (vm, _) = viewModel(store = FakeOfferStore(listOf(offer(2, salary = 39_999))))
        val state = awaitSettled(vm)
        assertTrue(state.offers.isEmpty())
    }

    @Test
    fun `filters by criteria - three-shift rejected when singleShiftOnly`() = runTest(dispatcher) {
        val (vm, _) = viewModel(store = FakeOfferStore(listOf(offer(3, shift = ShiftPattern.THREE_SHIFT))))
        val state = awaitSettled(vm)
        assertTrue(state.offers.isEmpty())
    }

    @Test
    fun `filters by profile - offer outside profile municipalities rejected`() = runTest(dispatcher) {
        val (vm, _) = viewModel(store = FakeOfferStore(listOf(offer(4, municipalityId = "Obec/999999"))))
        val state = awaitSettled(vm)
        assertTrue(state.offers.isEmpty())
    }

    @Test
    fun `empty store - bootstrapNeeded true`() = runTest(dispatcher) {
        val (vm, _) = viewModel(store = FakeOfferStore(emptyList()))
        val state = awaitSettled(vm)
        assertTrue(state.bootstrapNeeded)
        assertTrue(state.offers.isEmpty())
    }

    @Test
    fun `criteria summary - default latka text`() = runTest(dispatcher) {
        val (vm, _) = viewModel(store = FakeOfferStore(emptyList()))
        val state = awaitSettled(vm)
        assertEquals("≥ 40000 Kč, jednosměnné", state.criteriaSummary)
    }

    @Test
    fun `markSeen - adds to seen set and dao`() = runTest(dispatcher) {
        val (vm, seenDao) = viewModel(store = FakeOfferStore(emptyList()))
        advanceUntilIdle()
        vm.markSeen("mpsv/7")
        advanceUntilIdle()
        assertTrue(vm.state.value.seenKeys.contains("mpsv/7"))
        assertTrue(seenDao.isSeen("mpsv/7"))
    }

    @Test
    fun `syncDaily - applied changes refresh list and message`() = runTest(dispatcher) {
        val store = FakeOfferStore(emptyList())
        enqueueIncrement(101, 102, 103, 104, 105)
        val (vm, _) = viewModel(store = store)

        vm.syncDaily()
        val state = awaitSettled(vm)

        assertEquals("Sync: +5 změn", state.syncMessage)
        assertFalse(state.syncing)
        // 5 nových nabídek v store (mzda null → latkou neprojdou, proto totalCount)
        assertEquals(5, state.totalCount)
    }

    @Test
    fun `syncDaily - only skipped days keep list and show no-publication message`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        val (vm, _) = viewModel(
            store = FakeOfferStore(listOf(offer(8))),
            anchor = LocalDate.of(2026, 10, 5), // 2 dny před pevnými hodinami (2026-10-07)
        )

        vm.syncDaily()
        val state = awaitSettled(vm)

        assertEquals("Žádné nové přírůstky (2 dny bez publikace)", state.syncMessage)
        assertEquals(listOf(8L), state.offers.map { it.portalId })
    }

    @Test
    fun `syncDaily - no days since anchor - no message and no request`() = runTest(dispatcher) {
        val (vm, _) = viewModel(
            store = FakeOfferStore(listOf(offer(9))),
            anchor = LocalDate.of(2026, 10, 7), // kotva = dnes → žádné dny
        )

        vm.syncDaily()
        val state = awaitSettled(vm)

        assertEquals(null, state.syncMessage)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `downloadBootstrap - http error shows failure message`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(404))
        val (vm, _) = viewModel(store = FakeOfferStore(emptyList()))
        advanceUntilIdle()

        vm.downloadBootstrap()
        val state = awaitSettled(vm)

        assertEquals("Stahování selhalo (HTTP 404)", state.syncMessage)
        assertFalse(state.downloading)
        assertTrue(state.bootstrapNeeded)
    }

    @Test
    fun `downloadBootstrap - success imports offers and clears bootstrapNeeded`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("101\n102\n103"))
        val store = FakeOfferStore(emptyList())
        val (vm, _) = viewModel(store = store)

        vm.downloadBootstrap()
        val state = awaitSettled(vm)

        assertFalse(state.downloading)
        assertFalse(state.bootstrapNeeded)
        assertEquals(listOf(101L, 102L, 103L), state.offers.map { it.portalId })
        assertEquals(3, state.totalCount)
    }
}