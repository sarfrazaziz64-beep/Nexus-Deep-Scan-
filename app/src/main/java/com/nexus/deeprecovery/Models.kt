package com.nexus.deeprecovery

import android.net.Uri
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** What kind of finding a result is. Only real, readable files are ever listed. */
enum class ItemKind { VIDEO, TRASHED, HIDDEN, SIGNATURE, THUMBNAIL }

data class VideoItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateMillis: Long,
    val location: String,
    val durationMs: Long,
    val mime: String,
    val kind: ItemKind,
    val fromMediaStore: Boolean
)

object Format {
    fun size(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024
            i++
        }
        return if (i == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[i])
    }

    fun date(millis: Long): String =
        if (millis <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun duration(ms: Long): String {
        if (ms <= 0) return ""
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }

    private val trashedPrefix = Regex("^\\.(trashed|pending)-\\d+-")

    /** Android 11+ renames trashed files to ".trashed-<time>-name.mp4". Show the original name. */
    fun cleanName(raw: String): String = raw.replace(trashedPrefix, "")

    fun extensionFor(mime: String): String = when (mime) {
        "video/3gpp" -> "3gp"
        "video/x-matroska" -> "mkv"
        "video/webm" -> "webm"
        "video/x-msvideo" -> "avi"
        "video/quicktime" -> "mov"
        "image/jpeg" -> "jpg"
        else -> "mp4"
    }

    fun mimeFromName(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "3gp", "3gpp" -> "video/3gpp"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> null
    }
}

data class ScanProgress(val scanned: Int, val found: Int)
data class ScanResult(val items: List<VideoItem>, val scanned: Int)
