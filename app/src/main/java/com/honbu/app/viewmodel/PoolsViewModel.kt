package com.honbu.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.honbu.app.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// ─────────────────────────────────────────────────────────────────────────────

enum class PoolsView    { SETUP, LIST, MATCH }
enum class ScheduleType { CIRCLE, REST_OPTIMIZED, CLUSTERED, RANDOM, RANDOM_REST }

const val RANDOM_PICK = "Random"

data class PoolMatch(
    val id: Int,
    val whitePlayer: String,
    val redPlayer: String,
    // Results populated after submit
    val whiteResults: List<String> = emptyList(),   // e.g. ["M","K"]
    val redResults:   List<String> = emptyList(),
    val submitted: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────

class PoolsViewModel(app: Application) : AndroidViewModel(app) {

    private val memberDao = AppDatabase.getInstance(app).memberDao()
    private val job   = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)

    // Per-match timer states: survive prev/next navigation and are cleared on reset
    private val _matchStates = mutableMapOf<Int, MatchState>()
    private val _poolGeneration = MutableStateFlow(0)
    val poolGeneration: StateFlow<Int> = _poolGeneration

    fun saveMatchState(index: Int, state: MatchState) { _matchStates[index] = state }
    fun loadMatchState(index: Int): MatchState? = _matchStates[index]
    fun clearMatchState(index: Int) { _matchStates.remove(index) }
    private fun clearAllMatchStates() { _matchStates.clear() }

    val members = memberDao.getAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Setup state ──────────────────────────────────────────────────

    private val _selectedMembers = MutableStateFlow<List<String>>(emptyList())
    val selectedMembers: StateFlow<List<String>> = _selectedMembers

    private val _scheduleType = MutableStateFlow(ScheduleType.CIRCLE)
    val scheduleType: StateFlow<ScheduleType> = _scheduleType

    private val _clusterSize = MutableStateFlow(2)
    val clusterSize: StateFlow<Int> = _clusterSize

    private val _firstWhite = MutableStateFlow(RANDOM_PICK)
    val firstWhite: StateFlow<String> = _firstWhite

    private val _firstRed = MutableStateFlow(RANDOM_PICK)
    val firstRed: StateFlow<String> = _firstRed

    private val _poolDurationMs = MutableStateFlow(3 * 60_000L)
    val poolDurationMs: StateFlow<Long> = _poolDurationMs

    // ── Navigation / list state ──────────────────────────────────────

    private val _currentView = MutableStateFlow(PoolsView.SETUP)
    val currentView: StateFlow<PoolsView> = _currentView

    private val _matches = MutableStateFlow<List<PoolMatch>>(emptyList())
    val matches: StateFlow<List<PoolMatch>> = _matches

    private val _currentMatchIndex = MutableStateFlow(0)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex

    // ── Setup actions ────────────────────────────────────────────────

    fun toggleMember(name: String) {
        val current = _selectedMembers.value.toMutableList()
        if (name in current) {
            current.remove(name)
            // Clear first-match picks if they were this person
            if (_firstWhite.value == name) _firstWhite.value = RANDOM_PICK
            if (_firstRed.value   == name) _firstRed.value   = RANDOM_PICK
        } else {
            current.add(name)
        }
        _selectedMembers.value = current
    }

    fun setScheduleType(t: ScheduleType) { _scheduleType.value = t }
    fun setClusterSize(n: Int)           { _clusterSize.value  = n }
    fun setPoolDuration(ms: Long)        { _poolDurationMs.value = ms }

    fun setFirstWhite(name: String) {
        _firstWhite.value = name
        // Avoid duplicate: if red was set to same person, reset it
        if (name != RANDOM_PICK && _firstRed.value == name) _firstRed.value = RANDOM_PICK
    }

    fun setFirstRed(name: String) {
        _firstRed.value = name
        if (name != RANDOM_PICK && _firstWhite.value == name) _firstWhite.value = RANDOM_PICK
    }

    // ── Schedule generation ──────────────────────────────────────────

    fun generateSchedule() {
        val players = _selectedMembers.value
        if (players.size < 2) return

        val n  = players.size
        val fw = _firstWhite.value
        val fr = _firstRed.value

        // Rearrange player index assignments so the desired first-match players
        // land in the positions the algorithm naturally uses for its first match —
        // no post-processing or match-moving required.
        val orderedPlayers: List<String> = when (_scheduleType.value) {
            ScheduleType.CIRCLE ->
                rearrangeForCircle(players, fw, fr)
            ScheduleType.REST_OPTIMIZED,
            ScheduleType.CLUSTERED,
            ScheduleType.RANDOM,
            ScheduleType.RANDOM_REST ->
                rearrangeForRest(players, fw, fr)
        }

        val schedule: List<Pair<Int, Int>> = when (_scheduleType.value) {
            ScheduleType.CIRCLE          -> circleSchedule(n)
            ScheduleType.REST_OPTIMIZED  -> restOptimizedSchedule(n)
            ScheduleType.CLUSTERED       -> engagementScoreSchedule(n, _clusterSize.value)
            ScheduleType.RANDOM          -> randomSchedule(n)
            ScheduleType.RANDOM_REST     -> randomRestSchedule(n)
        }

        _matches.value = enforceConsistentSides(schedule).mapIndexed { idx, (a, b) ->
            PoolMatch(id = idx, whitePlayer = orderedPlayers[a], redPlayer = orderedPlayers[b])
        }
        clearAllMatchStates()
        _poolGeneration.value++
        _currentView.value = PoolsView.LIST
    }

    /**
     * Rearrange the player list so that [fw] lands at index 0 and [fr] at index n-1.
     * Circle and clustered algorithms produce their first match as 0 vs n-1.
     */
    private fun rearrangeForCircle(players: List<String>, fw: String, fr: String): List<String> {
        val p = players.toMutableList()
        val n = p.size
        // Move firstWhite to index 0
        if (fw != RANDOM_PICK) {
            val i = p.indexOf(fw)
            if (i > 0) { val t = p[0]; p[0] = p[i]; p[i] = t }
        }
        // Move firstRed to index n-1 (but not if it ended up at 0)
        if (fr != RANDOM_PICK && p.indexOf(fr) != 0) {
            val i = p.indexOf(fr)
            if (i != n - 1) { val t = p[n - 1]; p[n - 1] = p[i]; p[i] = t }
        }
        return p
    }

    /**
     * Rearrange the player list so that [fw] lands at index 0 and [fr] at index 1.
     * Rest-optimized picks (0, 1) first when all rests are equal (at the start).
     */
    private fun rearrangeForRest(players: List<String>, fw: String, fr: String): List<String> {
        val p = players.toMutableList()
        // Move firstWhite to index 0
        if (fw != RANDOM_PICK) {
            val i = p.indexOf(fw)
            if (i > 0) { val t = p[0]; p[0] = p[i]; p[i] = t }
        }
        // Move firstRed to index 1 (not 0 which belongs to firstWhite)
        if (fr != RANDOM_PICK && p.indexOf(fr) != 0) {
            val i = p.indexOf(fr)
            if (i != 1) { val t = p[1]; p[1] = p[i]; p[i] = t }
        }
        return p
    }

    // ── List actions ─────────────────────────────────────────────────

    fun moveMatchUp(index: Int) {
        if (index <= 0) return
        val list = _matches.value.toMutableList()
        val tmp = list[index]; list[index] = list[index - 1]; list[index - 1] = tmp
        _matches.value = list
    }

    fun moveMatchDown(index: Int) {
        val list = _matches.value.toMutableList()
        if (index >= list.size - 1) return
        val tmp = list[index]; list[index] = list[index + 1]; list[index + 1] = tmp
        _matches.value = list
    }

    fun goToMatch(index: Int) {
        _currentMatchIndex.value = index
        _currentView.value = PoolsView.MATCH
    }

    fun backToList() {
        _currentView.value = PoolsView.LIST
    }

    fun backToSetup() {
        _currentView.value = PoolsView.SETUP
    }

    fun resetPool() {
        _matches.value = emptyList()
        _currentView.value = PoolsView.SETUP
        clearAllMatchStates()
    }

    fun recordResult(index: Int, whitePoints: List<String>, redPoints: List<String>) {
        val list = _matches.value.toMutableList()
        if (index !in list.indices) return
        list[index] = list[index].copy(
            whiteResults = whitePoints,
            redResults   = redPoints,
            submitted    = true
        )
        _matches.value = list
    }

    fun clearResult(index: Int) {
        val list = _matches.value.toMutableList()
        if (index !in list.indices) return
        list[index] = list[index].copy(whiteResults = emptyList(), redResults = emptyList(), submitted = false)
        _matches.value = list
    }

    // ── Scheduling algorithms ────────────────────────────────────────

    /**
     * Generate circle schedule as a list of rounds (each round = one fight per player).
     * Algorithm: fix player 0, rotate others by inserting the last player at position 1.
     * Source: Wikipedia "Round-robin tournament – Scheduling algorithm"
     */
    private fun circleRounds(n: Int): List<List<Pair<Int, Int>>> {
        val size = if (n % 2 == 0) n else n + 1   // add bye slot for odd n
        // For even n: standard layout [0, 1, 2, ..., n-1].
        // For odd n: place the bye (index n) at position 1 so round 1's first pair is
        // (players[0], players[size-1]) = (0, n-1) directly, without the bye absorbing
        // player 0's first match. Without this, the desired first match (0, n-1) would
        // be delayed by (n-1)/2 rounds.
        val players: MutableList<Int> = if (n % 2 == 0) {
            (0 until size).toMutableList()
        } else {
            mutableListOf(0, n).also { it.addAll(1 until n) }
        }
        val rounds = mutableListOf<List<Pair<Int, Int>>>()

        repeat(size - 1) {
            val round = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until size / 2) {
                val a = players[i]; val b = players[size - 1 - i]
                if (a < n && b < n) round.add(a to b)   // skip bye matches
            }
            rounds.add(round)
            // Rotate: remove last element, insert at position 1 (after fixed player 0)
            val last = players.removeAt(size - 1)
            players.add(1, last)
        }
        return rounds
    }

    /** Standard Berger / round-robin circle schedule flattened to sequential match list. */
    private fun circleSchedule(n: Int): List<Pair<Int, Int>> =
        circleRounds(n).flatten()

    /** Greedy: always schedule the pair whose both players have rested the longest. */
    private fun restOptimizedSchedule(n: Int): List<Pair<Int, Int>> {
        val remaining = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until n) for (j in i + 1 until n) remaining.add(i to j)

        val scheduled = mutableListOf<Pair<Int, Int>>()
        val lastSeen  = IntArray(n) { -n }

        while (remaining.isNotEmpty()) {
            val best = remaining.maxByOrNull { (a, b) ->
                minOf(scheduled.size - lastSeen[a], scheduled.size - lastSeen[b])
            }!!
            scheduled.add(best); remaining.remove(best)
            lastSeen[best.first]  = scheduled.size - 1
            lastSeen[best.second] = scheduled.size - 1
        }
        return scheduled
    }

    /**
     * Clustered schedule: groups [clusterSize] rounds together, then outputs
     * each match-position across those rounds consecutively.
     * Result: each positional "slot" fights clusterSize times in a row before rotating.
     *
     * Example (6 players, cluster=2):
     *   Block = rounds 1&2: output pos0 from r1, pos0 from r2, pos1 from r1, pos1 from r2, ...
     *   This gives player-0 two consecutive fights, player-at-pos-1 two consecutive, etc.
     */
    /* private fun clusteredSchedule(n: Int, clusterSize: Int): List<Pair<Int, Int>> {
        val rounds = circleRounds(n)
        val result = mutableListOf<Pair<Int, Int>>()
        var r = 0
        while (r < rounds.size) {
            val block   = rounds.subList(r, minOf(r + clusterSize, rounds.size))
            val maxPos  = block.maxOf { it.size }
            for (pos in 0 until maxPos) {
                for (round in block) {
                    if (pos < round.size) result.add(round[pos])
                }
            }
            r += clusterSize
        }
        return result
    } */

    /**
     * Post-processing pass: whenever the same player appears in two consecutive matches,
     * ensure they stay on the same side (white or red) in both.
     *
     * Rule: if the shared player was White in match i-1 but Red in match i (or vice versa),
     * flip match i's orientation. Matches without a shared player are left unchanged.
     */
    private fun enforceConsistentSides(schedule: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (schedule.size < 2) return schedule
        val result = schedule.toMutableList()
        for (i in 1 until result.size) {
            val (prevA, prevB) = result[i - 1]
            val (curA,  curB)  = result[i]
            // If the staying player switched sides, flip the current match
            if (prevA == curB || prevB == curA) {
                result[i] = curB to curA
            }
            // prevA == curA or prevB == curB → already consistent, no action
        }
        return result
    }

    /** direct translation of the Python generate_chains algorithm.
     *
     * Produces chains where one fighter stays for consecutive matches ("A vs B, B vs C, C vs D…").
     * Chains are sorted longest-first, then any 1-match leftovers are appended at the end.
     * The first chain always starts with (0, 1), so rearrangeForRest() gives correct first-match
     * player placement.
     *
     * For n players, generates all n*(n-1)/2 unique matches.
     */

    /**
     * Engagement-score schedule for chain lengths 2, 3, 4, or 5.
     *
     * Each player has an "engagement score" that grows by 20 every match they play and
     * shrinks by 1 every match they sit out. Within a chain, the next opponent is the
     * lowest-scored player still available to the current captain (ties broken by index).
     *
     * After a chain completes, the last opponent inherits captaincy; their next chain is
     * one match shorter (since the handoff itself is the first match of that streak).
     * If the current captain runs out of opponents mid-flow, the lowest-scored player
     * with remaining matches takes over for a fresh full-length chain.
     */
    private fun engagementScoreSchedule(n: Int, chainLength: Int): List<Pair<Int, Int>> {
        if (n < 2) return emptyList()

        val scores    = IntArray(n)
        val remaining = mutableSetOf<Pair<Int, Int>>()
        for (i in 0 until n) for (j in i + 1 until n) remaining.add(i to j)

        val schedule = mutableListOf<Pair<Int, Int>>()

        fun opponentsOf(p: Int): List<Int> = remaining
            .filter { (a, b) -> a == p || b == p }
            .map { (a, b) -> if (a == p) b else a }
            .sortedWith(compareBy({ scores[it] }, { it }))

        var current   = 0
        var inherited = false

        while (remaining.isNotEmpty()) {
            // Make sure the current captain has at least one opponent left;
            // otherwise hand off to the lowest-scored player with remaining matches.
            if (opponentsOf(current).isEmpty()) {
                val candidate = (0 until n)
                    .sortedWith(compareBy({ scores[it] }, { it }))
                    .firstOrNull { opponentsOf(it).isNotEmpty() } ?: break
                current   = candidate
                inherited = false
            }

            // Inherited streak already counts the handoff match.
            // Guard against zero-length streaks (e.g. chainLength=1 inherited).
            val matchesToPlay = (chainLength - if (inherited) 1 else 0).coerceAtLeast(1)
            var lastOpponent: Int? = null
            var played = 0

            while (played < matchesToPlay) {
                val opps = opponentsOf(current)
                if (opps.isEmpty()) break
                val opponent = opps[0]
                val key = if (current < opponent) current to opponent else opponent to current
                if (key !in remaining) break
                remaining.remove(key)

                // Update engagement scores: +20 for participants, -1 for everyone else.
                // The wide gap (vs e.g. +10/-1) gives sat-out players much higher priority,
                // which produces noticeably tighter chains and cleaner endings.
                for (p in 0 until n) {
                    scores[p] += if (p == current || p == opponent) 20 else -1
                }

                schedule.add(current to opponent)   // current keeps the white side
                lastOpponent = opponent
                played++
            }

            if (lastOpponent != null) {
                current   = lastOpponent
                inherited = true
            } else {
                inherited = false
            }
        }
        return schedule
    }


    /**
     * Fully random schedule: all n*(n-1)/2 matches in shuffled order.
     * The first-match preference (via rearrangeForRest) ensures (0,1) lands first.
     */
    private fun randomSchedule(n: Int): List<Pair<Int, Int>> {
        val all = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until n) for (j in i + 1 until n) all.add(i to j)
        all.shuffle()
        // Move preferred first match (0 vs 1) to front
        val fi = all.indexOfFirst { (a, b) -> a == 0 && b == 1 }
        if (fi > 0) { val t = all[0]; all[0] = all[fi]; all[fi] = t }
        return all
    }

    /**
     * Random schedule with minimum rest: randomises the order but avoids scheduling
     * a player in back-to-back matches whenever an alternative exists.
     * Falls back to the best available match when no fully-rested option exists.
     */
    private fun randomRestSchedule(n: Int): List<Pair<Int, Int>> {
        val all = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until n) for (j in i + 1 until n) all.add(i to j)
        all.shuffle()

        // Always honour first-match preference: (0,1) goes first
        val firstMatch = all.firstOrNull { (a, b) -> a == 0 && b == 1 } ?: all[0]
        val remaining  = all.also { it.remove(firstMatch) }.toMutableList()
        val scheduled  = mutableListOf(firstMatch)
        val lastSeen   = IntArray(n) { -n }
        lastSeen[firstMatch.first]  = 0
        lastSeen[firstMatch.second] = 0

        while (remaining.isNotEmpty()) {
            // Prefer matches where both players have had at least one match of rest
            val eligible = remaining.filter { (a, b) ->
                scheduled.size - lastSeen[a] >= 2 && scheduled.size - lastSeen[b] >= 2
            }
            val pick = if (eligible.isNotEmpty()) {
                eligible.random()
            } else {
                // No fully-rested pair available — pick the one with the most combined rest
                remaining.maxByOrNull { (a, b) ->
                    minOf(scheduled.size - lastSeen[a], scheduled.size - lastSeen[b])
                }!!
            }
            scheduled.add(pick)
            remaining.remove(pick)
            lastSeen[pick.first]  = scheduled.size - 1
            lastSeen[pick.second] = scheduled.size - 1
        }
        return scheduled
    }

    override fun onCleared() {
        job.cancel()
    }
}
