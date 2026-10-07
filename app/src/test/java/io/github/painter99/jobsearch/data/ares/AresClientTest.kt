package io.github.painter99.jobsearch.data.ares

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
 * AresClient testy přes MockWebServer — žádná reálná síť.
 * Fixtury = živé ARES odpovědi 7. 10. 2026 (Jobs Contact = agentura,
 * SIGMA 1868 = výrobní firma, ne agentura).
 */
class AresClientTest {

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

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }

    private fun client() = AresClient(
        client = OkHttpClient(),
        baseUrl = server.url("/ekonomicke-subjekty-v-be/rest").toString(),
    )

    // --- detail ---

    @Test
    fun detail_parseFixture() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ares-detail-17181879.json")))

        val result = client().detail("17181879")

        assertTrue(result is FetchResult.Success)
        val subjekt = (result as FetchResult.Success).data
        assertEquals("17181879", subjekt.ico)
        assertEquals("Jobs Contact Personal, s.r.o.", subjekt.obchodniJmeno)
        assertEquals("Brno", subjekt.obec)
        assertEquals("Brno-město", subjekt.nazevOkresu)
        assertEquals("2022-05-26", subjekt.datumVzniku)
        assertTrue(subjekt.czNace.contains("73110"))
    }

    @Test
    fun detail_http404VraciHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client().detail("99999999")

        assertTrue(result is FetchResult.HttpError)
        assertEquals(404, (result as FetchResult.HttpError).code)
    }

    @Test
    fun detail_nevalidniJsonVraciParseError() = runTest {
        server.enqueue(MockResponse().setBody("<html>error</html>"))

        val result = client().detail("17181879")

        assertTrue(result is FetchResult.ParseError)
    }

    // --- vr (předměty podnikání) ---

    @Test
    fun vr_predmetyPodnikani() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ares-vr-17181879.json")))

        val result = client().vrPredmetyPodnikani("17181879")

        assertTrue(result is FetchResult.Success)
        val predmety = (result as FetchResult.Success).data
        assertTrue(
            "VR musí obsahovat Zprostředkování zaměstnání",
            predmety.any { it.equals("Zprostředkování zaměstnání", ignoreCase = true) },
        )
    }

    @Test
    fun vr_sigmaNemaZprostredkovani() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ares-vr-64608212.json")))

        val result = client().vrPredmetyPodnikani("64608212")

        assertTrue(result is FetchResult.Success)
        val predmety = (result as FetchResult.Success).data
        assertTrue(predmety.isNotEmpty())
        assertTrue(predmety.none { it.contains("zprostředkování zaměstnání", ignoreCase = true) })
    }

    @Test
    fun vr_http500VraciHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client().vrPredmetyPodnikani("17181879")

        assertTrue(result is FetchResult.HttpError)
    }
}