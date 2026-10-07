package io.github.painter99.jobsearch.ui.dossier

import io.github.painter99.jobsearch.core.filter.UserCriteria
import io.github.painter99.jobsearch.core.model.Benefit
import io.github.painter99.jobsearch.core.model.Employer
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.data.storage.LocationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Šablona checklistu latky (US4, vlna B): auto-fill dle strukturovaných
 * polí MPSV („Dovolená navíc", „Zvláštní prémie") a výsledku filtrů
 * (latka + lokalita). Ruční položky se neauto-fillují.
 */
class ChecklistTemplateTest {

    private fun offer(
        salary: Int? = 45_000,
        shift: ShiftPattern? = ShiftPattern.SINGLE_SHIFT,
        benefits: List<Benefit> = emptyList(),
        municipalityId: String? = "Obec/503657",
        type: WorkLocation.LocationType? = WorkLocation.LocationType.MUNICIPALITY,
    ) = JobOffer(
        portalId = 1L,
        referenceNumber = "ref-1",
        profession = "Profese",
        shiftPattern = shift,
        salaryFrom = salary,
        salaryTo = null,
        hoursPerWeek = null,
        employer = Employer(ico = "12345678", name = "Firma"),
        location = WorkLocation(
            type = type,
            municipalityId = municipalityId,
            districts = emptyList(),
            addressText = null,
            worksiteMunicipalityIds = emptyList(),
        ),
        benefits = benefits,
        url = null,
        agencyConsent = null,
        userConsent = null,
    )

    private val criteria = UserCriteria(minSalaryKc = 40_000, singleShiftOnly = true)
    private val profile = LocationProfile(municipalityIds = setOf("Obec/503657"))

    private fun autoFor(key: String, offer: JobOffer): Boolean =
        ChecklistTemplate.items.first { it.key == key }
            .autoFill?.invoke(offer, criteria, profile) == true

    @Test
    fun `auto-fill - latka splněna (mzda + jednosměnná)`() {
        assertTrue(autoFor("salary", offer()))
        assertTrue(autoFor("single_shift", offer()))
    }

    @Test
    fun `auto-fill - latka nesplněna (nízká mzda)`() {
        assertFalse(autoFor("salary", offer(salary = 30_000)))
    }

    @Test
    fun `auto-fill - dovolená navíc a prémie dle výhod nabídky`() {
        val withBenefits = offer(benefits = listOf(Benefit.EXTRA_VACATION, Benefit.CANTEEN))
        assertTrue(autoFor("extra_vacation", withBenefits))
        assertFalse(autoFor("special_bonus", withBenefits))
    }

    @Test
    fun `auto-fill - lokalita vyhovuje dle profilu`() {
        assertTrue(autoFor("location", offer()))
        assertFalse(autoFor("location", offer(municipalityId = "Obec/999999")))
    }

    @Test
    fun `ruční položky nemají auto-fill`() {
        val manual = ChecklistTemplate.items.filter { it.autoFill == null }.map { it.key }
        assertEquals(setOf("reviews", "registry", "contact"), manual.toSet())
    }

    @Test
    fun `šablona obsahuje očekávané klíče`() {
        assertEquals(
            listOf(
                "salary", "single_shift", "extra_vacation", "special_bonus",
                "location", "reviews", "registry", "contact",
            ),
            ChecklistTemplate.items.map { it.key },
        )
    }
}