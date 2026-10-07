package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.Employer
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.WorkLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OfferDedupe testy (pipeline-dedupe, AC1) — klíče: portalId →
 * referenceNumber → kanonizované URL → IČO+profese.
 */
class OfferDedupeTest {

    private fun offer(
        portalId: Long = 0L,
        referenceNumber: String = "",
        url: String? = null,
        ico: String? = null,
        profession: String = "Test",
    ) = JobOffer(
        portalId = portalId,
        referenceNumber = referenceNumber,
        profession = profession,
        shiftPattern = null,
        salaryFrom = null,
        salaryTo = null,
        hoursPerWeek = null,
        employer = ico?.let { Employer(it, "Firma") },
        location = WorkLocation.NONE,
        benefits = emptyList(),
        url = url,
        agencyConsent = null,
        userConsent = null,
    )

    @Test
    fun `duplicitní portalId se zahodí, první výskyt vyhrává`() {
        val a = offer(portalId = 111)
        val b = offer(portalId = 111, profession = "Jiná profese")
        val result = OfferDedupe.dedupe(listOf(a, b))
        assertEquals(1, result.size)
        assertEquals("Test", result[0].profession)
    }

    @Test
    fun `stejné referenční číslo bez portalId = duplicita`() {
        val a = offer(referenceNumber = "35101880724")
        val b = offer(referenceNumber = "35101880724")
        assertEquals(1, OfferDedupe.dedupe(listOf(a, b)).size)
    }

    @Test
    fun `kanonizace URL - rps a utm se stripnou, host lowercase`() {
        assertEquals(
            "https://portal.mpsv.cz/nabidka/67299543",
            OfferDedupe.canonicalUrl("https://portal.mpsv.cz/nabidka/67299543?rps=abc123"),
        )
        assertEquals(
            "https://prace.cz/nabidka/abc?keep=1",
            OfferDedupe.canonicalUrl("https://prace.cz/nabidka/abc?keep=1&utm_source=x&UTM_MEDIUM=y&rps=z"),
        )
        assertEquals(
            "https://portal.cz/Path",
            OfferDedupe.canonicalUrl("https://PORTAL.cz/Path"),
        )
    }

    @Test
    fun `duplicitní URL po kanonizaci = duplicita`() {
        val a = offer(url = "https://portal.mpsv.cz/nabidka/67299543?rps=abc")
        val b = offer(url = "https://portal.mpsv.cz/nabidka/67299543")
        assertEquals(1, OfferDedupe.dedupe(listOf(a, b)).size)
    }

    @Test
    fun `IČO + profese páruje inzeráty z webů bez ID`() {
        val a = offer(ico = "27080439", profession = "Vývojáři softwaru")
        val b = offer(ico = "27080439", profession = "vývojáři softwaru ") // case + mezery
        assertEquals(1, OfferDedupe.dedupe(listOf(a, b)).size)
    }

    @Test
    fun `různé klíče se nekolidují - portalId vs ref vs url`() {
        val a = offer(portalId = 1)
        val b = offer(referenceNumber = "X1")
        val c = offer(url = "https://portal.cz/a")
        assertEquals(3, OfferDedupe.dedupe(listOf(a, b, c)).size)
    }

    @Test
    fun `nabídka bez identifikátoru se ponechá (tolerantně)`() {
        val a = offer()
        val b = offer()
        // bez klíče nelze dedupovat — ponechají se obě (netvrdíme duplicitu bez důkazu)
        assertEquals(2, OfferDedupe.dedupe(listOf(a, b)).size)
    }

    @Test
    fun `pořadí vstupu se zachová a dedupe je deterministické`() {
        val offers = listOf(
            offer(portalId = 1),
            offer(portalId = 2),
            offer(portalId = 1),
            offer(portalId = 3),
            offer(portalId = 2),
        )
        val result = OfferDedupe.dedupe(offers)
        assertEquals(listOf(1L, 2L, 3L), result.map { it.portalId })
        assertTrue(result.size == 3)
    }
}