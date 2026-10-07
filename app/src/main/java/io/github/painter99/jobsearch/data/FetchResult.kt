package io.github.painter99.jobsearch.data

/**
 * Outcome of a single source fetch (WSW M1.6b-3 pattern, generic pro jobsearch).
 *
 * Nahrazuje tichý null-on-failure kontrakt: volající vidí PROČ zdroj selhal
 * (HTTP kód, síť, parse) — diagnostic místo dohadů. ChmuDataSource vzor
 * z WSW Olomouc; zde generický, protože zdrojů bude více (MPSV, ARES, prace.cz).
 */
sealed interface FetchResult<out T> {

    /** Fetch + parse se podařil; [data] jsou hotový doménový výstup. */
    data class Success<T>(val data: T) : FetchResult<T>

    /** Server odpověděl ne-2xx kódem. */
    data class HttpError(val code: Int) : FetchResult<Nothing>

    /** Request se nepodařil dokončit (IO/DNS/timeout). */
    data class NetworkError(val message: String) : FetchResult<Nothing>

    /** Server odpověděl 2xx, ale payload nebyl použitelný. */
    data class ParseError(val detail: String) : FetchResult<Nothing>
}