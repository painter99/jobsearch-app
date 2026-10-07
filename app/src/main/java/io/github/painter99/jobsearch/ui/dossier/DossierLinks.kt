package io.github.painter99.jobsearch.ui.dossier

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Vyhledávání volných míst na portálu ÚP (D5 fallback).
 *
 * Ověřeno živě 7. 10. 2026: oficiální stránka „Volná místa - hledání" ÚP
 * (mpsv.gov.cz/up-cz/volna-mista-v-cr) — Nuxt SPA, detail nabídky přes
 * hash-routu #/volna-mista-detail/&lt;id&gt;. Veřejné GET URL vyhledávání
 * S PARAMETRY ověřeno NENÍ (vyhledávání běží přes interní API gateway).
 *
 * Proto fallback v1 = otevřít tuto ověřenou stránku; uživatel dohledá
 * nabídku přes referenční číslo (zobrazuje se v detailu dossieru).
 * Parametrizované URL = otevřená položka D5 (případně doplnit později).
 */
const val UP_SEARCH_URL = "https://mpsv.gov.cz/up-cz/volna-mista-v-cr"

/** Deep link na nabídku prace.cz (vzor /nabidka/&lt;uuid&gt; ověřen M1.4). */
fun praceczOfferUrl(uuid: String): String = "https://www.prace.cz/nabidka/$uuid"

/** Otevře URL v prohlížeči; neznámý handler tiše ignoruje (nikdy nespadnout). */
fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // žádná aktivita pro URL — uživatel si link zkopíruje z referenčního čísla
    }
}