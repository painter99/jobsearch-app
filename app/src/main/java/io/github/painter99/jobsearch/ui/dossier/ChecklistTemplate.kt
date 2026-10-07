package io.github.painter99.jobsearch.ui.dossier

import io.github.painter99.jobsearch.core.filter.LocationFilter
import io.github.painter99.jobsearch.core.filter.UserCriteria
import io.github.painter99.jobsearch.core.filter.matchesCriteria
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.data.storage.LocationProfile

/**
 * Šablona checklistu latky u dossieru (US4, M1.6 vlna B).
 *
 * Položky s [autoFill] se při PRVNÍM otevření dossieru předvyplní ze
 * strukturovaných polí MPSV („Dovolená navíc", „Zvláštní prémie" — ověřeno
 * v top 3 rešerši 6. 10.) a z výsledku filtrů (latka, lokalita). Poté je
 * zdrojem pravdy databáze — uživatel může auto položku odškrtnout a stav
 * se přepisuje jen jeho akcí. Položky bez [autoFill] = ruční práce uživatele.
 *
 * itemKey = EN identifikátor (PRD §9); label = UI string (CZ).
 */
data class ChecklistTemplateItem(
    val key: String,
    val label: String,
    val autoFill: ((offer: JobOffer, criteria: UserCriteria, profile: LocationProfile) -> Boolean)? = null,
)

object ChecklistTemplate {

    val items: List<ChecklistTemplateItem> = listOf(
        ChecklistTemplateItem("salary", "Mzda odpovídá látce") { offer, criteria, _ ->
            offer.matchesCriteria(criteria)
        },
        ChecklistTemplateItem("single_shift", "Jednosměnná směna") { offer, _, _ ->
            offer.shiftPattern == ShiftPattern.SINGLE_SHIFT
        },
        ChecklistTemplateItem("extra_vacation", "Dovolená navíc") { offer, _, _ ->
            offer.hasExtraVacation()
        },
        ChecklistTemplateItem("special_bonus", "Zvláštní prémie") { offer, _, _ ->
            offer.hasSpecialBonus()
        },
        ChecklistTemplateItem("location", "Lokalita vyhovuje (dojezd)") { offer, _, profile ->
            LocationFilter.matches(
                offer = offer,
                profileMunicipalities = profile.municipalityIds,
                profileDistricts = profile.districtIds,
            )
        },
        ChecklistTemplateItem("reviews", "Recenze zaměstnavatele prověřeny"),
        ChecklistTemplateItem("registry", "Rejstřík (ARES) prověřen"),
        ChecklistTemplateItem("contact", "Pracoviště a kontakt ověřeny"),
    )
}