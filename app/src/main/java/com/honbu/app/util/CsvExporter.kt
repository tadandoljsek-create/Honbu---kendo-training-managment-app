package com.honbu.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter

data class MatchRecord(
    val matchStartDate: String,
    val matchStartTime: String,
    val whitePlayer: String,
    val redPlayer: String,
    val whitePoint1Technique: String,
    val whitePoint1Time: String,
    val whitePoint2Technique: String,
    val whitePoint2Time: String,
    val redPoint1Technique: String,
    val redPoint1Time: String,
    val redPoint2Technique: String,
    val redPoint2Time: String,
    val whiteHansoku: Int,
    val redHansoku: Int,
    val winner: String,
    val hadEncho: Boolean,
    val notes: String
)

object CsvExporter {

    private const val FILE_NAME = "honbu_matches.csv"

    private val HEADERS = listOf(
        "match_date", "match_time",
        "white_player", "red_player",
        "white_p1_tech", "white_p1_time",
        "white_p2_tech", "white_p2_time",
        "red_p1_tech",   "red_p1_time",
        "red_p2_tech",   "red_p2_time",
        "white_hansoku", "red_hansoku",
        "winner", "had_encho", "notes"
    )

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * Export a match record.
     *
     * If [persistedFileUri] is non-empty, writes to that exact file (user-chosen via
     * Settings → "Save As" or "Pick existing"). The file is created fresh with headers
     * if empty, otherwise rows are appended.
     *
     * If [persistedFileUri] is empty, falls back to the default MediaStore Downloads
     * location (Android 10+) or the legacy Downloads folder (<Android 10).
     * When the file is first created via MediaStore, [onFileUriCreated] is called with
     * the new URI so the caller can persist it and avoid the "(1)" duplicate bug on
     * subsequent app launches.
     */
    private fun buildRow(r: MatchRecord): String = listOf(
        cell(r.matchStartDate), cell(r.matchStartTime),
        cell(r.whitePlayer),    cell(r.redPlayer),
        cell(r.whitePoint1Technique), cell(r.whitePoint1Time),
        cell(r.whitePoint2Technique), cell(r.whitePoint2Time),
        cell(r.redPoint1Technique),   cell(r.redPoint1Time),
        cell(r.redPoint2Technique),   cell(r.redPoint2Time),
        r.whiteHansoku.toString(), r.redHansoku.toString(),
        cell(r.winner), r.hadEncho.toString(), cell(r.notes)
    ).joinToString(",")

    private fun cell(v: String) = "\"${v.replace("\"", "\"\"")}\""

    fun export(
        context: Context,
        record: MatchRecord,
        persistedFileUri: String = "",
        onFileUriCreated: (String) -> Unit = {},
    ): Result<String> = runCatching {
        val row = buildRow(record)
        if (persistedFileUri.isNotEmpty()) {
            exportToUri(context, row, Uri.parse(persistedFileUri))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportMediaStore(context, row, onFileUriCreated)
        } else {
            exportLegacy(row)
        }
    }.flatten()

    // ── Write to a specific URI (user-chosen or MediaStore-persisted) ──

    private fun exportToUri(context: Context, row: String, uri: Uri): Result<String> =
        runCatching {
            val resolver = context.contentResolver

            // Check if the file has content already; if empty write BOM + headers first
            val isEmpty = try {
                resolver.openFileDescriptor(uri, "r")?.use { it.statSize == 0L } ?: true
            } catch (_: Exception) { true }

            if (isEmpty) {
                resolver.openOutputStream(uri, "w")?.use { stream ->
                    stream.write(BOM)
                    OutputStreamWriter(stream, Charsets.UTF_8).use {
                        it.write(HEADERS.joinToString(",") + "\n")
                        it.write(row + "\n")
                    }
                }
            } else {
                resolver.openOutputStream(uri, "wa")?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(row + "\n") }
                }
            }

            // Extract a readable filename for the success message
            val displayName = try {
                resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Exception) { null } ?: "custom file"

            "Saved to $displayName"
        }

    // ── MediaStore Downloads (API 29+) — persists URI to avoid "(1)" duplicates ──

    private fun exportMediaStore(
        context: Context,
        row: String,
        onFileUriCreated: (String) -> Unit,
    ): Result<String> = runCatching {
        val resolver   = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val newUri = resolver.insert(collection, values)
            ?: error("Could not create CSV file in Downloads")

        resolver.openOutputStream(newUri)?.use { stream ->
            stream.write(BOM)
            OutputStreamWriter(stream, Charsets.UTF_8).use {
                it.write(HEADERS.joinToString(",") + "\n")
                it.write(row + "\n")
            }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(newUri, values, null, null)

        onFileUriCreated(newUri.toString())
        "Saved to Downloads/$FILE_NAME"
    }

    // ── Legacy (<API 29) ──────────────────────────────────────────────

    private fun exportLegacy(row: String): Result<String> = runCatching {
        val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, FILE_NAME)
        if (!file.exists()) {
            file.createNewFile()
            file.outputStream().use { s ->
                s.write(BOM)
                s.writer(Charsets.UTF_8).use { it.write(HEADERS.joinToString(",") + "\n") }
            }
        }
        FileWriter(file, true).use { it.write(row + "\n") }
        "Saved to Downloads/$FILE_NAME"
    }
}

private fun <T> Result<Result<T>>.flatten(): Result<T> =
    getOrElse { return Result.failure(it) }
