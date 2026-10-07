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
import java.io.File
import java.time.LocalDate

/**
 * T1: MpsvCiselnikyClient — MockWebServer testy (žádná reálná síť).
 * Fixture obce-sample.json = živý číselník 7. 10. 2026 (12 obcí, Lutín Obec/503657).
 */
class MpsvCiselnikyClientTest {

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
        javaClass.getResourceAsStream("/obce-sample.json")!!
            .bufferedReader().use { it.readText() }

    private fun client() = MpsvCiselnikyClient(
        client = OkHttpClient(),
        obceUrl = server.url("/od/soubory/ciselniky/obce.json").toString(),
    )

    @Test
    fun fetchMunicipalities_parsesFixture() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))

        val result = client().fetchMunicipalities()

        assertTrue("očekáván Success, byl: $result", result is FetchResult.Success)
        val municipalities = (result as FetchResult.Success).data
        assertEquals(12, municipalities.size)
        val lutin = municipalities.first { it.name == "Lutín" }
        assertEquals("Obec/503657", lutin.id)
        assertEquals("503657", lutin.code)
        assertEquals("Okres/3805", lutin.districtId)
    }

    @Test
    fun fetchMunicipalities_http500ReturnsHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client().fetchMunicipalities()

        assertTrue(result is FetchResult.HttpError)
        assertEquals(500, (result as FetchResult.HttpError).code)
    }

    @Test
    fun fetchMunicipalities_invalidJsonReturnsParseError() = runTest {
        server.enqueue(MockResponse().setBody("not JSON"))

        val result = client().fetchMunicipalities()

        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun fetchMunicipalities_emptyItemsReturnsParseError() = runTest {
        server.enqueue(MockResponse().setBody("""{"polozky": []}"""))

        val result = client().fetchMunicipalities()

        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun fetchMunicipalities_networkFailureReturnsNetworkError() = runTest {
        val dead = MpsvCiselnikyClient(
            client = OkHttpClient(),
            obceUrl = "http://127.0.0.1:1/obce.json",
        )

        val result = dead.fetchMunicipalities()

        assertTrue(result is FetchResult.NetworkError)
    }

    @Test
    fun parser_tolerantParsing_nullAndMissingFields() {
        val root = org.json.JSONObject(
            """
            {"polozky": [
              {"id": "Obec/1", "nazev": {"cs": "Obec A"}, "okres": null},
              {"nazev": {"cs": "bez id — zahodit"}},
              {"id": "Obec/2"}
            ]}
            """.trimIndent()
        )
        val parsed = MunicipalityParser().parse(root)
        assertEquals(2, parsed.size)
        assertEquals(null, parsed[0].districtId)
        assertEquals("", parsed[1].name)
    }
}