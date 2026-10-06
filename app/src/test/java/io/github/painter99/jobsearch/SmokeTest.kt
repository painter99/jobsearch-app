package io.github.painter99.jobsearch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M0.1 smoke test — skeleton sanity (WSW M1.1 pattern).
 */
class SmokeTest {

    @Test
    fun packageNameIsStable() {
        assertEquals("io.github.painter99.jobsearch", BuildConfigHolder.PACKAGE)
    }

    @Test
    fun versionIsPreRelease() {
        // SemVer 0.x policy (PRD §15): v1 starts at 0.1.0
        assertEquals("0.1.0", BuildConfigHolder.VERSION_NAME)
    }

    object BuildConfigHolder {
        const val PACKAGE = "io.github.painter99.jobsearch"
        const val VERSION_NAME = "0.1.0"
    }
}