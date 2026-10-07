package io.github.painter99.jobsearch.core.model

/**
 * Obec z MPSV číselníku (obce.json, 6 258 obcí, ověřeno živě 7. 10. 2026) —
 * zdroj autocomplete lokalit a join přes RÚIAN kódy pro [WorkLocation].
 */
data class Municipality(
    val id: String,           // "Obec/503657" (RÚIAN)
    val code: String,        // "503657"
    val name: String,        // "Lutín"
    val districtId: String?, // "Okres/3805" (LAU)
)