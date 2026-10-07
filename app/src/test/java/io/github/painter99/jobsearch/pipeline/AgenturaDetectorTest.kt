package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.Agentura
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.ares.AresClient
import io.github.painter99.jobsearch.data.mpsv.MpsvAgenturyClient
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
 * AgenturaDetector = pilíř č. 1A (US1): primárně MPSV oficiální seznam IČO,
 * doplněk ARES VR text. N7: výstup nikdy netvrdí fakt bez zdroje.
 */
class AgenturaDetectorTest {

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

    private fun detector(): AgenturaDetector {
        val mpsv = MpsvAgenturyClient(
            client = OkHttpClient(),
            baseUrl = server.url("/od/soubory/agentury-prace/agentury-prace.json").toString(),
        )
        val ares = AresClient(
            client = OkHttpClient(),
            baseUrl = server.url("/ekonomicke-subjekty-v-be/rest").toString(),
        )
        return AgenturaDetector(mpsv, ares)
    }

    private fun enqueueMpsv() {
        server.enqueue(MockResponse().setBody(fixture()))
    }

    @Test
    fun jobsContact_agenturaMpsv() = runTest {
        enqueueMpsv()

        val status = detector().jeAgentura("17181879")

        assertEquals(AgenturaStatus.AGENTURA_MPSV, status)
    }

    @Test
    fun icoMimoSeznam_bezAresVr_NENI() = runTest {
        // MPSV odpoví (ico není v seznamu), ARES VR vrátí prázdné předměty
        enqueueMpsv()
        server.enqueue(
            MockResponse().setBody(
                """{"icoId": "ico1", "zaznamy": [{"cinnosti": {"predmetPodnikani": []}}]}"""
            )
        )

        val status = detector().jeAgentura("99999999")

        assertEquals(AgenturaStatus.NENI_AGENTURA, status)
    }

    @Test
    fun icoMimoSeznam_aresVrShoda_AGENTURA_VR() = runTest {
        enqueueMpsv()
        server.enqueue(
            MockResponse().setBody(
                """{"icoId": "ico2", "zaznamy": [{"cinnosti": {"predmetPodnikani":
                   [{"hodnota": "Zprostředkování zaměstnání"}]}}]}"""
            )
        )

        val status = detector().jeAgentura("88888888")

        assertEquals(AgenturaStatus.AGENTURA_VR, status)
    }

    @Test
    fun aresNedostupny_NEVYDECENO() = runTest {
        // MPSV odpoví (ico mimo seznam), ARES VR vrátí 500 → graceful degradation
        enqueueMpsv()
        server.enqueue(MockResponse().setResponseCode(500))

        val status = detector().jeAgentura("99999999")

        assertEquals(AgenturaStatus.NEVYDECENO, status)
    }

    @Test
    fun mpsvSeznamNedostupny_NEVYDECENO() = runTest {
        // MPSV 503 + ARES VR 500 → ani jeden zdroj nedává důvod k tvrzení
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(500))

        val status = detector().jeAgentura("17181879")

        assertEquals(AgenturaStatus.NEVYDECENO, status)
    }

    @Test
    fun mpsvSeznamNedostupny_aresVrShoda_AGENTURA_VR() = runTest {
        // degradation: i bez MPSV seznamu dokáže VR shoda označit agenturu
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(
            MockResponse().setBody(
                """{"icoId": "ico3", "zaznamy": [{"cinnosti": {"predmetPodnikani":
                   [{"hodnota": "Zprostředkování zaměstnání"}]}}]}"""
            )
        )

        val status = detector().jeAgentura("77777777")

        assertEquals(AgenturaStatus.AGENTURA_VR, status)
    }

    @Test
    fun nevalidniIco_vraciNEVYDECENO_bezSite() = runTest {
        // krátké/nevalidní IČO se neposílá nikam (žádný enqueue → MockWebServer by spadl)
        val status = detector().jeAgentura("123")
        assertEquals(AgenturaStatus.NEVYDECENO, status)
    }

    @Test
    fun fetch_vraciSeznamProZobrazeni() = runTest {
        enqueueMpsv()

        val result = detector().nactiSeznamAgentur()

        assertTrue(result is FetchResult.Success)
        assertEquals(3, (result as FetchResult.Success).data.size)
    }

    @Test
    fun nazevAgentury_jeVDetekci() = runTest {
        enqueueMpsv()
        val d = detector()

        val result = d.nactiSeznamAgentur()
        assertTrue(result is FetchResult.Success)

        // název agentury je k dispozici pro UI (není osobní údaj);
        // druhé volání jeAgentura už čte in-memory seznam (fetch-once)
        assertEquals(AgenturaStatus.AGENTURA_MPSV, d.jeAgentura("17181879"))
        assertEquals("Jobs Contact Personal, s.r.o.", d.nazevAgentury("17181879"))
    }
}