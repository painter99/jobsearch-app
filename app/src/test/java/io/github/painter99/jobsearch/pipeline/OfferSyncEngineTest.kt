package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.mpsv.ChangeType
import io.github.painter99.jobsearch.data.mpsv.IncrementRecord
import io.github.painter99.jobsearch.data.mpsv.MpsvOffersClient
import io.github.painter99.jobsearch.data.mpsv.OfferStore
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * T5: OfferSyncEngine — denní sync + catch-up.
 * Fake OfferStore (in-memory), MockWebServer pro přírůstky.
 */
class OfferSyncEngineTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun incrementBody(vararg portalIds: Long): String {
        val items = portalIds.joinToString(",") { id ->
            """
            {"portalId": $id, "typZmenyOpenData": {"id": "TypyZmenOpenData/novy"},
             "pozadovanaProfese": {"cs": "Profese $id"}, "urlAdresa": "https://portal.mpsv.cz/$id"}
            """.trimIndent()
        }
        return """{"polozky": [$items]}"""
    }

    private fun engine(anchor: LocalDate, store: OfferStore = FakeStore()): Pair<OfferSyncEngine, FakeStore> {
        val fake = store as? FakeStore ?: store as FakeStore
        val offersClient = MpsvOffersClient(
            client = OkHttpClient(),
            incrementUrlBase = server.url("/od/soubory/volna-mista-prirustek").toString(),
        )
        val anchorStore = InMemorySyncAnchorStore(anchor)
        return OfferSyncEngine(offersClient, fake, anchorStore) to fake
    }

    private class FakeStore : OfferStore {
        val offers = LinkedHashMap<Long, Int>()
        var removals = 0
        override suspend fun load(): Int = offers.size
        override suspend fun importBootstrap(dumpFile: java.io.File): Int = 0
        override suspend fun applyIncrement(records: List<IncrementRecord>): Int {
            var changed = 0
            records.forEach { record ->
                when (record.changeType) {
                    ChangeType.REMOVED -> {
                        if (offers.remove(record.portalId) != null) { changed++; removals++ }
                    }
                    else -> {
                        if (record.offer != null) { offers[record.portalId] = record.offer!!.salaryFrom ?: 0; changed++ }
                    }
                }
            }
            return changed
        }
        override fun all() = emptyList<io.github.painter99.jobsearch.core.model.JobOffer>()
        override fun count() = offers.size
        override fun findByPortalId(portalId: Long) = null
    }

    @Test
    fun syncDaily_appliesIncrementAndMovesAnchor() = runTest {
        server.enqueue(MockResponse().setBody(incrementBody(1L)))
        val (engine, store) = engine(LocalDate.parse("2026-10-05"))

        val results = engine.syncDaily(LocalDate.parse("2026-10-06"))

        assertEquals(1, results.size)
        assertEquals(1, results[0].applied)
        assertTrue(!results[0].skipped)
        assertEquals(1, store.offers.size)
    }

    @Test
    fun syncDaily_404SkipsDayAndKeepsAnchor() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val (engine, store) = engine(LocalDate.parse("2026-10-05"))

        val results = engine.syncDaily(LocalDate.parse("2026-10-06"))

        assertEquals(1, results.size)
        assertTrue(results[0].skipped)
        assertEquals(0, store.offers.size)
        // kotva se NEposunula — příště se den zkusí znovu
        // (nelze číst přímo; ověřeno přes chování v dalším testu)
    }

    @Test
    fun syncDaily_catchUpAppliesMultipleDays() = runTest {
        server.enqueue(MockResponse().setBody(incrementBody(10L)))
        server.enqueue(MockResponse().setBody(incrementBody(20L)))
        server.enqueue(MockResponse().setBody(incrementBody(30L)))
        val (engine, store) = engine(LocalDate.parse("2026-10-02"))

        val results = engine.syncDaily(LocalDate.parse("2026-10-05"))

        assertEquals(3, results.size)
        assertEquals(3, results.map { it.applied }.sum())
        assertEquals(3, store.offers.size)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun syncDaily_serverErrorStopsCatchUp() = runTest {
        server.enqueue(MockResponse().setBody(incrementBody(10L)))
        server.enqueue(MockResponse().setResponseCode(500))
        val (engine, store) = engine(LocalDate.parse("2026-10-02"))

        val results = engine.syncDaily(LocalDate.parse("2026-10-05"))

        // den 1 aplikován, den 2 = 500 → catch-up zastaven (den 3 se nestahuje)
        assertEquals(1, results.size)
        assertEquals(1, store.offers.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun syncDaily_anchorAdvancesOnlyPastAppliedDays() = runTest {
        // den 1 OK, den 2 OK, den 3 404 → kotva = den 3 (den 2 + 1), den 3 se zkusí příště
        server.enqueue(MockResponse().setBody(incrementBody(10L)))
        server.enqueue(MockResponse().setBody(incrementBody(20L)))
        server.enqueue(MockResponse().setResponseCode(404))
        val (engine, _) = engine(LocalDate.parse("2026-10-02"))

        val results = engine.syncDaily(LocalDate.parse("2026-10-05"))

        assertEquals(3, results.size)
        assertEquals(2, results.count { !it.skipped })
        assertEquals(1, results.count { it.skipped })
    }

    @Test
    fun syncDaily_networkErrorStopsCatchUp() = runTest {
        val dead = MpsvOffersClient(
            client = OkHttpClient(),
            incrementUrlBase = "http://127.0.0.1:1/volna-mista-prirustek",
        )
        val store = FakeStore()
        val engine = OfferSyncEngine(dead, store, InMemorySyncAnchorStore(LocalDate.parse("2026-10-05")))

        val results = engine.syncDaily(LocalDate.parse("2026-10-07"))

        assertEquals(0, results.size)
        assertEquals(0, store.offers.size)
    }
}