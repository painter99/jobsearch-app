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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * T2: ProfileStore — lokalitní profil (DataStore, M1.6 vlna A).
 */
class ProfileStoreTest {

    private fun newTempFile(): File {
        val dir = Files.createTempDirectory("profile-test").toFile()
        return File(dir, "test.preferences_pb")
    }

    private fun newDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `profile - default is empty`() = runTest {
        val store = ProfileStore(newDataStore(newTempFile(), CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        assertEquals(LocationProfile(), store.load())
        assertTrue(store.load().isEmpty)
    }

    @Test
    fun `profile - roundtrip save and load`() = runTest {
        val store = ProfileStore(newDataStore(newTempFile(), CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        val profile = LocationProfile(
            municipalityIds = setOf("Obec/503657", "Obec/500496"),
            districtIds = setOf("Okres/3805"),
        )
        store.save(profile)
        assertEquals(profile, store.load())
    }

    @Test
    fun `profile - persists across instances on same file`() = runTest {
        val file = newTempFile()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ProfileStore(newDataStore(file, scope1)).save(
            LocationProfile(municipalityIds = setOf("Obec/503657"))
        )
        scope1.cancel()
        Thread.sleep(100)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store2 = ProfileStore(newDataStore(file, scope2))
        assertEquals(setOf("Obec/503657"), store2.load().municipalityIds)
        scope2.cancel()
    }
}