package io.github.painter99.jobsearch.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * T5: AiSettingsStore — modely per funkce (D7), souhlas s ToS (§5.2),
 * agregace klíče z ApiKeyStore (klíč drží jen ApiKeyStore).
 * Vzor DataStoreStoresTest: reálný DataStore na temp souboru.
 */
class AiSettingsStoreTest {

    private fun newTempFile(): File {
        val dir = Files.createTempDirectory("ai-settings-test").toFile()
        return File(dir, "test.preferences_pb")
    }

    private fun newDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `defaults - bez klíče a souhlasu je AI nedostupné (AC4)`() = runTest {
        val file = newTempFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = AiSettingsStore(newDataStore(file, scope), ApiKeyStore(newDataStore(file, scope)))
        val settings = store.load()
        assertFalse(settings.aiAvailable)
        assertEquals(AiSettings.DEFAULT_MODEL, settings.rankerModel)
        assertEquals(AiSettings.DEFAULT_MODEL, settings.summarizerModel)
        scope.cancel()
    }

    @Test
    fun `klíč bez souhlasu - AI stále nedostupné (souhlas ToS chybí)`() = runTest {
        val file = newTempFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = newDataStore(file, scope)
        val apiKeyStore = ApiKeyStore(dataStore)
        apiKeyStore.set("sk-or-v1-test")
        val store = AiSettingsStore(dataStore, apiKeyStore)
        val settings = store.load()
        assertFalse(settings.aiAvailable)
        assertTrue(settings.tosConsent.not())
        scope.cancel()
    }

    @Test
    fun `klíč i souhlas - AI dostupné, modely per funkce perzistují`() = runTest {
        val file = newTempFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = newDataStore(file, scope)
        val apiKeyStore = ApiKeyStore(dataStore)
        apiKeyStore.set("sk-or-v1-test")
        val store = AiSettingsStore(dataStore, apiKeyStore)
        store.saveModels(rankerModel = "a/b", summarizerModel = "c/d")
        store.setTosConsent(true)

        val settings = store.load()
        assertTrue(settings.aiAvailable)
        assertEquals("a/b", settings.rankerModel)
        assertEquals("c/d", settings.summarizerModel)
        assertTrue(settings.tosConsent)
        scope.cancel()
    }

    @Test
    fun `clear klíče - AI se vypne, modely zůstávají`() = runTest {
        val file = newTempFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = newDataStore(file, scope)
        val apiKeyStore = ApiKeyStore(dataStore)
        apiKeyStore.set("sk-or-v1-test")
        val store = AiSettingsStore(dataStore, apiKeyStore)
        store.setTosConsent(true)
        store.saveModels("a/b", "c/d")
        apiKeyStore.clear()

        val settings = store.load()
        assertFalse(settings.aiAvailable)
        assertEquals("a/b", settings.rankerModel)
        assertTrue(settings.tosConsent)
        scope.cancel()
    }
}