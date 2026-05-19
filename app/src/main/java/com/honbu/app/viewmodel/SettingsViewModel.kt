package com.honbu.app.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.honbu.app.HonbuApplication
import com.honbu.app.data.db.AppDatabase
import com.honbu.app.data.db.Member
import com.honbu.app.data.preferences.SoundConfig
import com.honbu.app.data.preferences.SoundType
import com.honbu.app.util.SoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val soundPrefs  = (app as HonbuApplication).soundPreferences
    private val memberDao   = AppDatabase.getInstance(app).memberDao()
    private val soundMgr    = SoundManager(app)
    private val appContext  = app.applicationContext

    val soundConfig = soundPrefs.soundConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SoundConfig())

    val members = memberDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult

    // ── Sound ────────────────────────────────────────────────────────

    fun setTimerEndSound(t: SoundType)       = viewModelScope.launch { soundPrefs.setTimerEndSound(t) }
    fun setIntervalChangeSound(t: SoundType) = viewModelScope.launch { soundPrefs.setIntervalChangeSound(t) }
    fun setIntervalStartSound(t: SoundType)  = viewModelScope.launch { soundPrefs.setIntervalStartSound(t) }

    fun setCustomUri(slot: SoundSlot, uri: Uri) = viewModelScope.launch {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        val s = uri.toString()
        when (slot) {
            SoundSlot.TIMER_END       -> { soundPrefs.setTimerEndSound(SoundType.CUSTOM);       soundPrefs.setCustomTimerEndUri(s) }
            SoundSlot.INTERVAL_CHANGE -> { soundPrefs.setIntervalChangeSound(SoundType.CUSTOM); soundPrefs.setCustomIntervalChangeUri(s) }
            SoundSlot.INTERVAL_START  -> { soundPrefs.setIntervalStartSound(SoundType.CUSTOM);  soundPrefs.setCustomIntervalStartUri(s) }
        }
    }

    fun previewSound(slot: SoundSlot) {
        val cfg = soundConfig.value
        when (slot) {
            SoundSlot.TIMER_END       -> soundMgr.playTimerEnd(cfg)
            SoundSlot.INTERVAL_CHANGE -> soundMgr.playIntervalChange(cfg)
            SoundSlot.INTERVAL_START  -> soundMgr.playIntervalStart(cfg)
        }
    }

    // ── CSV save file ("Save As") ────────────────────────────────────

    /** User picked a new file via CreateDocument (Save As). Clears the file so headers are written fresh. */
    fun createNewCsvFile(uri: Uri) = viewModelScope.launch {
        persistUri(uri)
        try { appContext.contentResolver.openOutputStream(uri, "w")?.use { it.write(byteArrayOf()) } }
        catch (_: Exception) {}
        soundPrefs.setCsvFileUri(uri.toString())
    }

    /** User picked an existing CSV via OpenDocument (append mode). */
    fun useExistingCsvFile(uri: Uri) = viewModelScope.launch {
        persistUri(uri)
        soundPrefs.setCsvFileUri(uri.toString())
    }

    /** Reset to default Downloads/honbu_matches.csv. */
    fun clearCustomCsvFile() = viewModelScope.launch { soundPrefs.setCsvFileUri("") }

    private fun persistUri(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    // ── Members ──────────────────────────────────────────────────────

    fun addMember(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) memberDao.insert(Member(name = name.trim()))
    }

    fun deleteMember(member: Member) = viewModelScope.launch { memberDao.delete(member) }

    /**
     * Import members from a plain-text file with one name per line.
     * Handles UTF-8 and Windows-1250 (CP1250) — the latter is common for
     * files created on Slovenian/Croatian Windows systems.
     * Skips blank lines and deduplicates against existing members (case-insensitive).
     */
    fun importMembersFromCsv(uri: Uri) = viewModelScope.launch {
        runCatching {
            val bytes = appContext.contentResolver.openInputStream(uri)
                ?.readBytes() ?: error("Could not open file")

            val text = decodeBytes(bytes)

            val existingNames = members.value
                .map { it.name.trim().lowercase() }
                .toHashSet()

            val newNames = text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { it.lowercase() !in existingNames }
                .distinct()

            newNames.forEach { name ->
                memberDao.insert(Member(name = name))
                existingNames.add(name.lowercase())
            }

            _importResult.value = if (newNames.isEmpty())
                "No new members to import (all already exist)."
            else
                "Imported ${newNames.size} member${if (newNames.size == 1) "" else "s"}."

        }.onFailure { _importResult.value = "Import failed: ${it.message}" }
    }

    fun clearImportResult() { _importResult.value = null }

    // ── Encoding helpers ─────────────────────────────────────────────

    private fun decodeBytes(bytes: ByteArray): String {
        // Strip UTF-8 BOM if present
        val data = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes

        // Try UTF-8 first
        val utf8 = data.toString(Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8

        // Manually decode CP1250 (Windows-1250), which Android may not ship as a named charset.
        // This covers Slovenian/Croatian characters: Č č Ć ć Š š Ž ž Đ đ ...
        return buildString(data.size) {
            for (b in data) {
                val i = b.toInt() and 0xFF
                append(if (i < 0x80) i.toChar() else CP1250[i - 0x80])
            }
        }
    }

    companion object {
        /**
         * Complete Windows-1250 (CP1250) decoding table for bytes 0x80–0xFF.
         * Key Slovenian characters: Č=0xC8, č=0xE8, Š=0x8A, š=0x9A, Ž=0x8E, ž=0x9E
         * Key Croatian characters: Ć=0xC6, ć=0xE6, Đ=0xD0, đ=0xF0
         */
        private val CP1250 = charArrayOf(
            // 0x80–0x8F
            '\u20AC', '\uFFFD', '\u201A', '\uFFFD', '\u201E', '\u2026', '\u2020', '\u2021',
            '\uFFFD', '\u2030', '\u0160', '\u2039', '\u015A', '\u0164', '\u017D', '\u0179',
            // 0x90–0x9F
            '\uFFFD', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014',
            '\uFFFD', '\u2122', '\u0161', '\u203A', '\u015B', '\u0165', '\u017E', '\u017A',
            // 0xA0–0xAF
            '\u00A0', '\u02C7', '\u02D8', '\u0141', '\u00A4', '\u0104', '\u00A6', '\u00A7',
            '\u00A8', '\u00A9', '\u015E', '\u00AB', '\u00AC', '\u00AD', '\u00AE', '\u017B',
            // 0xB0–0xBF
            '\u00B0', '\u00B1', '\u02DB', '\u0142', '\u00B4', '\u00B5', '\u00B6', '\u00B7',
            '\u00B8', '\u0105', '\u015F', '\u00BB', '\u013D', '\u02DD', '\u013E', '\u017C',
            // 0xC0–0xCF  (Č=0xC8, Ć=0xC6)
            '\u0154', '\u00C1', '\u00C2', '\u0102', '\u00C4', '\u0139', '\u0106', '\u00C7',
            '\u010C', '\u00C9', '\u0118', '\u00CB', '\u011A', '\u00CD', '\u00CE', '\u010E',
            // 0xD0–0xDF  (Đ=0xD0)
            '\u0110', '\u0143', '\u0147', '\u00D3', '\u00D4', '\u0150', '\u00D6', '\u00D7',
            '\u0158', '\u016E', '\u00DA', '\u0170', '\u00DC', '\u00DD', '\u0162', '\u00DF',
            // 0xE0–0xEF  (č=0xE8, ć=0xE6)
            '\u0155', '\u00E1', '\u00E2', '\u0103', '\u00E4', '\u013A', '\u0107', '\u00E7',
            '\u010D', '\u00E9', '\u0119', '\u00EB', '\u011B', '\u00ED', '\u00EE', '\u010F',
            // 0xF0–0xFF  (đ=0xF0)
            '\u0111', '\u0144', '\u0148', '\u00F3', '\u00F4', '\u0151', '\u00F6', '\u00F7',
            '\u0159', '\u016F', '\u00FA', '\u0171', '\u00FC', '\u00FD', '\u0163', '\u02D9'
        )
    }

    override fun onCleared() { soundMgr.release() }
}

enum class SoundSlot { TIMER_END, INTERVAL_CHANGE, INTERVAL_START }
