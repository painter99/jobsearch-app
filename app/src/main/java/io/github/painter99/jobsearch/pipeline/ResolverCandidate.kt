package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.Company

/**
 * Kandidát na koncového zaměstnavatele (pilíř č. 1B, US2).
 *
 * N7: kandidát NIKDY není fakt — vždy jde o návrh se skóre a zdůvodněním;
 * potvrzuje uživatel („assistant never clicks for you").
 *
 * Oracle: Python prototyp `agency_resolver.py` (score + duvody, 6. 10. 2026).
 */
data class ResolverCandidate(
    val company: Company,
    /** Heuristické skóre (deterministické; AI řazení až M1.7). Vyšší = lepší. */
    val score: Double,
    /** Zdůvodnění pro UI (CZ stringy; každý důvod = jeden řádek). */
    val reasons: List<String>,
)

/**
 * Stopy z inzerátu pro vyhledání koncové firmy.
 *
 * Vstup resolveru — vše volitelné kromě [naceCodes] (bez NACE by vyhledávání
 * vrátilo příliš široký seznam). Hodnoty dle ověřeného PoC 6. 10. 2026:
 * NACE 28130 (výroba čerpadel) + Olomouc → ISH PUMPS / SIGMA 1868 / ZAHAS.
 */
data class EmployerHints(
    /** Komprimované CZ-NACE kódy (28.13 → "28130"). */
    val naceCodes: List<String>,
    /** Název obce ze stopy inzerátu (např. „Olomouc"); null = neznámá. */
    val municipalityName: String? = null,
    /** Název okresu (širší pásmo); null = neznámý. */
    val districtName: String? = null,
    /** Inzerát naznačuje rodinnou firmu („rodinná firma", „familiární podnik"). */
    val familyBusiness: Boolean = false,
    /** „zavedená firma" — preferovat subjekty založené do tohoto roku. */
    val foundedUntilYear: Int? = null,
)