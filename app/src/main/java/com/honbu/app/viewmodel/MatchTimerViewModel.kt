package com.honbu.app.viewmodel

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.honbu.app.HonbuApplication
import com.honbu.app.data.db.AppDatabase
import com.honbu.app.data.preferences.SoundConfig
import com.honbu.app.util.CsvExporter
import com.honbu.app.util.MatchRecord
import com.honbu.app.util.SoundManager
import com.honbu.app.util.TimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class MatchPhase { IDLE, RUNNING, PAUSED, FINISHED, ENCHO, ENCHO_PAUSED }
enum class PlayerSide { WHITE, RED }

data class MatchEvent(
    val side: PlayerSide,
    val type: String,      // "M"|"K"|"D"|"T"|"H"|"H2"
    val elapsedMs: Long,
)

data class MatchState(
    val phase: MatchPhase    = MatchPhase.IDLE,
    val durationMs: Long     = 3 * 60_000L,
    val remainingMs: Long    = 3 * 60_000L,
    val enchoElapsedMs: Long = 0L,
    val whitePlayer: String  = "",
    val redPlayer: String    = "",
    val events: List<MatchEvent> = emptyList(),
    val whiteHansoku: Int    = 0,
    val redHansoku: Int      = 0,
    val notes: String        = "",
    val notesManuallyEdited: Boolean = false,
    val hasSubmittedResults: Boolean = false,
) {
    val elapsedMs: Long get() = durationMs - remainingMs

    val whitePoints: List<MatchEvent>
        get() = events.filter { it.side == PlayerSide.WHITE && it.type in POINT_TYPES }

    val redPoints: List<MatchEvent>
        get() = events.filter { it.side == PlayerSide.RED && it.type in POINT_TYPES }

    fun currentElapsedMs() = when (phase) {
        MatchPhase.ENCHO, MatchPhase.ENCHO_PAUSED -> durationMs + enchoElapsedMs
        else -> elapsedMs
    }

    companion object {
        val POINT_TYPES = setOf("M", "K", "D", "T", "H2")
        val SCORING_PHASES = setOf(
            MatchPhase.RUNNING, MatchPhase.PAUSED,
            MatchPhase.ENCHO,   MatchPhase.ENCHO_PAUSED
        )
        // Max hansoku per player — each 2nd one grants an ippon to the opponent
        const val MAX_HANSOKU = 4
    }
}

class MatchTimerViewModel(app: Application) : AndroidViewModel(app) {

    private val soundMgr   = SoundManager(app)
    private val vibrator   = app.getSystemService<Vibrator>()
    private val soundPrefs = (app as HonbuApplication).soundPreferences
    private val memberDao  = AppDatabase.getInstance(app).memberDao()

    val members = memberDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state

    private val _submitResult = MutableStateFlow<String?>(null)
    val submitResult: StateFlow<String?> = _submitResult

    private var job: Job? = null
    private var lastMs = 0L
    private var cfg = SoundConfig()
    private var matchStartDate = ""
    private var matchStartTime = ""
    private var csvFileUri = ""

    init {
        viewModelScope.launch { soundPrefs.soundConfig.collect {
            cfg        = it
            csvFileUri = it.csvFileUri
        }}
    }

    // ── State restore (for pool match navigation) ────────────────────

    fun restoreState(saved: MatchState) {
        job?.cancel()
        // If it was actively ticking when saved, keep it paused — user must resume
        val phase = when (saved.phase) {
            MatchPhase.RUNNING -> MatchPhase.PAUSED
            MatchPhase.ENCHO   -> MatchPhase.ENCHO_PAUSED
            else               -> saved.phase
        }
        _state.value = saved.copy(phase = phase)
    }

    // ── Duration / players ───────────────────────────────────────────

    fun setDuration(ms: Long) {
        if (_state.value.phase != MatchPhase.IDLE) return
        _state.value = _state.value.copy(durationMs = ms, remainingMs = ms)
    }

    fun updatePlayerName(side: PlayerSide, newName: String) {
        val s = _state.value
        val oldDisplay = when (side) {
            PlayerSide.WHITE -> s.whitePlayer.ifEmpty { "White" }
            PlayerSide.RED   -> s.redPlayer.ifEmpty   { "Red"   }
        }
        val updated = when (side) {
            PlayerSide.WHITE -> s.copy(whitePlayer = newName)
            PlayerSide.RED   -> s.copy(redPlayer   = newName)
        }
        _state.value = if (!s.notesManuallyEdited) {
            updated.copy(notes = generateNotes(updated))
        } else {
            val newDisplay = newName.ifEmpty { if (side == PlayerSide.WHITE) "White" else "Red" }
            updated.copy(notes = updated.notes.replace(oldDisplay, newDisplay))
        }
    }

    fun updateNotes(text: String) {
        _state.value = _state.value.copy(
            notes = text,
            notesManuallyEdited = true,
            hasSubmittedResults = false  // re-enable submit when notes are edited
        )
    }

