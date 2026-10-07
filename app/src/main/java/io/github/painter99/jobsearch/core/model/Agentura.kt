package io.github.painter99.jobsearch.core.model

/**
 * Personální agentura z MPSV oficiálního seznamu „Agentury práce"
 * (agentury-prace.json, 1 912 záznamů s platným povolením dle §435/2004 Sb.,
 * update 1x denně; ověřeno živě 6. 10. 2026).
 *
 * GDPR whitelist: ukládáme JEN ico, nazev a povolení (druhyPraci + platnost).
 * odpovednyZastupce a kontaktniOsoby jsou osobní údaje — do modelu se vůbec
 * nemapují (vynuceno absencí pole, ne disciplínou; stejné pravidlo jako Nabidka).
 */
data class Agentura(
    val ico: String,
    val nazev: String,
    val povoleni: List<AgenturaPovoleni>,
) {
    /**
     * Platné povolení k zadanému datu (ISO yyyy-MM-dd — lexikografické
     * porovnání ISO řetězců stačí). null platnostDo = bez omezení.
     */
    fun maPlatnePovoleniK(dneIso: String): Boolean =
        povoleni.any { p ->
            (p.platnostOd == null || p.platnostOd <= dneIso) &&
                (p.platnostDo == null || p.platnostDo >= dneIso)
        }
}

/** Jedno povolení agentury (druhyPraci.cs + platnost; MPSV struktura ověřena 6. 10.). */
data class AgenturaPovoleni(
    val druhyPraci: String?,
    val platnostOd: String?,
    val platnostDo: String?,
)