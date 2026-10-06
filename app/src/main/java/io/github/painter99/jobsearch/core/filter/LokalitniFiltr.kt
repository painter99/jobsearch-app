package io.github.painter99.jobsearch.core.filter

import io.github.painter99.jobsearch.core.model.MistoVykonu
import io.github.painter99.jobsearch.core.model.Nabidka

/**
 * Matching lokality — strategie z top 3 robustness rešerše (6. 10. 2026):
 * primárně RÚIAN kódy (pracoviste → obec), pak okresy, adresaText naposled.
 *
 * `Okres/9999` = placeholder v datech (objevuje se u typů okres/celaCR),
 * nikdy nematchuje profil.
 */
object LokalitniFiltr {

    const val PLACEHOLDER_OKRES = "Okres/9999"

    fun vyhovuje(
        nabidka: Nabidka,
        profilObci: Set<String>,
        profilOkresu: Set<String>,
        profilText: Set<String> = emptySet(),
    ): Boolean {
        val misto = nabidka.misto

        // celaCR = nabídka platí celoplošně → vyhovuje vždy
        if (misto.typ == MistoVykonu.TypMistaVykonu.CELA_CR) return true

        // 1) RÚIAN kódy obcí: pracoviste (adrprov) i obec
        if (misto.pracovisteObecIds.any { it in profilObci }) return true
        if (misto.obecId != null && misto.obecId in profilObci) return true

        // 2) Okresy (LAU) — placeholder 9999 nikdy nematchuje
        if (misto.okresy.any { it != PLACEHOLDER_OKRES && it in profilOkresu }) return true

        // 3) Volný text — poslední fallback (adresaText)
        if (profilText.isNotEmpty() && misto.adresaText != null) {
            val blob = misto.adresaText.lowercase()
            if (profilText.any { it in blob }) return true
        }

        return false
    }
}