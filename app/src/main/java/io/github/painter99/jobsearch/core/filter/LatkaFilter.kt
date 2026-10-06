package io.github.painter99.jobsearch.core.filter

import io.github.painter99.jobsearch.core.model.Nabidka

/**
 * Uživatelská latka — tvrdé filtry (per-user konfigurace, výchozí = Pavlův profil).
 */
data class Latka(
    val minMzdaKc: Int,
    val jenJednosmenna: Boolean,
)

/**
 * Tvrdý filtr: nabídka vyhovuje latce (směna + mzda).
 */
fun Nabidka.vyhovujeLatce(latka: Latka): Boolean = false // RED stub