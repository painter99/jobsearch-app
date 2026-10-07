package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.mpsv.IncrementParser
import io.github.painter99.jobsearch.data.mpsv.MpsvOffersClient
import io.github.painter99.jobsearch.data.mpsv.OfferStore
import java.time.LocalDate

/**
 * Denní sync MPSV nabídek (pipeline, M1.4): přírůstek → [OfferStore].
 *
 * Catch-up po offline dnech: [syncDaily] projde dny od poslední kotvy
 * (poslední úspěšný den) do včera. Chybějící přírůstek (HTTP 404) je
 * běžný stav MPSV publikace — den se přeskočí a kotva se NEposouvá
 * (další pokus příště). Jiné chyby (síť, 5xx) ukončí catch-up dřív,
 * ať uživatel vidí konzistentní stav.
 *
 * Bootstrap (187 MB full dump) se stahuje odděleně přes
 * [MpsvOffersClient.downloadBootstrap] + [OfferStore.importBootstrap]
 * (Wi-Fi potvrzení řeší UI/WorkManager, ne tato vrstva).
 */
class OfferSyncEngine(
    private val offersClient: MpsvOffersClient,
    private val store: OfferStore,
    private val anchorStore: SyncAnchorStore,
) {

    /** Výsledek jednoho dne syncu. */
    data class DayResult(
        val date: LocalDate,
        val applied: Int,        // počet aplikovaných záznamů
        val skipped: Boolean,    // true = přírůstek neexistoval (404)
    )

    /**
     * Synchronizuje všechny dny od poslední kotvy (včetně) do [todayExclusive].
     * Vrací výsledky per den. Kotva se posouvá jen za úspěšně aplikované
     * (nebo přeskočené) dny.
     */
    suspend fun syncDaily(todayExclusive: LocalDate): List<DayResult> {
        var anchor = anchorStore.load()
        val results = mutableListOf<DayResult>()
        var date = anchor
        while (date.isBefore(todayExclusive)) {
            when (val fetchResult = offersClient.fetchIncrement(date)) {
                is FetchResult.Success -> {
                    val records = IncrementParser().parseText(fetchResult.data)
                    val applied = store.applyIncrement(records)
                    anchor = date.plusDays(1)
                    anchorStore.save(anchor)
                    results.add(DayResult(date, applied, skipped = false))
                }
                is FetchResult.HttpError -> {
                    if (fetchResult.code == 404) {
                        // den nevyšel — přeskočit, kotvu NEposouvat
                        results.add(DayResult(date, 0, skipped = true))
                    } else {
                        // server problém — ukončit catch-up (zítřek to zkusí znovu)
                        return results
                    }
                }
                else -> {
                    // síť/parse chyba — ukončit catch-up
                    return results
                }
            }
            date = date.plusDays(1)
        }
        return results
    }
}

/**
 * Perzistence sync kotvy (poslední den, jehož přírůstek byl aplikován + 1).
 * Implementace M1.5 (DataStore); tady rozhraní + jednoduchá in-memory
 * implementace pro testy.
 */
interface SyncAnchorStore {
    fun load(): LocalDate
    fun save(date: LocalDate)
}

class InMemorySyncAnchorStore(private var anchor: LocalDate) : SyncAnchorStore {
    override fun load(): LocalDate = anchor
    override fun save(date: LocalDate) { anchor = date }
}