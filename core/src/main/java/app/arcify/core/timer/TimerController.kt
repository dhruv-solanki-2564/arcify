package app.arcify.core.timer

import app.arcify.core.data.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Source of wall-clock time; swapped out in tests. */
fun interface WallClock {
    fun nowMs(): Long
}

/**
 * App-wide owner of the single active session. The UI and the foreground service both talk to
 * this; [FocusTimer] does the actual state changes. Finished sessions are saved here.
 */
@Singleton
class TimerController @Inject constructor(
    private val clock: WallClock,
    private val sessions: SessionRepository,
) {
    private val mutex = Mutex()
    private val _timer = MutableStateFlow<ActiveTimer?>(null)
    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 32)

    val timer: StateFlow<ActiveTimer?> = _timer.asStateFlow()
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    fun now(): Long = clock.nowMs()

    /** Starts a session unless one is already active. Returns whether a new session started. */
    suspend fun start(mode: TimerMode, strict: Boolean, tagId: Long? = null): Boolean = mutex.withLock {
        if (_timer.value != null) return@withLock false
        _timer.value = FocusTimer.start(mode, now(), strict, tagId)
        true
    }

    suspend fun pause() {
        mutex.withLock {
            _timer.value?.let { _timer.value = FocusTimer.pause(it, now()) }
        }
    }

    suspend fun resume() {
        mutex.withLock {
            _timer.value?.let { _timer.value = FocusTimer.resume(it, now()) }
        }
    }

    /** Applies phase changes and finishes the session when it reaches its end. */
    suspend fun tick() {
        mutex.withLock {
            val current = _timer.value ?: return@withLock
            val advance = FocusTimer.advance(current, now())
            _timer.value = advance.timer
            publish(advance.events)
        }
    }

    /** Ends the session if the strict rules allow it. Returns whether it stopped. */
    suspend fun stop(): Boolean = mutex.withLock {
        val current = _timer.value ?: return@withLock false
        if (!FocusTimer.canStop(current)) return@withLock false
        val events = FocusTimer.stop(current, now())
        _timer.value = null
        publish(events)
        true
    }

    private suspend fun publish(events: List<TimerEvent>) {
        for (event in events) {
            if (event is TimerEvent.SessionFinished) sessions.record(event.summary)
            _events.emit(event)
        }
    }
}
