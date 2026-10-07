package io.github.painter99.jobsearch

import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.core.model.Benefit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T4: Výhody → checklist auto-fill (Pavlova criteria strojově).
 * dovol (Dovolená navíc) + premie (Zvláštní prémie) = signály pro auto-fill.
 */
class BenefitsChecklistTest {

    private fun offer(benefits: List<Benefit>) = JobOffer(
        portalId = 1,
        referenceNumber = "X",
        profession = "Test",
        shiftPattern = ShiftPattern.SINGLE_SHIFT,
        salaryFrom = 45_000,
        salaryTo = null,
        hoursPerWeek = null,
        employer = null,
        location = WorkLocation.NONE,
        benefits = benefits,
        url = null,
        agencyConsent = null,
        userConsent = null,
    )

    @Test
    fun `dovol + premie = auto-fill signály latky`() {
        val n = offer(listOf(Benefit.EXTRA_VACATION, Benefit.SPECIAL_BONUS))
        assertTrue("dovol → dovolená navíc", n.benefits.contains(Benefit.EXTRA_VACATION))
        assertTrue("premie → zvláštní prémie", n.benefits.contains(Benefit.SPECIAL_BONUS))
        assertTrue(n.hasExtraVacation())
        assertTrue(n.hasSpecialBonus())
    }

    @Test
    fun `without extraVacation and specialBonus - no signals`() {
        val n = offer(listOf(Benefit.CANTEEN, Benefit.TRANSPORT))
        assertFalse(n.hasExtraVacation())
        assertFalse(n.hasSpecialBonus())
    }

    @Test
    fun `empty benefits - no signals`() {
        val n = offer(emptyList())
        assertFalse(n.hasExtraVacation())
        assertFalse(n.hasSpecialBonus())
    }

    @Test
    fun `all benefit codes from codelist map to czech names`() {
        val expected = mapOf(
            "ubyt" to "Ubytování",
            "predsk" to "Předškolní zařízení",
            "natur" to "Naturální výhody",
            "jizdne" to "Jízdní výhody",
            "jine" to "Jiné výhody",
            "strav" to "Podnikové stravování",
            "dovol" to "Dovolená navíc",
            "premie" to "Zvláštní prémie",
            "mimo" to "Mimo okres bydliště",
            "zahr" to "V zahraničí",
        )
        assertEquals(expected.size, Benefit.entries.size)
        Benefit.entries.forEach { v ->
            assertEquals("kód ${v.mpsvKod}", expected[v.mpsvKod], v.czechName)
        }
    }
}