package app.arcify.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.arcify.core.block.AppLimit
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_limit")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val dailyLimitMs: Long,
    val enabled: Boolean,
    val strictUntil: Long?,
    val reminderPercent: Int?,
) {
    fun toModel() = AppLimit(packageName, dailyLimitMs, enabled, strictUntil, reminderPercent)

    companion object {
        fun from(limit: AppLimit) = AppLimitEntity(
            packageName = limit.packageName,
            dailyLimitMs = limit.dailyLimitMs,
            enabled = limit.enabled,
            strictUntil = limit.strictUntil,
            reminderPercent = limit.reminderPercent,
        )
    }
}

/** Apps the user allows during strict focus, on top of the always-allowed system apps. */
@Entity(tableName = "strict_allowed_app")
data class StrictAllowedAppEntity(@PrimaryKey val packageName: String)

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limit ORDER BY packageName")
    fun observeAll(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limit WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppLimitEntity?

    @Upsert
    suspend fun upsert(limit: AppLimitEntity)

    @Query("DELETE FROM app_limit WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface StrictAllowedAppDao {
    @Query("SELECT packageName FROM strict_allowed_app ORDER BY packageName")
    fun observeAll(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: StrictAllowedAppEntity)

    @Query("DELETE FROM strict_allowed_app WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM strict_allowed_app")
    suspend fun clear()
}
