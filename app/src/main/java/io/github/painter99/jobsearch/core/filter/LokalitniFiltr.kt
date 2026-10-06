package io.github.painter99.jobsearch.core.filter

import io.github.painter99.jobsearch.core.model.MistoVykonu
import io.github.painter99.jobsearch.core.model.Nabidka

/**
 * Matching lokality — strategie z top 3 robustness rešerše (6. 10. 2026):
 * primárně RÚIAN kódy (pracoviste → obec), pak okresy, adresaText naposled.
 */
object LokalitniFiltr {

    const val PLACEHOLDER_OKRES = "Okres/9999"

    /**
     * @param profilObci RÚIAN id obcí ("Obec/<kod>")
     * @param profilOkresu LAU id okresů ("Okres/<kod>")
     * @param profilText volné texty lokalit (malými písmeny; poslední fallback)
     */
    fun vyhovuje(
        nabidka: Nabidka,
        profilObci: Set<String>,
        profilOkresu: Set<String>,
        profilText: Set<String> = emptySet(),
    ): Boolean = false // RED stub
}