package io.github.painter99.jobsearch.core.filter

import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.WorkLocation

/**
 * Matching lokality — strategie z top 3 robustness rešerše (6. 10. 2026):
 * primárně RÚIAN kódy (pracoviste → obec), pak okresy, adresaText naposled.
 *
 * `Okres/9999` = placeholder v datech (objevuje se u typů okres/celaCR),
 * nikdy nematchuje profil.
 */
object LocationFilter {

    const val PLACEHOLDER_DISTRICT = "Okres/9999"

    fun matches(
        offer: JobOffer,
        profileMunicipalities: Set<String>,
        profileDistricts: Set<String>,
        profileText: Set<String> = emptySet(),
    ): Boolean {
        val location = offer.location

        // celaCR = nabídka platí celoplošně → vyhovuje vždy
        if (location.type == WorkLocation.LocationType.WHOLE_CR) return true

        // 1) RÚIAN kódy obcí: pracoviste (adrprov) i obec
        if (location.worksiteMunicipalityIds.any { it in profileMunicipalities }) return true
        if (location.municipalityId != null && location.municipalityId in profileMunicipalities) return true

        // 2) Okresy (LAU) — placeholder 9999 nikdy nematchuje
        if (location.districts.any { it != PLACEHOLDER_DISTRICT && it in profileDistricts }) return true

        // 3) Volný text — poslední fallback (adresaText)
        if (profileText.isNotEmpty() && location.addressText != null) {
            val blob = location.addressText.lowercase()
            if (profileText.any { it in blob }) return true
        }

        return false
    }
}