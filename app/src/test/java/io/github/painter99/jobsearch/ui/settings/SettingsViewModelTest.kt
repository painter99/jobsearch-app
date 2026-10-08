package io.github.painter99.jobsearch.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.painter99.jobsearch.data.storage.AiSettings
import io.github.painter99.jobsearch.data.storage.AiSettingsStore
import io.github.painter99.jobsearch.data.storage.ApiKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * T5: SettingsViewModel — uložení klíče/modelů/souhlasu, smazání klíče,
 * hasKey indikace bez zobrazení klíče. Reálné DataStore na temp souboru
 * (lekce run #10 — final třídy se nedají fakovat).
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var aiSettingsStore: AiSettingsStore
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val dir = Files.createTempDirectory("settings-vm-test").toFile()
        val file = File(dir, "test.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        apiKeyStore = ApiKeyStore(dataStore)
        aiSettingsStore = AiSettingsStore(dataStore, apiKeyStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(apiKeyStore, aiSettingsStore)

    private suspend fun TestScope.awaitLoaded(vm: SettingsViewModel): SettingsUiState {
        val deadline = System.nanoTime() + 10_000_000_000
        while (System.nanoTime() < deadline) {
            advanceUntilIdle()
            if (!vm.state.value.loading) return vm.state.value
            Thread.sleep(20)
        }
        return vm.state.value
    }

    @Test
    fun `init - bez klíče hasKey false, AI modely na defaultu`() = runTest(dispatcher) {
        val vm = viewModel()
        val state = awaitLoaded(vm)
        assertFalse(state.hasKey)
        assertEquals(AiSettings.DEFAULT_MODEL, state.rankerModel)
        assertFalse(state.tosConsent)
    }

    @Test
    fun `save - uloží klíč, modely i souhlas; input se vyčistí`() = runTest(dispatcher) {
        val vm = viewModel()
        awaitLoaded(vm)
        vm.onApiKeyInput("sk-or-v1-abc")
        vm.onRankerModel("google/gemini-2.0-flash-001")
        vm.onSummarizerModel("openai/gpt-4o-mini")
        vm.onTosConsent(true)
        vm.save()
        val state = awaitSettled(vm)
        assertTrue(state.hasKey)
        assertEquals("", state.apiKeyInput)
        assertEquals("google/gemini-2.0-flash-001", state.rankerModel)
        assertTrue(state.tosConsent)
        assertTrue(state.message != null)
    }

    @Test
    fun `save - prázdný input klíče nesmaže uložený klíč`() = runTest(dispatcher) {
        apiKeyStore.set("sk-or-v1-existing")
        val vm = viewModel()
        awaitLoaded(vm)
        vm.onTosConsent(true)
        vm.save()
        val state = awaitSettled(vm)
        assertTrue(state.hasKey)
    }

    @Test
    fun `clearKey - smaže klíč, hasKey false, AI vypnuté`() = runTest(dispatcher) {
        apiKeyStore.set("sk-or-v1-existing")
        val vm = viewModel()
        awaitLoaded(vm)
        vm.clearKey()
        val state = awaitSettled(vm)
        assertFalse(state.hasKey)
        assertEquals("", state.apiKeyInput)
    }

    private suspend fun TestScope.awaitSettled(vm: SettingsViewModel): SettingsUiState {
        val deadline = System.nanoTime() + 10_000_000_000
        while (System.nanoTime() < deadline) {
            advanceUntilIdle()
            val s = vm.state.value
            if (!s.loading && !s.saving) return s
            Thread.sleep(20)
        }
        return vm.state.value
    }
}