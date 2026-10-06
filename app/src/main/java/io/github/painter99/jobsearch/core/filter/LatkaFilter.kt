package io.github.painter99.jobsearch.core.filter

import io.github.painter99.jobsearch.core.model.Nabidka
import io.github.painter99.jobsearch.core.model.Smennost

/**
 * Uživatelská latka — tvrdé filtry (per-user konfigurace, výchozí = Pavlův profil).
 */
data class Latka(
    val minMzdaKc: Int,
    val jenJednosmenna: Boolean,
)

/**
 * Tvrdý filtr: nabídka vyhovuje latce (směna + mzda).
 *
 * Oracle: Python prototyp filters.py — mzda_od >= minimum (neuvedeno → False),
 * jednosměnná směnnost (neuvedeno → False, pokud je filtr zapnutý).
 */
fun Nabidka.vyhovujeLatce(latka: Latka): Boolean {
    val smennaOk = !latka.jenJednosmenna || smennost == Smennost.JEDNOSMENNA
    val mzdaOk = (mzdaOd ?: 0) >= latka.minMzdaKc && mzdaOd != null
    return smennaOk && mzdaOk
}