package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Municipality
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
 * T2: MunicipalityRepository — fetch-once cache (vzor AgenturaDetector).
 * Druhé volání nesmí jít na síť (MockWebServer by vrátil chybu / počítal requesty).
 */
class MunicipalityRepositoryTest {

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

    private fun repository(): MunicipalityRepository {
        val client = MpsvCiselnikyClient(
            client = OkHttpClient(),
            obceUrl = server.url("/od/soubory/ciselniky/obce.json").toString(),
        )
        return MunicipalityRepository(client)
    }

    @Test
    fun `fetch once - second call served from cache without network`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                javaClass.getResourceAsStream("/obce-sample.json")!!
                    .readBytes().toString(Charsets.UTF_8)
            )
        )
        val repository = repository()

        val first = repository.getMunicipalities()
        assertTrue(first is FetchResult.Success)
        assertEquals(12, (first as FetchResult.Success).data.size)

        // žádný další enqueue — kdyby šel request na síť, selže na prázdné frontě
        val second = repository.getMunicipalities()
        assertTrue(second is FetchResult.Success)
        assertEquals(12, (second as FetchResult.Success).data.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `failed fetch is not cached - next call retries network`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val repository = repository()

        val first = repository.getMunicipalities()
        assertTrue(first is FetchResult.HttpError)

        server.enqueue(
            MockResponse().setBody(
                javaClass.getResourceAsStream("/obce-sample.json")!!
                    .readBytes().toString(Charsets.UTF_8)
            )
        )
        val second = repository.getMunicipalities()
        assertTrue(second is FetchResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `nameMap - municipality id to name`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                javaClass.getResourceAsStream("/obce-sample.json")!!
                    .readBytes().toString(Charsets.UTF_8)
            )
        )
        val names = repository().nameMap()
        assertEquals("Lutín", names["Obec/503657"])
        assertEquals("Olomouc", names["Obec/500496"])
    }
}