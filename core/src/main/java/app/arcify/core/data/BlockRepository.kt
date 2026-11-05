package app.arcify.core.data

import app.arcify.core.block.AppLimit
import app.arcify.core.block.LimitRules
import app.arcify.core.timer.DayBounds
import app.arcify.core.timer.WallClock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App limits and strict-focus allowed apps. Enforces the strict-lock rules on every change. */
@Singleton
class BlockRepository @Inject constructor(
    private val limits: AppLimitDao,
    private val allowed: StrictAllowedAppDao,
    private val clock: WallClock,
) {
    fun observeLimits(): Flow<List<AppLimit>> = limits.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Saves [limit] unless an existing strict lock forbids the change. Returns whether it was saved. */
    suspend fun saveLimit(limit: AppLimit): Boolean {
        val current = limits.get(limit.packageName)?.toModel()
        if (current != null && !LimitRules.canChange(current, limit, clock.nowMs())) return false
        limits.upsert(AppLimitEntity.from(limit))
        return true
    }

    /** Deletes the limit unless it is strictly locked. Returns whether it was deleted. */
    suspend fun deleteLimit(packageName: String): Boolean {
        val current = limits.get(packageName)?.toModel() ?: return true
        if (!LimitRules.canChange(current, null, clock.nowMs())) return false
        limits.delete(packageName)
        return true
    }

    /** Locks the limit in Strict Mode until the end of today (local time). */
    suspend fun lockForToday(packageName: String): Boolean {
        val current = limits.get(packageName)?.toModel() ?: return false
        val endOfDay = DayBounds.of(clock.nowMs(), ZoneId.systemDefault()).endMs
        val lockEnd = maxOf(current.strictUntil ?: Long.MIN_VALUE, endOfDay)
        return saveLimit(current.copy(enabled = true, strictUntil = lockEnd))
    }

    fun observeAllowedApps(): Flow<Set<String>> = allowed.observeAll().map { it.toSet() }

    suspend fun allowApp(packageName: String) = allowed.insert(StrictAllowedAppEntity(packageName))

    suspend fun disallowApp(packageName: String) = allowed.delete(packageName)

    /** "Reset to defaults": only the always-allowed system apps remain. */
    suspend fun resetAllowedApps() = allowed.clear()
}
