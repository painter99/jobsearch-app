package io.github.painter99.jobsearch

import io.github.painter99.jobsearch.core.filter.LocationFilter
import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3: Matching lokality — strategie z top 3 robustness rešerše:
 * pracoviste (RÚIAN) → obec.id → districts → addressText (poslední);
 * celaCR vyhovuje vždy; Okres/9999 = placeholder (nikdy nematchuje).
 */
class LocationFilterTest {

    private val olomouc = setOf("Obec/554782")
    private val olomoucDistrict = setOf("Okres/3806")

    private fun offer(location: WorkLocation) = JobOffer(
        portalId = 1,
        referenceNumber = "X",
        profession = "Test",
        shiftPattern = ShiftPattern.SINGLE_SHIFT,
        salaryFrom = 45_000,
        salaryTo = null,
        hoursPerWeek = null,
        employer = null,
        location = location,
        benefits = emptyList(),
        url = null,
        agencyConsent = null,
        userConsent = null,
    )

    @Test
    fun `typ obec - municipalityId v profilu vyhovuje`() {
        val location = WorkLocation(WorkLocation.LocationType.MUNICIPALITY, "Obec/554782", emptyList(), null, emptyList())
        assertTrue(LocationFilter.matches(offer(location), olomouc, olomoucDistrict))
    }

    @Test
    fun `type municipality - other municipality rejected`() {
        val location = WorkLocation(WorkLocation.LocationType.MUNICIPALITY, "Obec/537683", emptyList(), null, emptyList())
        assertFalse(LocationFilter.matches(offer(location), olomouc, olomoucDistrict))
    }

    @Test
    fun `type worksite address - worksiteMunicipalityIds match`() {
        val location = WorkLocation(
            WorkLocation.LocationType.WORKSITE_ADDRESS, null, emptyList(), null,
            listOf("Obec/537683", "Obec/554782"),
        )
        assertTrue(LocationFilter.matches(offer(location), olomouc, olomoucDistrict))
    }

    @Test
    fun `type district - district in profile matches`() {
        val location = WorkLocation(WorkLocation.LocationType.DISTRICT, null, listOf("Okres/3806", "Okres/9999"), null, emptyList())
        assertTrue(LocationFilter.matches(offer(location), olomouc, olomoucDistrict))
    }

    @Test
    fun `type district - Okres 9999 placeholder never matches`() {
        val location = WorkLocation(WorkLocation.LocationType.DISTRICT, null, listOf("Okres/9999"), null, emptyList())
        assertFalse(LocationFilter.matches(offer(location), olomouc, olomoucDistrict))
    }

    @Test
    fun `type wholeCR always matches`() {
        val location = WorkLocation(WorkLocation.LocationType.WHOLE_CR, null, emptyList(), null, emptyList())
        assertTrue(LocationFilter.matches(offer(location), olomouc, olomoucDistrict))
        assertTrue(LocationFilter.matches(offer(location), emptySet(), emptySet()))
    }

    @Test
    fun `typ adrvolna - addressText jako poslední fallback`() {
        val location = WorkLocation(WorkLocation.LocationType.FREE_ADDRESS, "Obec/567027", emptyList(), "Most, Litvínov", emptyList())
        // municipalityId není v profilu, ale addressText obsahuje hledaný text
        val profileText = setOf("most")
        assertTrue(LocationFilter.matches(offer(location), emptySet(), emptySet(), profileText))
    }

    @Test
    fun `type unspecified rejected (nothing to match)`() {
        val location = WorkLocation(WorkLocation.LocationType.UNSPECIFIED, null, emptyList(), null, emptyList())
        assertFalse(LocationFilter.matches(offer(location), olomouc, olomoucDistrict))
    }
}