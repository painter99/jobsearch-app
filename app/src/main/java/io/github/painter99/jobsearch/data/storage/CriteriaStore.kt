package io.github.painter99.jobsearch.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.painter99.jobsearch.core.filter.UserCriteria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Perzistence Pavlovy latky [UserCriteria] (mzda + směnnost) — DataStore Preferences (M1.5).
 *
 * Výchozí hodnoty = Pavlův profil dle PRD (otázka 5): minimální mzda 40 000 Kč,
 * jen jednosměnné. Výchozí hodnota se vrací i při chybě čtení (poškozený soubor,
 * prakticky nedosažitelné) — appka nespadne, jen ukáže výchozí latku.
 */
class CriteriaStore(private val dataStore: DataStore<Preferences>) {

    /** Aktuální latka (suspend — jednorázové čtení pro sync/pipeline). */
    suspend fun load(): UserCriteria {
        return try {
            val prefs = dataStore.data.first()
            UserCriteria(
                minSalaryKc = prefs[KEY_MIN_SALARY] ?: DEFAULT.minSalaryKc,
                singleShiftOnly = prefs[KEY_SINGLE_SHIFT] ?: DEFAULT.singleShiftOnly,
            )
        } catch (e: Exception) {
            DEFAULT
        }
    }

    /** Reaktivní stream latky (pro UI M1.6). */
    val criteriaFlow: Flow<UserCriteria> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            UserCriteria(
                minSalaryKc = prefs[KEY_MIN_SALARY] ?: DEFAULT.minSalaryKc,
                singleShiftOnly = prefs[KEY_SINGLE_SHIFT] ?: DEFAULT.singleShiftOnly,
            )
        }

    /** Uloží latku (atomicky, jediný soubor DataStore). */
    suspend fun save(criteria: UserCriteria) {
        dataStore.edit { prefs ->
            prefs[KEY_MIN_SALARY] = criteria.minSalaryKc
            prefs[KEY_SINGLE_SHIFT] = criteria.singleShiftOnly
        }
    }

    companion object {
        private val DEFAULT = UserCriteria(minSalaryKc = 40_000, singleShiftOnly = true)
        private val KEY_MIN_SALARY = intPreferencesKey("criteria_min_salary_kc")
        private val KEY_SINGLE_SHIFT = booleanPreferencesKey("criteria_single_shift_only")
    }
}