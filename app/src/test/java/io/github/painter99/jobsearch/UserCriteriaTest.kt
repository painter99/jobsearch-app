package io.github.painter99.jobsearch

import io.github.painter99.jobsearch.core.filter.UserCriteria
import io.github.painter99.jobsearch.core.filter.matchesCriteria
import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2: Pavlova latka (UserCriteria) — tvrdé filtry (směna + mzda).
 * Oracle: Python prototyp filters.py (mzda_od >= minimum, neuvedeno → NEvyhovuje).
 */
class UserCriteriaTest {

    private val criteria = UserCriteria(minSalaryKc = 40_000, singleShiftOnly = true)

    private fun offer(
        shiftPattern: ShiftPattern?,
        salaryFrom: Int?,
    ) = JobOffer(
        portalId = 1,
        referenceNumber = "X",
        profession = "Test",
        shiftPattern = shiftPattern,
        salaryFrom = salaryFrom,
        salaryTo = null,
        hoursPerWeek = null,
        employer = null,
        location = WorkLocation.NONE,
        benefits = emptyList(),
        url = null,
        agencyConsent = null,
        userConsent = null,
    )

    @Test
    fun `salary boundary - 39999 rejected, 40000 accepted`() {
        assertFalse(offer(ShiftPattern.SINGLE_SHIFT, 39_999).matchesCriteria(criteria))
        assertTrue(offer(ShiftPattern.SINGLE_SHIFT, 40_000).matchesCriteria(criteria))
    }

    @Test
    fun `missing salary rejected (as in prototype)`() {
        assertFalse(offer(ShiftPattern.SINGLE_SHIFT, null).matchesCriteria(criteria))
    }

    @Test
    fun `jiná směnnost NEvyhovuje při singleShiftOnly`() {
        ShiftPattern.entries.filter { it != ShiftPattern.SINGLE_SHIFT }.forEach { s ->
            assertFalse("směnnost $s má vyřadit", offer(s, 50_000).matchesCriteria(criteria))
        }
    }

    @Test
    fun `missing shift pattern rejected`() {
        assertFalse(offer(null, 50_000).matchesCriteria(criteria))
    }

    @Test
    fun `shift filter off passes three-shift offer`() {
        val noShiftFilter = UserCriteria(minSalaryKc = 40_000, singleShiftOnly = false)
        assertTrue(offer(ShiftPattern.THREE_SHIFT, 50_000).matchesCriteria(noShiftFilter))
        assertTrue(offer(null, 50_000).matchesCriteria(noShiftFilter))
    }
}