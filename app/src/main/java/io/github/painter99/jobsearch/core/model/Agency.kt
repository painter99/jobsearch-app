package io.github.painter99.jobsearch.core.model

/**
 * Personální agentura z MPSV oficiálního seznamu „Agentury práce"
 * (agentury-prace.json, 1 912 záznamů s platným povolením dle §435/2004 Sb.,
 * update 1x denně; ověřeno živě 6. 10. 2026).
 *
 * GDPR whitelist: ukládáme JEN ico, name a permits (druhyPraci + platnost).
 * odpovednyZastupce a kontaktniOsoby jsou osobní údaje — do modelu se vůbec
 * nemapují (vynuceno absencí pole, ne disciplínou; stejné pravidlo jako JobOffer).
 */
data class Agency(
    val ico: String,
    val name: String,
    val permits: List<AgencyPermit>,
) {
    /**
     * Platné povolení k zadanému datu (ISO yyyy-MM-dd — lexikografické
     * porovnání ISO řetězců stačí). null platnostDo = bez omezení.
     */
    fun hasValidPermitOn(dateIso: String): Boolean =
        permits.any { p ->
            (p.validFrom == null || p.validFrom <= dateIso) &&
                (p.validTo == null || p.validTo >= dateIso)
        }
}

/** Jedno povolení agentury (druhyPraci.cs + platnost; MPSV struktura ověřena 6. 10.). */
data class AgencyPermit(
    val workTypes: String?,
    val validFrom: String?,
    val validTo: String?,
)