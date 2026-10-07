package io.github.painter99.jobsearch.core.filter

import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern

/**
 * Uživatelská latka (UserCriteria) — tvrdé filtry (per-user konfigurace, výchozí = Pavlův profil).
 */
data class UserCriteria(
    val minSalaryKc: Int,
    val singleShiftOnly: Boolean,
)

/**
 * Tvrdý filtr: nabídka vyhovuje latce (směna + mzda).
 *
 * Oracle: Python prototyp filters.py — mzda_od >= minimum (neuvedeno → False),
 * jednosměnná směnnost (neuvedeno → False, pokud je filtr zapnutý).
 */
fun JobOffer.matchesCriteria(criteria: UserCriteria): Boolean {
    val shiftOk = !criteria.singleShiftOnly || shiftPattern == ShiftPattern.SINGLE_SHIFT
    val salaryOk = (salaryFrom ?: 0) >= criteria.minSalaryKc && salaryFrom != null
    return shiftOk && salaryOk
}