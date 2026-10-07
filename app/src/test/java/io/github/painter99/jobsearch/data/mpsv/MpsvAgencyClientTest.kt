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
import org.junit.Test

/**
 * MpsvAgencyClient integration testy přes MockWebServer — žádná reálná síť
 * (WSW ChmuDataSourceTest vzor). Fixture = živá data 7. 10. 2026.
 */
class MpsvAgencyClientTest {

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

    private fun fixture(): String =
        javaClass.getResourceAsStream("/agentury-prace-sample.json")!!
            .bufferedReader().use { it.readText() }

    private fun client() = MpsvAgencyClient(
        client = OkHttpClient(),
        baseUrl = server.url("/od/soubory/agentury-prace/agentury-prace.json").toString(),
    )

    @Test
    fun fetch_parsesFixtureIntoAgencies() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))

        val result = client().fetch()

        assertTrue("očekáván Success, byl: $result", result is FetchResult.Success)
        val agencies = (result as FetchResult.Success).data
        assertEquals(3, agencies.size)
        assertTrue(agencies.any { it.ico == "17181879" })
    }

    @Test
    fun fetch_http500ReturnsHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client().fetch()

        assertTrue(result is FetchResult.HttpError)
        assertEquals(500, (result as FetchResult.HttpError).code)
    }

    @Test
    fun fetch_invalidJsonReturnsParseError() = runTest {
        server.enqueue(MockResponse().setBody("tohle není JSON"))

        val result = client().fetch()

        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun fetch_emptyItemsReturnsParseError() = runTest {
        // 2xx s prázdným seznamem = podezřelý payload (seznam má ~1900 záznamů)
        server.enqueue(MockResponse().setBody("""{"polozky": []}"""))

        val result = client().fetch()

        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun fetch_networkFailureReturnsNetworkError() = runTest {
        val dead = MpsvAgencyClient(
            client = OkHttpClient(),
            baseUrl = "http://127.0.0.1:1/agentury-prace.json", // nikdo nenaslouchá
        )

        val result = dead.fetch()

        assertTrue(result is FetchResult.NetworkError)
    }
}