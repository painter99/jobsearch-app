package io.github.painter99.jobsearch.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.painter99.jobsearch.core.filter.LocationFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Read-only přístup k lokalitnímu profilu — kontrakt pro ViewModels (M1.6):
 * testy fákují provider, DataStore implementace = [ProfileStore].
 */
interface ProfileProvider {
    suspend fun load(): LocationProfile
}

/**
 * Uživatelský profil lokalit (PRD otázka 4 — realizace M1.6 vlna A, D1 GO):
 * více obcí najednou, jeden aktivní profil. IDs ve formátu "Obec/&lt;ruian&gt;"
 * / "Okres/&lt;lau&gt;" — konzumuje je [LocationFilter].
 *
 * Výchozí = prázdný profil (appka je univerzální pro celou ČR; žádné
 * hardcoded obce v repu — repo je veřejné). Více uložených profilů = v2.
 */
data class LocationProfile(
    val municipalityIds: Set<String> = emptySet(),
    val districtIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = municipalityIds.isEmpty() && districtIds.isEmpty()
}

class ProfileStore(private val dataStore: DataStore<Preferences>) : ProfileProvider {

    /** Aktuální profil (suspend — jednorázové čtení pro pipeline). */
    override suspend fun load(): LocationProfile {
        return try {
            val prefs = dataStore.data.first()
            LocationProfile(
                municipalityIds = prefs[KEY_MUNICIPALITIES] ?: emptySet(),
                districtIds = prefs[KEY_DISTRICTS] ?: emptySet(),
            )
        } catch (e: Exception) {
            LocationProfile()
        }
    }

    /** Reaktivní stream profilu (pro UI). */
    val profileFlow: Flow<LocationProfile> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            LocationProfile(
                municipalityIds = prefs[KEY_MUNICIPALITIES] ?: emptySet(),
                districtIds = prefs[KEY_DISTRICTS] ?: emptySet(),
            )
        }

    /** Uloží profil (atomicky; districtIds se zachovávají — vlna A mění jen obce). */
    suspend fun save(profile: LocationProfile) {
        dataStore.edit { prefs ->
            prefs[KEY_MUNICIPALITIES] = profile.municipalityIds
            prefs[KEY_DISTRICTS] = profile.districtIds
        }
    }

    companion object {
        private val KEY_MUNICIPALITIES = stringSetPreferencesKey("profile_municipality_ids")
        private val KEY_DISTRICTS = stringSetPreferencesKey("profile_district_ids")
    }
}