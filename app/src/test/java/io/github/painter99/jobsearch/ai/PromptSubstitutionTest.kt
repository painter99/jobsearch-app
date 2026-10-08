package io.github.painter99.jobsearch.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2: substituce placeholderrů v promptech („prompt = data, ne kód").
 * Substituce = čistá funkce → JVM testy bez Robolectric.
 */
class PromptSubstitutionTest {

    @Test
    fun `substitute - nahradí všechny placeholdery`() {
        val raw = "Profese: {{profession}}, obec: {{municipality}}."
        val result = PromptSubstitution.substitute(
            raw,
            mapOf("profession" to "lakýrník", "municipality" to "Olomouc"),
        )
        assertEquals("Profese: lakýrník, obec: Olomouc.", result)
    }

    @Test
    fun `substitute - nezsubstituovaný placeholder hází IllegalStateException`() {
        val raw = "Profese: {{profession}}, {{missing}}."
        try {
            PromptSubstitution.substitute(raw, mapOf("profession" to "lakýrník"))
            org.junit.Assert.fail("čekal jsem výjimku")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("{{missing}}"))
        }
    }

    @Test
    fun `substitute - hodnota obsahující placeholdery se dál neexpanduje`() {
        // hodnota parametru se NEinterpretuje jako šablona (žádný rekurzivní expand)
        val result = PromptSubstitution.substitute(
            "X: {{x}}",
            mapOf("x" to "literal {{y}}"),
        )
        assertEquals("X: literal {{y}}", result)
    }
}