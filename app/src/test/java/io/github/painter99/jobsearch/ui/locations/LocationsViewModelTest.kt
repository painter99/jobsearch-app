package io.github.painter99.jobsearch.ui.locations

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.painter99.jobsearch.data.mpsv.MpsvCiselnikyClient
import io.github.painter99.jobsearch.data.mpsv.MunicipalityRepository
import io.github.painter99.jobsearch.data.storage.ProfileStore
import java.nio.file.Files
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
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T2: LocationsViewModel — autocomplete (prefix dřív než obsah, case-
 * insensitive), multi-select toggle, uložení profilu, chybový stav číselníku.
 *
 * Reálné komponenty (lekce run #10 — final třídy se nedají fakovat):
 * [MunicipalityRepository] nad MockWebServerem s fixture `obce-sample.json`
 * (12 obcí), [ProfileStore] nad reálným temp DataStore (vzor ProfileStoreTest).
 *
 * Síť + DataStore běží na reálném Dispatchers.IO — [awaitSettled] polluje
 * se skutečným sleepem (advanceUntilIdle sám nepočká na IO).
 */
class LocationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var server: MockWebServer

    /** Scope každého temp DataStore — ručíme v tearDown, ať netěšíme vlákna. */
    private val dataStoreScopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
        dataStoreScopes.forEach { it.cancel() }
    }

    private fun profileStore(): ProfileStore {
        val file = Files.createTempDirectory("loc-test").toFile().resolve("t.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreScopes.add(scope)
        return ProfileStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    }

    private fun repository(): MunicipalityRepository =
        MunicipalityRepository(
            MpsvCiselnikyClient(
                client = OkHttpClient(),
                obceUrl = server.url("/od/soubory/ciselniky/obce.json").toString(),
            ),
        )

    /** Fixture číselníku (12 obcí) — jeden enqueue = jeden fetch. */
    private fun enqueueObce() {
        server.enqueue(
            MockResponse().setBody(
                javaClass.getResourceAsStream("/obce-sample.json")!!
                    .readBytes().toString(Charsets.UTF_8),
            ),
        )
    }

    private fun viewModel(store: ProfileStore = profileStore()): LocationsViewModel =
        LocationsViewModel(repository = repository(), profileStore = store)

    /** Síť/DataStore běží na reálném IO — polluje se sleepem (viz KDoc třídy). */
    private suspend fun TestScope.awaitSettled(vm: LocationsViewModel, timeoutMs: Long = 10_000) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            advanceUntilIdle()
            val state = vm.state.value
            if (!state.loading && !state.saving) return
            Thread.sleep(20)
        }
    }

    @Test
    fun `loads municipalities and preselects saved profile`() = runTest(dispatcher) {
        enqueueObce()
        val store = profileStore()
        store.save(io.github.painter99.jobsearch.data.storage.LocationProfile(municipalityIds = setOf("Obec/503657")))
        val vm = viewModel(store)

        awaitSettled(vm)

        val state = vm.state.value
        assertFalse(state.loading)
        assertFalse(state.loadError)
        assertEquals(setOf("Obec/503657"), state.selected)
        assertEquals(emptyList<io.github.painter99.jobsearch.core.model.Municipality>(), state.results) // prázdný dotaz = žádné výsledky
    }

    @Test
    fun `autocomplete - prefix results before contains results`() = runTest(dispatcher) {
        enqueueObce()
        val vm = viewModel()
        awaitSettled(vm)

        vm.onQueryChange("l")
        val names = vm.state.value.results.map { it.name }

        // prefix: Lutín, Litovel, Lipník nad Bečvou; pak obsah: Olomouc, Hulín
        assertEquals(
            listOf("Lutín", "Litovel", "Lipník nad Bečvou", "Olomouc", "Hulín"),
            names,
        )
    }

    @Test
    fun `autocomplete - case insensitive query`() = runTest(dispatcher) {
        enqueueObce()
        val vm = viewModel()
        awaitSettled(vm)

        vm.onQueryChange("OL")
        assertEquals(listOf("Olomouc"), vm.state.value.results.map { it.name })
    }

    @Test
    fun `autocomplete - contains match`() = runTest(dispatcher) {
        enqueueObce()
        val vm = viewModel()
        awaitSettled(vm)

        vm.onQueryChange("rov")
        // "rov" je uvnitř "Přerov"; Prostějov "rov" neobsahuje (p-r-o-s…)
        assertEquals(listOf("Přerov"), vm.state.value.results.map { it.name })
    }

    @Test
    fun `toggle - select and deselect`() = runTest(dispatcher) {
        enqueueObce()
        val vm = viewModel()
        awaitSettled(vm)

        vm.onQueryChange("lut")
        val lutin = vm.state.value.results.first()
        vm.toggle(lutin)
        assertTrue(vm.state.value.selected.contains("Obec/503657"))
        vm.toggle(lutin)
        assertFalse(vm.state.value.selected.contains("Obec/503657"))
    }

    @Test
    fun `save - writes selected municipalities into profile`() = runTest(dispatcher) {
        enqueueObce()
        val store = profileStore()
        val vm = viewModel(store)
        awaitSettled(vm)

        vm.onQueryChange("lut")
        vm.toggle(vm.state.value.results.first())
        vm.save()
        awaitSettled(vm)

        assertEquals(setOf("Obec/503657"), store.load().municipalityIds)
    }

    @Test
    fun `codelist load error - loadError state`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(503))
        val vm = viewModel()

        awaitSettled(vm)

        assertTrue(vm.state.value.loadError)
    }
}