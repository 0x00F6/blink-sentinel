package dev.homesentinel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.homesentinel.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LogRetentionSettingsTest {
    private class MemoryStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock {
                transform(state.value).also { state.value = it }
            }
    }

    @Test fun existingInstallationsReceiveThe500DefaultWithoutChangingOtherSettings() =
        runTest {
            val repository = SettingsRepository(MemoryStore(preferencesOf(stringPreferencesKey("ssid") to "Existing home")))
            assertEquals(500, repository.snapshot().logRetention)
            assertEquals("Existing home", repository.snapshot().homeSsid)
        }

    @Test fun savedLimitSurvivesRepositoryRecreationAndOtherSettingsUpdates() =
        runTest {
            val store = MemoryStore()
            val repository = SettingsRepository(store)
            repository.update { it.copy(logRetention = 1_500) }
            repository.update { it.copy(homeSsid = "Home") }
            assertEquals(1_500, SettingsRepository(store).snapshot().logRetention)
            assertEquals("Home", SettingsRepository(store).snapshot().homeSsid)
        }

    @Test fun invalidLimitsRejectTheWholeUpdate() =
        runTest {
            val repository = SettingsRepository(MemoryStore())
            for (limit in listOf(49, 5_001)) {
                try {
                    repository.update { it.copy(logRetention = limit, homeSsid = "Must not save") }
                    fail("Invalid limit accepted")
                } catch (_: IllegalArgumentException) {
                }
            }
            assertEquals(500, repository.snapshot().logRetention)
            assertEquals("", repository.snapshot().homeSsid)
        }

    @Test fun corruptedPersistedLimitsRemainBounded() =
        runTest {
            val key = intPreferencesKey("log_retention")
            assertEquals(50, SettingsRepository(MemoryStore(preferencesOf(key to -1))).snapshot().logRetention)
            assertEquals(5_000, SettingsRepository(MemoryStore(preferencesOf(key to Int.MAX_VALUE))).snapshot().logRetention)
        }
}
