package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * T2: MpsvOffersClient — bootstrap download (stream do souboru) +
 * denní přírůstek (gz). MockWebServer, žádná reálná síť.
 * Fixture prirustek-sample.json.gz = živý přírůstek 2026-10-05 (8 záznamů).
 */
class MpsvOffersClientTest {

    private lateinit var server: MockWebServer

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = MpsvOffersClient(
        client = OkHttpClient(),
        bootstrapUrl = server.url("/od/soubory/volna-mista/volna-mista.json").toString(),
        incrementUrlBase = server.url("/od/soubory/volna-mista-prirustek").toString(),
    )

    // --- bootstrap ---

    @Test
    fun downloadBootstrap_streamsToFile() = runTest {
        val payload = """{"polozky": [{"portalId": 1, "pozadovanaProfese": {"cs": "Test"}}]}"""
        server.enqueue(MockResponse().setBody(payload))
        val target = tmp.newFile("volna-mista.json")

        val result = client().downloadBootstrap(target)

        assertTrue("očekáván Success, byl: $result", result is FetchResult.Success)
        assertEquals(target, (result as FetchResult.Success).data)
        assertEquals(payload.length.toLong(), target.length())
    }

    @Test
    fun downloadBootstrap_httpErrorReturnsHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val target = tmp.newFile("volna-mista.json")

        val result = client().downloadBootstrap(target)

        assertTrue(result is FetchResult.HttpError)
        assertEquals(503, (result as FetchResult.HttpError).code)
    }

    @Test
    fun downloadBootstrap_emptyBodyReturnsParseError() = runTest {
        server.enqueue(MockResponse().setBody(""))
        val target = tmp.newFile("volna-mista.json")

        val result = client().downloadBootstrap(target)

        assertTrue(result is FetchResult.ParseError)
    }

    // --- přírůstky ---

    @Test
    fun fetchIncrement_gzFixtureParses() = runTest {
        val gz = javaClass.getResourceAsStream("/prirustek-sample.json.gz")!!.readBytes()
        server.enqueue(MockResponse().setBody(okio.Buffer().apply { write(gz) }))

        val result = client().fetchIncrement(LocalDate.parse("2026-10-05"))

        assertTrue("očekáván Success, byl: $result", result is FetchResult.Success)
        val text = (result as FetchResult.Success).data
        assertTrue(text.contains("polozky"))
        assertEquals(
            "/od/soubory/volna-mista-prirustek/volna-mista-prirustek-2026-10-05.json.gz",
            server.takeRequest().path,
        )
    }

    @Test
    fun fetchIncrement_404ReturnsHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client().fetchIncrement(LocalDate.parse("2026-10-06"))

        assertTrue(result is FetchResult.HttpError)
        assertEquals(404, (result as FetchResult.HttpError).code)
    }

    @Test
    fun fetchIncrement_corruptGzipReturnsParseError() = runTest {
        server.enqueue(MockResponse().setBody("toto není gzip"))

        val result = client().fetchIncrement(LocalDate.parse("2026-10-05"))

        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun fetchIncrement_networkFailureReturnsNetworkError() = runTest {
        val dead = MpsvOffersClient(
            client = OkHttpClient(),
            incrementUrlBase = "http://127.0.0.1:1/volna-mista-prirustek",
        )

        val result = dead.fetchIncrement(LocalDate.parse("2026-10-05"))

        assertTrue(result is FetchResult.NetworkError)
    }

    @Test
    fun fetchIncrement_urlContainsDate() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        client().fetchIncrement(LocalDate.parse("2026-10-05"))

        assertEquals(
            "/od/soubory/volna-mista-prirustek/volna-mista-prirustek-2026-10-05.json.gz",
            server.takeRequest().path,
        )
    }
}