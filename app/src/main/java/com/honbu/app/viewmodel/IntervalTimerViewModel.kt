package com.honbu.app.viewmodel

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.honbu.app.HonbuApplication
import com.honbu.app.data.preferences.SoundConfig
import com.honbu.app.util.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class IntervalPhase { IDLE, STARTING, BASIC, SWITCH, COMPLETE }

data class IntervalState(
    val phase: IntervalPhase = IntervalPhase.IDLE,
    val remainingMs: Long = 0L,
    val currentRep: Int = 0,          // 0-based; display as currentRep+1
    // Settings (kept in state so the UI can reflect them):
    val startingMs: Long  = 0L,
    val basicMs: Long     = 2 * 60_000L,
    val switchMs: Long    = 20_000L,
    val useReps: Boolean  = false,
    val repCount: Int     = 3,
)

class IntervalTimerViewModel(app: Application) : AndroidViewModel(app) {

    private val soundMgr  = SoundManager(app)
    private val vibrator  = app.getSystemService<Vibrator>()
    private val soundPrefs = (app as HonbuApplication).soundPreferences

    private val _state     = MutableStateFlow(IntervalState())
    val state: StateFlow<IntervalState> = _state

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var job: Job? = null
    private var lastMs = 0L
    private var cfg = SoundConfig()

    init {
        viewModelScope.launch { soundPrefs.soundConfig.collect { cfg = it } }
    }

    fun updateSettings(
        startingMs: Long,
        basicMs: Long,
        switchMs: Long,
        useReps: Boolean,
        repCount: Int,
    ) {
        if (_isRunning.value) return
        _state.value = _state.value.copy(
            startingMs = startingMs,
            basicMs    = basicMs.coerceAtLeast(1_000L),
            switchMs   = switchMs.coerceAtLeast(1_000L),
            useReps    = useReps,
            repCount   = repCount.coerceAtLeast(1),
        )
    }

    fun start() {
        if (_isRunning.value) return
        val s = _state.value
        if (s.phase == IntervalPhase.IDLE || s.phase == IntervalPhase.COMPLETE) {
            // Fresh start
            val (phase, dur) = if (s.startingMs > 0)
                IntervalPhase.STARTING to s.startingMs
            else
                IntervalPhase.BASIC to s.basicMs
            _state.value = s.copy(phase = phase, remainingMs = dur, currentRep = 0)
        }
        // else: resume from pause — phase + remainingMs already correct
        launchJob()
    }

    fun pause() {
        job?.cancel()
        _isRunning.value = false
    }

    fun reset() {
        job?.cancel()
        _isRunning.value = false
        _state.value = _state.value.copy(
            phase = IntervalPhase.IDLE, remainingMs = 0L, currentRep = 0
        )
    }

    private fun launchJob() {
        _isRunning.value = true
        lastMs = System.currentTimeMillis()
        job = viewModelScope.launch {
            while (isActive) {
                delay(16)
                val now   = System.currentTimeMillis()
                val delta = now - lastMs
                lastMs = now
                val newRem = (_state.value.remainingMs - delta).coerceAtLeast(0L)
                if (newRem == 0L) advancePhase() else _state.value = _state.value.copy(remainingMs = newRem)
            }
        }
    }

    private fun advancePhase() {
        val s = _state.value
        when (s.phase) {
            IntervalPhase.STARTING -> {
                soundMgr.playIntervalStart(cfg)
                _state.value = s.copy(phase = IntervalPhase.BASIC, remainingMs = s.basicMs)
            }
            IntervalPhase.BASIC -> {
                soundMgr.playIntervalChange(cfg)
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                _state.value = s.copy(phase = IntervalPhase.SWITCH, remainingMs = s.switchMs)
            }
            IntervalPhase.SWITCH -> {
                val newRep  = s.currentRep + 1
                val maxReps = if (s.useReps) s.repCount else Int.MAX_VALUE
                if (newRep >= maxReps) {
                    soundMgr.playTimerEnd(cfg)
                    job?.cancel()
                    _isRunning.value = false
                    _state.value = s.copy(phase = IntervalPhase.COMPLETE, remainingMs = 0L, currentRep = newRep)
                } else {
                    soundMgr.playIntervalStart(cfg)
                    _state.value = s.copy(phase = IntervalPhase.BASIC, remainingMs = s.basicMs, currentRep = newRep)
                }
            }
            else -> {}
        }
    }

    override fun onCleared() {
        job?.cancel()
        soundMgr.release()
    }
}
