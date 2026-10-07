package io.github.painter99.jobsearch.data.pracecz

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
 * T4: PraceCzClient + PraceCzListingParser — deep linky z výpisů.
 * Fixtures = strukturální zrcadlo živého výpisu Lutín 7. 10. 2026
 * (25 odkazů, ?rps= tracking, paginace ?page=N).
 */
class PraceCzClientTest {

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
        javaClass.getResourceAsStream("/$name")!!
            .bufferedReader().use { it.readText() }

    private fun client() = PraceCzClient(
        client = OkHttpClient(),
        baseUrl = server.url("/").toString().trimEnd('/'),
    )

    // --- parser (fixtures, žádná síť) ---

    @Test
    fun parser_extractsUniqueDeepLinksAndStripsTracking() {
        val page = PraceCzListingParser().parse(fixture("pracecz-listing-page1.html"))

        // simulace na živých datech: 5 unikátních linků (duplicita ?rps=9999 dedupnuta)
        assertEquals(5, page.deepLinks.size)
        assertTrue(page.deepLinks.none { it.contains("rps=") })
        assertTrue(page.deepLinks.none { it.contains("utm_") })
        assertTrue(page.deepLinks.all { it.startsWith("https://www.prace.cz/nabidka/") || it.startsWith("https://www.prace.cz/firma/") })
        // firma slug link (bonus pro resolver)
        assertTrue(page.deepLinks.any { it.startsWith("https://www.prace.cz/firma/3fyme2-mediacall-s-r-o/nabidka/") })
        assertTrue(page.hasNextPage)
    }

    @Test
    fun parser_lastPageHasNoNext() {
        val page = PraceCzListingParser().parse(fixture("pracecz-listing-last.html"))

        assertEquals(2, page.deepLinks.size)
        assertTrue(!page.hasNextPage)
    }

    @Test
    fun parser_ignoresNonOfferLinks() {
        val page = PraceCzListingParser().parse(fixture("pracecz-listing-page1.html"))

        // výpisy jiných lokalit a externí portály se ignorují
        assertTrue(page.deepLinks.none { it.contains("/nabidky/") })
        assertTrue(page.deepLinks.none { it.contains("jobs.cz") })
    }

    @Test(expected = java.io.IOException::class)
    fun parser_suspiciousStructureThrows() {
        // žádný deep link = podezřelá struktura (PRD R1) → ParseError signál
        PraceCzListingParser().parse("<html><body><a href='/nabidky/olomoucky-kraj/lutin/'>výpis</a></body></html>")
    }

    // --- client (MockWebServer) ---

    @Test
    fun fetchListing_parsesServerResponse() = runTest {
        server.enqueue(MockResponse().setBody(fixture("pracecz-listing-page1.html")))

        val result = client().fetchListing("olomoucky-kraj", "lutin")

        assertTrue("očekáván Success, byl: $result", result is FetchResult.Success)
        val page = (result as FetchResult.Success).data
        assertEquals(5, page.deepLinks.size)
        assertTrue(page.hasNextPage)
        assertEquals("/nabidky/olomoucky-kraj/lutin/", server.takeRequest().path)
    }

    @Test
    fun fetchListing_page2AppendsQuery() = runTest {
        server.enqueue(MockResponse().setBody(fixture("pracecz-listing-last.html")))

        client().fetchListing("olomoucky-kraj", "lutin", page = 2)

        assertEquals("/nabidky/olomoucky-kraj/lutin/?page=2", server.takeRequest().path)
    }

    @Test
    fun fetchListing_http404ReturnsHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client().fetchListing("olomoucky-kraj", "lutin")

        assertTrue(result is FetchResult.HttpError)
        assertEquals(404, (result as FetchResult.HttpError).code)
    }

    @Test
    fun fetchListing_suspiciousHtmlReturnsParseError() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>žádné odkazy</body></html>"))

        val result = client().fetchListing("olomoucky-kraj", "lutin")

        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun fetchListing_networkFailureReturnsNetworkError() = runTest {
        val dead = PraceCzClient(
            client = OkHttpClient(),
            baseUrl = "http://127.0.0.1:1",
        )

        val result = dead.fetchListing("olomoucky-kraj", "lutin")

        assertTrue(result is FetchResult.NetworkError)
    }
}