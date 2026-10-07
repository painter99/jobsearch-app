package io.github.painter99.jobsearch.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.painter99.jobsearch.core.filter.UserCriteria
import io.github.painter99.jobsearch.pipeline.SyncAnchorStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

/**
 * T1–T3: DataStore vrstva — sync kotva, latka, API klíč.
 *
 * JVM testy nad reálným DataStore (PreferenceDataStoreFactory na temp souboru,
 * žádný Android framework). „Persists across instances" = čtení novou instancí
 * nad stejným souborem (předchozí scope se zruší — DataStore vyžaduje jednu
 * aktivní instanci na soubor).
 */
class DataStoreStoresTest {

    private fun newTempFile(): File {
        val dir = Files.createTempDirectory("datastore-test").toFile()
        return File(dir, "test.preferences_pb")
    }

    private fun newDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    // --- T1: SyncAnchorStore ---

    @Test
    fun `anchor - default is day before increments dataset start`() = runTest {
        val file = newTempFile()
        val store = DataStoreSyncAnchorStore(newDataStore(file, CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        assertEquals(LocalDate.of(2024, 10, 30), (store as SyncAnchorStore).load())
    }

    @Test
    fun `anchor - roundtrip save and load`() = runTest {
        val file = newTempFile()
        val store = DataStoreSyncAnchorStore(newDataStore(file, CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        store.save(LocalDate.of(2026, 10, 7))
        assertEquals(LocalDate.of(2026, 10, 7), store.load())
    }

    @Test
    fun `anchor - persists across instances on same file`() = runTest {
        val file = newTempFile()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        DataStoreSyncAnchorStore(newDataStore(file, scope1)).save(LocalDate.of(2026, 10, 6))
        scope1.cancel()
        Thread.sleep(100) // dokončení interního actoru DataStore (uklizení activeFiles)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store2 = DataStoreSyncAnchorStore(newDataStore(file, scope2))
        assertEquals(LocalDate.of(2026, 10, 6), store2.load())
        scope2.cancel()
    }

    // --- T2: CriteriaStore ---

    @Test
    fun `criteria - defaults are 40000 and single shift only`() = runTest {
        val file = newTempFile()
        val store = CriteriaStore(newDataStore(file, CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        assertEquals(UserCriteria(minSalaryKc = 40_000, singleShiftOnly = true), store.load())
    }

    @Test
    fun `criteria - roundtrip save and load`() = runTest {
        val file = newTempFile()
        val store = CriteriaStore(newDataStore(file, CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        store.save(UserCriteria(minSalaryKc = 38_000, singleShiftOnly = false))
        assertEquals(UserCriteria(minSalaryKc = 38_000, singleShiftOnly = false), store.load())
    }

    @Test
    fun `criteria - persists across instances on same file`() = runTest {
        val file = newTempFile()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        CriteriaStore(newDataStore(file, scope1)).save(UserCriteria(minSalaryKc = 42_000, singleShiftOnly = true))
        scope1.cancel()
        Thread.sleep(100)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store2 = CriteriaStore(newDataStore(file, scope2))
        assertEquals(UserCriteria(minSalaryKc = 42_000, singleShiftOnly = true), store2.load())
        scope2.cancel()
    }

    // --- T3: ApiKeyStore ---

    @Test
    fun `api key - default is null (app fully functional without key)`() = runTest {
        val file = newTempFile()
        val store = ApiKeyStore(newDataStore(file, CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        assertNull(store.get())
    }

    @Test
    fun `api key - set get clear roundtrip`() = runTest {
        val file = newTempFile()
        val store = ApiKeyStore(newDataStore(file, CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        store.set("sk-or-test-123")
        assertEquals("sk-or-test-123", store.get())
        store.clear()
        assertNull(store.get())
    }

    @Test
    fun `api key - persists across instances on same file`() = runTest {
        val file = newTempFile()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ApiKeyStore(newDataStore(file, scope1)).set("sk-or-persist")
        scope1.cancel()
        Thread.sleep(100)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store2 = ApiKeyStore(newDataStore(file, scope2))
        assertEquals("sk-or-persist", store2.get())
        scope2.cancel()
    }
}