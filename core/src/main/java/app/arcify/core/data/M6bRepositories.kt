package app.arcify.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.arcify.core.alarm.WakeAlarm
import app.arcify.core.batching.BatchingSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Study wake-up alarms (spec F14). */
@Singleton
class WakeAlarmRepository @Inject constructor(private val dao: WakeAlarmDao) {
    fun observe(): Flow<List<WakeAlarm>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun all(): List<WakeAlarm> = dao.all().map { it.toModel() }

    suspend fun get(id: Long): WakeAlarm? = dao.get(id)?.toModel()

    suspend fun save(alarm: WakeAlarm) {
        dao.upsert(WakeAlarmEntity.from(alarm))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

/** Notification batching settings and the notifications currently held (spec F13). */
@Singleton
class BatchingRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val dao: HeldNotificationDao,
) {
    val settings: Flow<BatchingSettings> = store.data.map { prefs ->
        BatchingSettings(
            enabled = prefs[ENABLED] ?: false,
            intervalMinutes = prefs[INTERVAL] ?: BatchingSettings.DEFAULT_INTERVAL_MIN,
            packages = prefs[PACKAGES].orEmpty(),
        )
    }

    suspend fun setEnabled(enabled: Boolean) = store.edit { it[ENABLED] = enabled }

    suspend fun setInterval(minutes: Int) = store.edit { it[INTERVAL] = minutes.coerceAtLeast(1) }

    suspend fun addPackage(packageName: String) = store.edit { it[PACKAGES] = it[PACKAGES].orEmpty() + packageName }

    suspend fun removePackage(packageName: String) = store.edit { it[PACKAGES] = it[PACKAGES].orEmpty() - packageName }

    /** "Reset to defaults": no apps are batched. */
    suspend fun clearPackages() = store.edit { it[PACKAGES] = emptySet() }

    suspend fun hold(notification: HeldNotificationEntity): Long = dao.insert(notification)

    suspend fun held(): List<HeldNotificationEntity> = dao.all()

    fun observeHeldCount(): Flow<Int> = dao.observeCount()

    suspend fun delivered(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.delete(ids)
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("batching_enabled")
        val INTERVAL = intPreferencesKey("batching_interval_min")
        val PACKAGES = stringSetPreferencesKey("batching_packages")
    }
}
