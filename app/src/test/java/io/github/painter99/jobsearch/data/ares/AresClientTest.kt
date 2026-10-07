package io.github.painter99.jobsearch.data.ares

import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val subject = (result as FetchResult.Success).data
        assertEquals("17181879", subject.ico)
        assertEquals("Jobs Contact Personal, s.r.o.", subject.businessName)
        assertEquals("Brno", subject.municipality)
        assertEquals("Brno-město", subject.districtName)
        assertEquals("2022-05-26", subject.foundedOn)
        assertTrue(subject.czNace.contains("73110"))
    }

    @Test
    fun detail_http404ReturnsHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client().detail("99999999")

        assertTrue(result is FetchResult.HttpError)
        assertEquals(404, (result as FetchResult.HttpError).code)
    }

    @Test
    fun detail_invalidJsonReturnsParseError() = runTest {
        server.enqueue(MockResponse().setBody("<html>error</html>"))

        val result = client().detail("17181879")

        assertTrue(result is FetchResult.ParseError)
    }

    // --- vr (předměty podnikání) ---

    @Test
    fun vr_businessActivities() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ares-vr-17181879.json")))

        val result = client().vrBusinessActivities("17181879")

        assertTrue(result is FetchResult.Success)
        val activities = (result as FetchResult.Success).data
        assertTrue(
            "VR musí obsahovat Zprostředkování zaměstnání",
            activities.any { it.equals("Zprostředkování zaměstnání", ignoreCase = true) },
        )
    }

    @Test
    fun vr_sigmaHasNoIntermediation() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ares-vr-64608212.json")))

        val result = client().vrBusinessActivities("64608212")

        assertTrue(result is FetchResult.Success)
        val activities = (result as FetchResult.Success).data
        assertTrue(activities.isNotEmpty())
        assertTrue(activities.none { it.contains("zprostředkování zaměstnání", ignoreCase = true) })
    }

    @Test
    fun vr_http500ReturnsHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client().vrBusinessActivities("17181879")

        assertTrue(result is FetchResult.HttpError)
    }

    // --- search (POST vyhledat, M1.3 resolver) ---

    @Test
    fun search_parsesFixtureIntoCompanies() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ares-vyhledat-28130.json")))

        val result = client().search(listOf("28130"))

        assertTrue("očekáván Success, byl: $result", result is FetchResult.Success)
        val companies = (result as FetchResult.Success).data
        assertEquals(44, companies.size)
        val ish = companies.firstOrNull { it.ico == "25272365" }
        assertTrue("ISH PUMPS musí být v odpovědi", ish != null)
        assertEquals("ISH PUMPS OLOMOUC a.s.", ish!!.businessName)
        assertEquals("Olomouc", ish.municipality)
        assertTrue(ish.czNace.contains("28130"))
    }

    @Test
    fun search_sendsPostWithCzNaceBody() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ares-vyhledat-28130.json")))

        client().search(listOf("28130"))

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/ekonomicke-subjekty-v-be/rest/ekonomicke-subjekty/vyhledat", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("tělo musí obsahovat czNace", body.contains("\"czNace\""))
        assertTrue(body.contains("28130"))
    }

    @Test
    fun search_emptyNaceListReturnsParseError() = runTest {
        val result = client().search(emptyList())

        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun search_http500ReturnsHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client().search(listOf("28130"))

        assertTrue(result is FetchResult.HttpError)
    }

    @Test
    fun search_tolerant_nullSidloAndMissingFields() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"pocetCelkem": 2, "ekonomickeSubjekty": [
                     {"ico": "11111111", "obchodniJmeno": "Test s.r.o.", "sidlo": null},
                     {"obchodniJmeno": "Bez IČO"}
                   ]}""",
            )
        )

        val result = client().search(listOf("28130"))

        assertTrue(result is FetchResult.Success)
        val companies = (result as FetchResult.Success).data
        assertEquals(2, companies.size)
        assertEquals("11111111", companies[0].ico)
        assertNull(companies[0].municipality)
        assertEquals("", companies[1].ico)
    }
}