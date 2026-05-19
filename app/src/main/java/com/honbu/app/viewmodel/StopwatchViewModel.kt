package com.honbu.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StopwatchViewModel : ViewModel() {

    private val _elapsedMs  = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs

    private val _isRunning  = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var job: Job? = null
    private var lastMs = 0L

    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true
        lastMs = System.currentTimeMillis()
        job = viewModelScope.launch {
            while (isActive) {
                delay(16)
                val now = System.currentTimeMillis()
                _elapsedMs.value += now - lastMs
                lastMs = now
            }
        }
    }

    fun pause() {
        job?.cancel()
        _isRunning.value = false
    }

    fun reset() {
        job?.cancel()
        _isRunning.value = false
        _elapsedMs.value = 0L
    }

    override fun onCleared() { job?.cancel() }
}