    // ── Timer control ────────────────────────────────────────────────

    fun startMatch() {
        val s = _state.value
        if (s.phase != MatchPhase.IDLE && s.phase != MatchPhase.PAUSED) return
        if (s.phase == MatchPhase.IDLE) {
            val now = LocalDateTime.now()
            matchStartDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            matchStartTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        _state.value = s.copy(phase = MatchPhase.RUNNING)
        launchTimerJob()
    }

    fun pause() {
        job?.cancel()
        val s = _state.value
        _state.value = s.copy(phase = when (s.phase) {
            MatchPhase.RUNNING -> MatchPhase.PAUSED
            MatchPhase.ENCHO   -> MatchPhase.ENCHO_PAUSED
            else               -> s.phase
        })
    }

    fun startEncho() {
        if (_state.value.phase != MatchPhase.FINISHED) return
        _state.value = _state.value.copy(phase = MatchPhase.ENCHO, enchoElapsedMs = 0L)
        launchTimerJob()
    }

    fun resumeEncho() {
        if (_state.value.phase != MatchPhase.ENCHO_PAUSED) return
        _state.value = _state.value.copy(phase = MatchPhase.ENCHO)
        launchTimerJob()
    }

    fun resetMatch() {
        job?.cancel()
        val s = _state.value
        _state.value = MatchState(
            durationMs  = s.durationMs,
            remainingMs = s.durationMs,
            whitePlayer = s.whitePlayer,
            redPlayer   = s.redPlayer,
        )
    }

    private fun launchTimerJob() {
        lastMs = System.currentTimeMillis()
        job = viewModelScope.launch {
            while (isActive) {
                delay(16)
                val now = System.currentTimeMillis()
                val delta = now - lastMs
                lastMs = now
                val s = _state.value
                when (s.phase) {
                    MatchPhase.RUNNING -> {
                        val rem = (s.remainingMs - delta).coerceAtLeast(0L)
                        _state.value = s.copy(remainingMs = rem)
                        if (rem == 0L) onMatchEnd()
                    }
                    MatchPhase.ENCHO -> {
                        _state.value = s.copy(enchoElapsedMs = s.enchoElapsedMs + delta)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun onMatchEnd() {
        job?.cancel()
        _state.value = _state.value.copy(phase = MatchPhase.FINISHED)
        soundMgr.playTimerEnd(cfg)
        vibrator?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // ── Scoring ──────────────────────────────────────────────────────

    fun addPoint(side: PlayerSide, technique: String) {
        val s = _state.value
        if (s.phase !in MatchState.SCORING_PHASES) return
        val current = if (side == PlayerSide.WHITE) s.whitePoints else s.redPoints
        if (current.size >= 2) return
        applyEvent(s, MatchEvent(side, technique, s.currentElapsedMs()))
    }

    fun addHansoku(side: PlayerSide) {
        val s = _state.value
        if (s.phase !in MatchState.SCORING_PHASES) return

        val elapsed   = s.currentElapsedMs()
        val newWhiteH = if (side == PlayerSide.WHITE) s.whiteHansoku + 1 else s.whiteHansoku
        val newRedH   = if (side == PlayerSide.RED)   s.redHansoku   + 1 else s.redHansoku

        // Guard: never exceed max
        if (side == PlayerSide.WHITE && newWhiteH > MatchState.MAX_HANSOKU) return
        if (side == PlayerSide.RED   && newRedH   > MatchState.MAX_HANSOKU) return

        var newEvents = s.events + MatchEvent(side, "H", elapsed)

        // Every 2nd hansoku (i.e. count divisible by 2) awards an ippon to the opponent
        val newCount  = if (side == PlayerSide.WHITE) newWhiteH else newRedH
        val doubled   = newCount % 2 == 0
        if (doubled) {
            val oppSide   = if (side == PlayerSide.WHITE) PlayerSide.RED else PlayerSide.WHITE
            val oppPoints = if (oppSide == PlayerSide.WHITE) s.whitePoints else s.redPoints
            if (oppPoints.size < 2) {
                newEvents = newEvents + MatchEvent(oppSide, "H2", elapsed)
            }
        }

        val updated = s.copy(
            events       = newEvents,
            whiteHansoku = newWhiteH,
            redHansoku   = newRedH,
            hasSubmittedResults = false,
        )
        _state.value = if (!s.notesManuallyEdited) updated.copy(notes = generateNotes(updated)) else updated
    }

    fun undoLastEvent() {
        val s = _state.value
        if (s.events.isEmpty()) return

        val last = s.events.last()
        val newWhiteH: Int
        val newRedH: Int
        val newEvents: List<MatchEvent>

        if (last.type == "H2") {
            val hIdx = s.events.dropLast(1).indexOfLast { it.type == "H" }
            newEvents = if (hIdx >= 0) {
                s.events.dropLast(1).toMutableList().also { it.removeAt(hIdx) }
            } else {
                s.events.dropLast(1)
            }
            val hSide = if (hIdx >= 0) s.events[hIdx].side
                        else if (last.side == PlayerSide.WHITE) PlayerSide.RED else PlayerSide.WHITE
            newWhiteH = if (hSide == PlayerSide.WHITE) (s.whiteHansoku - 1).coerceAtLeast(0) else s.whiteHansoku
            newRedH   = if (hSide == PlayerSide.RED)   (s.redHansoku   - 1).coerceAtLeast(0) else s.redHansoku
        } else if (last.type == "H") {
            newEvents = s.events.dropLast(1)
            newWhiteH = if (last.side == PlayerSide.WHITE) (s.whiteHansoku - 1).coerceAtLeast(0) else s.whiteHansoku
            newRedH   = if (last.side == PlayerSide.RED)   (s.redHansoku   - 1).coerceAtLeast(0) else s.redHansoku
        } else {
            newEvents = s.events.dropLast(1)
            newWhiteH = s.whiteHansoku
            newRedH   = s.redHansoku
        }

        val updated = s.copy(
            events       = newEvents,
            whiteHansoku = newWhiteH,
            redHansoku   = newRedH,
            hasSubmittedResults = false,
        )
        _state.value = if (!s.notesManuallyEdited) updated.copy(notes = generateNotes(updated)) else updated
    }

    private fun applyEvent(s: MatchState, event: MatchEvent) {
        val updated = s.copy(events = s.events + event, hasSubmittedResults = false)
        _state.value = if (!s.notesManuallyEdited) updated.copy(notes = generateNotes(updated)) else updated
    }

    // ── Notes ────────────────────────────────────────────────────────

    private fun generateNotes(s: MatchState): String {
        fun label(e: MatchEvent) = "${e.type}(${TimeFormatter.formatMs(e.elapsedMs)})"
        val whiteEvts = s.events.filter { it.side == PlayerSide.WHITE }
        val redEvts   = s.events.filter { it.side == PlayerSide.RED   }
        return buildList {
            if (whiteEvts.isNotEmpty())
                add("${s.whitePlayer.ifEmpty { "White" }}: ${whiteEvts.joinToString(", ", transform = ::label)}")
            if (redEvts.isNotEmpty())
                add("${s.redPlayer.ifEmpty { "Red" }}: ${redEvts.joinToString(", ", transform = ::label)}")
        }.joinToString("\n")
    }

    // ── Submit ───────────────────────────────────────────────────────

    fun submit(context: Context) {
        val s = _state.value
        viewModelScope.launch {
            val wp = s.whitePoints
            val rp = s.redPoints
            val winner = when {
                wp.size > rp.size -> s.whitePlayer.ifEmpty { "White" }
                rp.size > wp.size -> s.redPlayer.ifEmpty   { "Red"   }
                else              -> "Draw"
            }
            val record = MatchRecord(
                matchStartDate       = matchStartDate.ifEmpty { "unknown" },
                matchStartTime       = matchStartTime.ifEmpty { "unknown" },
                whitePlayer          = s.whitePlayer,
                redPlayer            = s.redPlayer,
                whitePoint1Technique = wp.getOrNull(0)?.type ?: "",
                whitePoint1Time      = wp.getOrNull(0)?.let { TimeFormatter.formatMs(it.elapsedMs) } ?: "",
                whitePoint2Technique = wp.getOrNull(1)?.type ?: "",
                whitePoint2Time      = wp.getOrNull(1)?.let { TimeFormatter.formatMs(it.elapsedMs) } ?: "",
                redPoint1Technique   = rp.getOrNull(0)?.type ?: "",
                redPoint1Time        = rp.getOrNull(0)?.let { TimeFormatter.formatMs(it.elapsedMs) } ?: "",
                redPoint2Technique   = rp.getOrNull(1)?.type ?: "",
                redPoint2Time        = rp.getOrNull(1)?.let { TimeFormatter.formatMs(it.elapsedMs) } ?: "",
                whiteHansoku = s.whiteHansoku,
                redHansoku   = s.redHansoku,
                winner       = winner,
                hadEncho     = s.enchoElapsedMs > 0,
                notes        = s.notes,
            )
            val result = CsvExporter.export(
                context          = context,
                record           = record,
                persistedFileUri = csvFileUri,
                onFileUriCreated = { newUri ->
                    csvFileUri = newUri
                    viewModelScope.launch { soundPrefs.setCsvFileUri(newUri) }
                }
            )
            if (result.isSuccess) {
                _state.value = _state.value.copy(hasSubmittedResults = true)
            }
            _submitResult.value = result.getOrElse { "Error: ${it.message}" }
        }
    }

    fun clearSubmitResult() { _submitResult.value = null }

    override fun onCleared() {
        job?.cancel()
        soundMgr.release()
    }
}
