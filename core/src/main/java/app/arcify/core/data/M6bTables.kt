package app.arcify.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.arcify.core.alarm.WakeAlarm
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wake_alarm")
data class WakeAlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minuteOfDay: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val snoozesAllowed: Int,
    val snoozeMinutes: Int,
    val focusMinutes: Int,
) {
    fun toModel() = WakeAlarm(id, minuteOfDay, daysMask, enabled, snoozesAllowed, snoozeMinutes, focusMinutes)

    companion object {
        fun from(a: WakeAlarm) =
            WakeAlarmEntity(a.id, a.minuteOfDay, a.daysMask, a.enabled, a.snoozesAllowed, a.snoozeMinutes, a.focusMinutes)
    }
}

/** A notification held for the next batch delivery (spec F13). Deleted once delivered. */
@Entity(tableName = "held_notification")
data class HeldNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
)

@Dao
interface WakeAlarmDao {
    @Query("SELECT * FROM wake_alarm ORDER BY minuteOfDay, id")
    fun observeAll(): Flow<List<WakeAlarmEntity>>

    @Query("SELECT * FROM wake_alarm")
    suspend fun all(): List<WakeAlarmEntity>

    @Query("SELECT * FROM wake_alarm WHERE id = :id")
    suspend fun get(id: Long): WakeAlarmEntity?

    @Upsert
    suspend fun upsert(alarm: WakeAlarmEntity): Long

    @Query("DELETE FROM wake_alarm WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HeldNotificationDao {
    @Insert
    suspend fun insert(notification: HeldNotificationEntity): Long

    @Query("SELECT * FROM held_notification ORDER BY postedAt")
    suspend fun all(): List<HeldNotificationEntity>

    @Query("SELECT COUNT(*) FROM held_notification")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM held_notification WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
