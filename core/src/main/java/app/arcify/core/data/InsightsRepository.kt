package app.arcify.core.data

import androidx.room.withTransaction
import app.arcify.core.insights.AppCategory
import app.arcify.core.schedule.ScheduledSession
import app.arcify.core.usage.AppUsage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stored usage history, app categories and the inputs insights and streaks need (spec F08).
 * Android only keeps detailed usage events for a few days, so each day is saved here.
 */
@Singleton
class InsightsRepository @Inject constructor(
    private val db: ArcifyDatabase,
    private val dao: InsightsDao,
) {
    /** Replaces everything stored for one day. */
    suspend fun saveDay(day: Long, usage: Map<String, AppUsage>, unlocks: Int?) {
        db.withTransaction {
            dao.deleteUsageDay(day)
            dao.upsertUsage(usage.map { (pkg, u) -> UsageDayEntity(day, pkg, u.foregroundMs, u.launches) })
            if (unlocks != null) dao.upsertUnlocks(UnlockDayEntity(day, unlocks))
        }
    }

    suspend fun hasDay(day: Long): Boolean = dao.rowsForDay(day) > 0

    fun observeUsageSince(fromDay: Long): Flow<List<UsageDayEntity>> = dao.observeUsageSince(fromDay)

    fun observeFirstUsageDay(): Flow<Long?> = dao.observeFirstUsageDay()

    fun observeUnlocksSince(fromDay: Long): Flow<Map<Long, Int>> =
        dao.observeUnlocksSince(fromDay).map { rows -> rows.associate { it.day to it.count } }

    /** The user's own category choices; apps not listed use their default. */
    fun observeCategories(): Flow<Map<String, AppCategory>> = dao.observeCategories().map { rows ->
        rows.mapNotNull { row -> AppCategory.entries.firstOrNull { it.name == row.category }?.let { row.packageName to it } }.toMap()
    }

    /** Null resets the app to its default category. */
    suspend fun setCategory(packageName: String, category: AppCategory?) {
        if (category == null) dao.deleteCategory(packageName) else dao.upsertCategory(AppCategoryEntity(packageName, category.name))
    }

    fun observeFocusSince(fromMs: Long): Flow<List<FocusStart>> = dao.observeFocusSince(fromMs)

    fun observeShortFormDaysSince(fromDay: Long): Flow<Map<Long, Int>> =
        dao.observeShortFormDaysSince(fromDay).map { rows -> rows.associate { it.day to it.n } }
}

@Singleton
class ScheduleRepository @Inject constructor(private val dao: ScheduleDao) {
    fun observe(): Flow<List<ScheduledSession>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun all(): List<ScheduledSession> = dao.all().map { it.toModel() }

    suspend fun get(id: Long): ScheduledSession? = dao.get(id)?.toModel()

    suspend fun save(session: ScheduledSession) {
        dao.upsert(ScheduledSessionEntity.from(session))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
