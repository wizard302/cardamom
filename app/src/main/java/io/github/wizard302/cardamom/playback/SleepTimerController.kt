package io.github.wizard302.cardamom.playback

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sleep timer state, shared by the UI and PlaybackService.
 *
 * The timer lives app-side rather than in a screen so that it keeps running when
 * the UI is gone. Deadlines use [SystemClock.elapsedRealtime] (monotonic, counts
 * deep sleep) but are awaited with a plain coroutine delay: no alarm, no wake
 * lock. On a dozing device the pause may land late — that is deliberate; the
 * point is stopping playback that is actually running, and running playback
 * keeps the process awake.
 */
@Singleton
class SleepTimerController @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdown: Job? = null

    /** Deadline on the elapsed-realtime clock, or null when no timed sleep is armed. */
    private val _endTimeMs = MutableStateFlow<Long?>(null)
    val endTimeMs: StateFlow<Long?> = _endTimeMs.asStateFlow()

    /** "End of track" mode: pause on the next automatic track transition. */
    private val _stopAfterTrack = MutableStateFlow(false)
    val stopAfterTrack: StateFlow<Boolean> = _stopAfterTrack.asStateFlow()

    /** Emits when a deadline passes; PlaybackService pauses the player on it. */
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    val isActive: StateFlow<Boolean> =
        combine(_endTimeMs, _stopAfterTrack) { end, afterTrack -> end != null || afterTrack }
            .stateIn(scope, SharingStarted.Eagerly, false)

    fun start(durationMs: Long) {
        if (durationMs <= 0L) {
            cancel()
            return
        }
        countdown?.cancel()
        _stopAfterTrack.value = false
        val deadline = SystemClock.elapsedRealtime() + durationMs
        _endTimeMs.value = deadline
        countdown = scope.launch {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            // Disarm before signalling: the service pauses on the emission, and
            // the pause listener must not find the timer still running.
            _endTimeMs.value = null
            _stopAfterTrack.value = false
            _expired.tryEmit(Unit)
        }
    }

    fun startAfterCurrentTrack() {
        countdown?.cancel()
        countdown = null
        _endTimeMs.value = null
        _stopAfterTrack.value = true
    }

    fun cancel() {
        countdown?.cancel()
        countdown = null
        _endTimeMs.value = null
        _stopAfterTrack.value = false
    }

    /**
     * Milliseconds left, ticking once a second while collected; null when no
     * timed sleep is armed (including "end of track" mode, which has no deadline).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun remainingMs(): Flow<Long?> = _endTimeMs.flatMapLatest { end ->
        if (end == null) {
            flowOf(null)
        } else {
            flow {
                while (true) {
                    val left = end - SystemClock.elapsedRealtime()
                    emit(left.coerceAtLeast(0L))
                    if (left <= 0L) break
                    delay(1_000)
                }
            }
        }
    }
}
