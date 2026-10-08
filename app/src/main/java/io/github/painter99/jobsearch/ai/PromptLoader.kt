package io.github.painter99.jobsearch.ai

import android.content.res.AssetManager

/**
 * Načítání promptů (M1.7 T2): „prompt = data, ne kód".
 *
 * Prompty jsou verzované .md soubory v `assets/prompts/` — žádný prompt
 * text v Kotlinu. Interface kvůli testovatelnosti (lekce run #10 — final
 * třídy se nedají fakovat); produkční implementace = [AssetPromptLoader],
 * substituci placeholderrů dělá [PromptSubstitution] (čistá funkce — JVM
 * testy bez Robolectric).
 */
interface PromptLoader {

    /**
     * Načte prompt `prompts/[name]` a substituuje placeholdery `{{klic}}`
     * hodnotami z [params]. Nezsubstituovaný placeholder = chyba
     * (IllegalStateException) — radši spadnout v testu než poslat modelu
     * rozbitý prompt.
     */
    fun load(name: String, params: Map<String, String>): String
}

/** Substituce placeholderrů `{{klic}}` v raw promptu (bez Android závislostí). */
object PromptSubstitution {

    fun substitute(raw: String, params: Map<String, String>): String {
        var result = raw
        for ((key, value) in params) {
            result = result.replace("{{$key}}", value)
        }
        PLACEHOLDER.find(result)?.let {
            throw IllegalStateException(
                "Prompt obsahuje nezsubstituovaný placeholder ${it.value} " +
                    "(dostupné parametry: ${params.keys})",
            )
        }
        return result
    }

    val PLACEHOLDER = Regex("\\{\\{[a-zA-Z0-9_]+\\}\\}")
}

/** Produkční loader nad APK assets (AssetManager). */
class AssetPromptLoader(private val assets: AssetManager) : PromptLoader {

    override fun load(name: String, params: Map<String, String>): String {
        val raw = assets.open("$PROMPTS_DIR/$name").bufferedReader().use { it.readText() }
        return PromptSubstitution.substitute(raw, params)
    }

    companion object {
        const val PROMPTS_DIR = "prompts"
    }
}