package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.Agency
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.ares.AresClient
import io.github.painter99.jobsearch.data.mpsv.MpsvAgencyClient
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
 * AgencyDetector = pilíř č. 1A (US1): primárně MPSV oficiální seznam IČO,
 * doplněk ARES VR text. N7: výstup nikdy netvrdí fakt bez zdroje.
 */
class AgencyDetectorTest {

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

    private fun detector(): AgencyDetector {
        val mpsv = MpsvAgencyClient(
            client = OkHttpClient(),
            baseUrl = server.url("/od/soubory/agentury-prace/agentury-prace.json").toString(),
        )
        val ares = AresClient(
            client = OkHttpClient(),
            baseUrl = server.url("/ekonomicke-subjekty-v-be/rest").toString(),
        )
        return AgencyDetector(mpsv, ares)
    }

    private fun enqueueMpsv() {
        server.enqueue(MockResponse().setBody(fixture()))
    }

    @Test
    fun jobsContact_agencyMpsv() = runTest {
        enqueueMpsv()

        val status = detector().isAgency("17181879")

        assertEquals(AgencyStatus.AGENCY_MPSV, status)
    }

    @Test
    fun icoNotInList_emptyVr_NOT_AGENCY() = runTest {
        // MPSV odpoví (ico není v seznamu), ARES VR vrátí prázdné předměty
        enqueueMpsv()
        server.enqueue(
            MockResponse().setBody(
                """{"icoId": "ico1", "zaznamy": [{"cinnosti": {"predmetPodnikani": []}}]}"""
            )
        )

        val status = detector().isAgency("99999999")

        assertEquals(AgencyStatus.NOT_AGENCY, status)
    }

    @Test
    fun icoNotInList_vrMatch_AGENCY_VR() = runTest {
        enqueueMpsv()
        server.enqueue(
            MockResponse().setBody(
                """{"icoId": "ico2", "zaznamy": [{"cinnosti": {"predmetPodnikani":
                   [{"hodnota": "Zprostředkování zaměstnání"}]}}]}"""
            )
        )

        val status = detector().isAgency("88888888")

        assertEquals(AgencyStatus.AGENCY_VR, status)
    }

    @Test
    fun aresUnavailable_UNDETERMINED() = runTest {
        // MPSV odpoví (ico mimo seznam), ARES VR vrátí 500 → graceful degradation
        enqueueMpsv()
        server.enqueue(MockResponse().setResponseCode(500))

        val status = detector().isAgency("99999999")

        assertEquals(AgencyStatus.UNDETERMINED, status)
    }

    @Test
    fun mpsvListUnavailable_UNDETERMINED() = runTest {
        // MPSV 503 + ARES VR 500 → ani jeden zdroj nedává důvod k tvrzení
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(500))

        val status = detector().isAgency("17181879")

        assertEquals(AgencyStatus.UNDETERMINED, status)
    }

    @Test
    fun mpsvListUnavailable_vrMatch_AGENCY_VR() = runTest {
        // degradation: i bez MPSV seznamu dokáže VR shoda označit agenturu
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(
            MockResponse().setBody(
                """{"icoId": "ico3", "zaznamy": [{"cinnosti": {"predmetPodnikani":
                   [{"hodnota": "Zprostředkování zaměstnání"}]}}]}"""
            )
        )

        val status = detector().isAgency("77777777")

        assertEquals(AgencyStatus.AGENCY_VR, status)
    }

    @Test
    fun invalidIco_returnsUNDETERMINED_withoutNetwork() = runTest {
        // krátké/nevalidní IČO se neposílá nikam (žádný enqueue → MockWebServer by spadl)
        val status = detector().isAgency("123")
        assertEquals(AgencyStatus.UNDETERMINED, status)
    }

    @Test
    fun fetch_loadsAgencyList() = runTest {
        enqueueMpsv()

        val result = detector().loadAgencies()

        assertTrue(result is FetchResult.Success)
        assertEquals(3, (result as FetchResult.Success).data.size)
    }

    @Test
    fun agencyName_availableInDetector() = runTest {
        enqueueMpsv()
        val d = detector()

        val result = d.loadAgencies()
        assertTrue(result is FetchResult.Success)

        // název agencies je k dispozici pro UI (není osobní údaj);
        // druhé volání isAgency už čte in-memory seznam (fetch-once)
        assertEquals(AgencyStatus.AGENCY_MPSV, d.isAgency("17181879"))
        assertEquals("Jobs Contact Personal, s.r.o.", d.agencyName("17181879"))
    }
}