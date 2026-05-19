package com.honbu.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.soundDataStore by preferencesDataStore(name = "sound_prefs")

enum class SoundType {
    BEEP,           // sound_beep.wav  — default timer end
    WHISTLE,        // whistle.mp3
    GONG,           // gong.mp3
    START_PISTOL,   // start_pistol.mp3
    YAME,           // yame.mp3        — default interval change
    TIME,           // time.mp3
    AIR_HORN,       // air_horn.mp3
    BELL,           // bell.mp3
    SHIP_HORN,      // ship_horn.mp3
    HAJIME,         // hajime.mp3      — default interval start
    CUSTOM,
    NONE,
}

data class SoundConfig(
    val timerEndSound: SoundType       = SoundType.BEEP,
    val intervalChangeSound: SoundType = SoundType.YAME,
    val intervalStartSound: SoundType  = SoundType.HAJIME,
    val customTimerEndUri: String      = "",
    val customIntervalChangeUri: String = "",
    val customIntervalStartUri: String  = "",
    // csvDirUri removed — superseded by csvFileUri (see CsvExporter)
    val csvFileUri: String              = "",  // URI of the file to append results to
)

class SoundPreferences(private val context: Context) {

    private val TIMER_END         = stringPreferencesKey("timer_end")
    private val INTERVAL_CHANGE   = stringPreferencesKey("interval_change")
    private val INTERVAL_START    = stringPreferencesKey("interval_start")
    private val CUSTOM_TIMER_URI  = stringPreferencesKey("custom_timer_uri")
    private val CUSTOM_CHANGE_URI = stringPreferencesKey("custom_change_uri")
    private val CUSTOM_START_URI  = stringPreferencesKey("custom_start_uri")
    private val CSV_DIR_URI       = stringPreferencesKey("csv_dir_uri")
    private val CSV_FILE_URI      = stringPreferencesKey("csv_file_uri")  // persisted after first creation

    val soundConfig: Flow<SoundConfig> = context.soundDataStore.data.map { p ->
        SoundConfig(
            timerEndSound           = parse(p[TIMER_END],        SoundType.BEEP),
            intervalChangeSound     = parse(p[INTERVAL_CHANGE],  SoundType.YAME),
            intervalStartSound      = parse(p[INTERVAL_START],   SoundType.HAJIME),
            customTimerEndUri       = p[CUSTOM_TIMER_URI]  ?: "",
            customIntervalChangeUri = p[CUSTOM_CHANGE_URI] ?: "",
            customIntervalStartUri  = p[CUSTOM_START_URI]  ?: "",
            // csvDirUri no longer in SoundConfig (key kept in DataStore for compat)
            csvFileUri              = p[CSV_FILE_URI]      ?: "",
        )
    }

    suspend fun setTimerEndSound(t: SoundType)       { context.soundDataStore.edit { it[TIMER_END]       = t.name } }
    suspend fun setIntervalChangeSound(t: SoundType) { context.soundDataStore.edit { it[INTERVAL_CHANGE] = t.name } }
    suspend fun setIntervalStartSound(t: SoundType)  { context.soundDataStore.edit { it[INTERVAL_START]  = t.name } }

    suspend fun setCustomTimerEndUri(uri: String)       { context.soundDataStore.edit { it[CUSTOM_TIMER_URI]  = uri } }
    suspend fun setCustomIntervalChangeUri(uri: String) { context.soundDataStore.edit { it[CUSTOM_CHANGE_URI] = uri } }
    suspend fun setCustomIntervalStartUri(uri: String)  { context.soundDataStore.edit { it[CUSTOM_START_URI]  = uri } }
    suspend fun setCsvDirUri(uri: String)               { context.soundDataStore.edit { it[CSV_DIR_URI]       = uri } }
    suspend fun setCsvFileUri(uri: String)              { context.soundDataStore.edit { it[CSV_FILE_URI]      = uri } }
    suspend fun clearCsvFileUri()                       { context.soundDataStore.edit { it.remove(CSV_FILE_URI) } }

    private fun parse(value: String?, default: SoundType) =
        value?.let { runCatching { SoundType.valueOf(it) }.getOrNull() } ?: default
}
