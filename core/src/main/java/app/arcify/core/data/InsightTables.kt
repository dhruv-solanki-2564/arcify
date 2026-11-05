package app.arcify.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.arcify.core.schedule.Repeat
import app.arcify.core.schedule.ScheduledSession
import kotlinx.coroutines.flow.Flow

/** One app's usage on one local day (`day` = local midnight in epoch millis). */
@Entity(tableName = "usage_day", primaryKeys = ["day", "packageName"])
data class UsageDayEntity(
    val day: Long,
    val packageName: String,
    val foregroundMs: Long,
    val launches: Int,
)

@Entity(tableName = "unlock_day")
data class UnlockDayEntity(@PrimaryKey val day: Long, val count: Int)

/** The user's own category for an app; apps without a row use the default from Android's app category. */
@Entity(tableName = "app_category")
data class AppCategoryEntity(@PrimaryKey val packageName: String, val category: String)

@Entity(tableName = "scheduled_session")
data class ScheduledSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startMinute: Int,
    val durationMin: Int,
    val pomodoro: Boolean,
    val strict: Boolean,
    val repeat: String,
    val daysMask: Int,
    val onceDate: Long?,
) {
    fun toModel() = ScheduledSession(
        id = id,
        title = title,
        startMinute = startMinute,
        durationMin = durationMin,
        pomodoro = pomodoro,
        strict = strict,
        repeat = Repeat.entries.firstOrNull { it.name == repeat } ?: Repeat.ONCE,
        daysMask = daysMask,
        onceDate = onceDate,
    )

    companion object {
        fun from(s: ScheduledSession) = ScheduledSessionEntity(
            id = s.id,
            title = s.title,
            startMinute = s.startMinute,
            durationMin = s.durationMin,
            pomodoro = s.pomodoro,
            strict = s.strict,
            repeat = s.repeat.name,
            daysMask = s.daysMask,
            onceDate = s.onceDate,
        )
    }
}

data class FocusStart(val startedAt: Long, val focusedMs: Long)

data class DayCount(val day: Long, val n: Int)

@Dao
interface InsightsDao {
    @Upsert
    suspend fun upsertUsage(rows: List<UsageDayEntity>)

    @Query("DELETE FROM usage_day WHERE day = :day")
    suspend fun deleteUsageDay(day: Long)

    @Query("SELECT * FROM usage_day WHERE day >= :fromDay")
    fun observeUsageSince(fromDay: Long): Flow<List<UsageDayEntity>>

    @Query("SELECT MIN(day) FROM usage_day")
    fun observeFirstUsageDay(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM usage_day WHERE day = :day")
    suspend fun rowsForDay(day: Long): Int

    @Upsert
    suspend fun upsertUnlocks(row: UnlockDayEntity)

    @Query("SELECT * FROM unlock_day WHERE day >= :fromDay")
    fun observeUnlocksSince(fromDay: Long): Flow<List<UnlockDayEntity>>

    @Query("SELECT * FROM app_category")
    fun observeCategories(): Flow<List<AppCategoryEntity>>

    @Upsert
    suspend fun upsertCategory(row: AppCategoryEntity)

    @Query("DELETE FROM app_category WHERE packageName = :packageName")
    suspend fun deleteCategory(packageName: String)

    @Query("SELECT startedAt, focusedMs FROM focus_session WHERE startedAt >= :fromMs AND flaggedOutlier = 0")
    fun observeFocusSince(fromMs: Long): Flow<List<FocusStart>>

    @Query("SELECT day, COUNT(*) AS n FROM short_form_event WHERE day >= :fromDay GROUP BY day")
    fun observeShortFormDaysSince(fromDay: Long): Flow<List<DayCount>>
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM scheduled_session ORDER BY startMinute, id")
    fun observeAll(): Flow<List<ScheduledSessionEntity>>

    @Query("SELECT * FROM scheduled_session")
    suspend fun all(): List<ScheduledSessionEntity>

    @Query("SELECT * FROM scheduled_session WHERE id = :id")
    suspend fun get(id: Long): ScheduledSessionEntity?

    @Upsert
    suspend fun upsert(session: ScheduledSessionEntity): Long

    @Query("DELETE FROM scheduled_session WHERE id = :id")
    suspend fun delete(id: Long)
}
