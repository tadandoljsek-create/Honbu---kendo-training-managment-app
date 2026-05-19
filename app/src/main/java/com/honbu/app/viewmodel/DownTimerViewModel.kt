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

class DownTimerViewModel(app: Application) : AndroidViewModel(app) {

    private val soundMgr = SoundManager(app)
    private val vibrator  = app.getSystemService<Vibrator>()
    private val soundPrefs = (app as HonbuApplication).soundPreferences

    private val _durationMs  = MutableStateFlow(3 * 60 * 1000L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _remainingMs = MutableStateFlow(3 * 60 * 1000L)
    val remainingMs: StateFlow<Long> = _remainingMs

    private val _isRunning   = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _isFinished  = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished

    private var job: Job? = null
    private var lastMs = 0L
    private var cfg = SoundConfig()

    init {
        viewModelScope.launch { soundPrefs.soundConfig.collect { cfg = it } }
    }

    fun setDuration(ms: Long) {
        if (_isRunning.value) return
        _durationMs.value  = ms
        _remainingMs.value = ms
        _isFinished.value  = false
    }

    fun start() {
        if (_isRunning.value || _isFinished.value) return
        _isRunning.value = true
        lastMs = System.currentTimeMillis()
        job = viewModelScope.launch {
            while (isActive && _remainingMs.value > 0) {
                delay(16)
                val now   = System.currentTimeMillis()
                val delta = now - lastMs
                lastMs = now
                _remainingMs.value = (_remainingMs.value - delta).coerceAtLeast(0L)
                if (_remainingMs.value == 0L) {
                    _isRunning.value  = false
                    _isFinished.value = true
                    soundMgr.playTimerEnd(cfg)
                }
            }
        }
    }

    fun pause() {
        job?.cancel()
        _isRunning.value = false
    }

    fun reset() {
        job?.cancel()
        _isRunning.value  = false
        _isFinished.value = false
        _remainingMs.value = _durationMs.value
    }

    override fun onCleared() {
        job?.cancel()
        soundMgr.release()
    }
}
