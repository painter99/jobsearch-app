package io.github.painter99.jobsearch.core.model

/**
 * Obchodní subjekt z ARES detail (GET /ekonomicke-subjekty/{ico}?detail=2).
 *
 * Veřejná rejstříková data — žádné osobní údaje v rozsahu, který ukládáme.
 * Pole municipality/districtName mapujeme ze sidlo.nazevObce / sidlo.nazevOkresu
 * (ověřeno na živé odpovědi 7. 10. 2026 — klíč `obec` v sidlo je null,
 * název obce nese `nazevObce`).
 */
data class Company(
    val ico: String,
    val businessName: String,
    val municipality: String?,
    val districtName: String?,
    val foundedOn: String?,
    val czNace: List<String>,
    val legalForm: String?,
)