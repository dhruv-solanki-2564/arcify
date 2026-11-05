package app.arcify.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.arcify.core.content.IgSurface
import app.arcify.core.content.MonitoredBrowser
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormMode
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.StudyChannel
import app.arcify.core.content.WebsiteRule
import app.arcify.core.content.WebsiteRuleKind
import kotlinx.coroutines.flow.Flow

/** Room can't key on a nullable column, so "all surfaces" is stored as an empty string. */
internal fun IgSurface?.toKey(): String = this?.name.orEmpty()

internal fun String.toSurface(): IgSurface? = IgSurface.entries.firstOrNull { it.name == this }

@Entity(tableName = "short_form_rule", primaryKeys = ["platform", "surfaceKey"])
data class ShortFormRuleEntity(
    val platform: String,
    val surfaceKey: String,
    val mode: String,
    val dailyLimit: Int?,
    val strictUntil: Long?,
) {
    fun toModel() = ShortFormRule(
        platform = Platform.valueOf(platform),
        surface = surfaceKey.toSurface(),
        mode = ShortFormMode.valueOf(mode),
        dailyLimit = dailyLimit,
        strictUntil = strictUntil,
    )

    companion object {
        fun from(rule: ShortFormRule) = ShortFormRuleEntity(
            platform = rule.platform.name,
            surfaceKey = rule.surface.toKey(),
            mode = rule.mode.name,
            dailyLimit = rule.dailyLimit,
            strictUntil = rule.strictUntil,
        )
    }
}

/** One counted short-form item. `day` is the local midnight it belongs to, in epoch millis. */
@Entity(tableName = "short_form_event", indices = [Index("day", "platform")])
data class ShortFormEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val viaPackage: String,
    val platform: String,
    val surfaceKey: String,
    val itemKey: String,
    val atMs: Long,
)

data class SurfaceCount(val surfaceKey: String, val n: Int)

data class SourceCount(val viaPackage: String, val platform: String, val n: Int)

@Entity(tableName = "study_channel")
data class StudyChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val handle: String?,
    val isDefault: Boolean,
    val position: Int,
) {
    fun toModel() = StudyChannel(displayName, handle, isDefault)
}

@Entity(tableName = "website_rule")
data class WebsiteRuleEntity(
    @PrimaryKey val pattern: String,
    val kind: String,
    val strictUntil: Long?,
) {
    fun toModel() = WebsiteRule(pattern, WebsiteRuleKind.valueOf(kind), strictUntil)
}

@Entity(tableName = "monitored_browser")
data class MonitoredBrowserEntity(
    @PrimaryKey val packageName: String,
    /** Comma-separated address-bar view IDs. */
    val urlBarIds: String,
) {
    fun toModel() = MonitoredBrowser(packageName, urlBarIds.split(',').map { it.trim() }.filter { it.isNotEmpty() })

    companion object {
        fun from(browser: MonitoredBrowser) = MonitoredBrowserEntity(browser.packageName, browser.urlBarIds.joinToString(","))
    }
}

@Dao
interface ContentDao {
    // Short-form rules
    @Query("SELECT * FROM short_form_rule")
    fun observeShortFormRules(): Flow<List<ShortFormRuleEntity>>

    @Query("SELECT * FROM short_form_rule WHERE platform = :platform AND surfaceKey = :surfaceKey")
    suspend fun getShortFormRule(platform: String, surfaceKey: String): ShortFormRuleEntity?

    @Upsert
    suspend fun upsertShortFormRule(rule: ShortFormRuleEntity)

    @Query("DELETE FROM short_form_rule WHERE platform = :platform AND surfaceKey = :surfaceKey")
    suspend fun deleteShortFormRule(platform: String, surfaceKey: String)

    // Counter
    @Insert
    suspend fun insertShortFormEvent(event: ShortFormEventEntity)

    @Query(
        "SELECT surfaceKey, COUNT(*) AS n FROM short_form_event " +
            "WHERE day = :day AND platform = :platform GROUP BY surfaceKey",
    )
    suspend fun surfaceCounts(day: Long, platform: String): List<SurfaceCount>

    @Query(
        "SELECT viaPackage, platform, COUNT(*) AS n FROM short_form_event " +
            "WHERE day = :day GROUP BY viaPackage, platform ORDER BY n DESC",
    )
    fun observeSourceCounts(day: Long): Flow<List<SourceCount>>

    // Study channels
    @Query("SELECT * FROM study_channel ORDER BY position, id")
    fun observeStudyChannels(): Flow<List<StudyChannelEntity>>

    @Insert
    suspend fun insertStudyChannels(channels: List<StudyChannelEntity>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM study_channel")
    suspend fun maxStudyChannelPosition(): Int

    @Query("DELETE FROM study_channel WHERE id = :id")
    suspend fun deleteStudyChannel(id: Long)

    @Query("DELETE FROM study_channel")
    suspend fun clearStudyChannels()

    // Websites
    @Query("SELECT * FROM website_rule ORDER BY pattern")
    fun observeWebsiteRules(): Flow<List<WebsiteRuleEntity>>

    @Query("SELECT * FROM website_rule WHERE pattern = :pattern")
    suspend fun getWebsiteRule(pattern: String): WebsiteRuleEntity?

    @Upsert
    suspend fun upsertWebsiteRule(rule: WebsiteRuleEntity)

    @Query("DELETE FROM website_rule WHERE pattern = :pattern")
    suspend fun deleteWebsiteRule(pattern: String)

    // Browsers
    @Query("SELECT * FROM monitored_browser ORDER BY packageName")
    fun observeBrowsers(): Flow<List<MonitoredBrowserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrowsers(browsers: List<MonitoredBrowserEntity>)

    @Query("DELETE FROM monitored_browser WHERE packageName = :packageName")
    suspend fun deleteBrowser(packageName: String)

    @Query("DELETE FROM monitored_browser")
    suspend fun clearBrowsers()
}
