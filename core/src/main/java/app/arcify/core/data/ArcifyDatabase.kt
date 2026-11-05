package app.arcify.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Name of a theme colour token, not a raw colour, so tags follow the UI theme. */
    val colorToken: String,
)

@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val tagId: Long?,
    val startedAt: Long,
    val endedAt: Long,
    val focusedMs: Long,
    val pausedMs: Long,
    val completed: Boolean,
    val strict: Boolean,
    val focusCycles: Int,
    /** Pushes the day past the daily cap; excluded from totals and streaks. */
    val flaggedOutlier: Boolean,
)

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_session ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query(
        "SELECT COALESCE(SUM(focusedMs), 0) FROM focus_session " +
            "WHERE startedAt >= :fromMs AND startedAt < :toMs AND flaggedOutlier = 0",
    )
    suspend fun focusedBetween(fromMs: Long, toMs: Long): Long

    @Query(
        "SELECT COALESCE(SUM(focusedMs), 0) FROM focus_session " +
            "WHERE startedAt >= :fromMs AND startedAt < :toMs AND flaggedOutlier = 0",
    )
    fun observeFocusedBetween(fromMs: Long, toMs: Long): Flow<Long>
}

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tag ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>
}

@Database(entities = [TagEntity::class, FocusSessionEntity::class], version = 1, exportSchema = true)
abstract class ArcifyDatabase : RoomDatabase() {
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun tagDao(): TagDao
}
