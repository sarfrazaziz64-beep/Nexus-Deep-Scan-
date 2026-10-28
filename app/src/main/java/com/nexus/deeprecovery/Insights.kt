package com.nexus.deeprecovery

import java.io.File
import java.io.RandomAccessFile

/** Works out which app or place a file came from, using its folder path. */
object SourceDetector {
    fun fromPath(fullPath: String): String {
        val p = fullPath.lowercase()
        return when {
            RecycleBins.matches(p) -> "Recycle bin"
            "whatsapp business" in p || "com.whatsapp.w4b" in p -> "WhatsApp Business"
            "whatsapp" in p -> "WhatsApp"
            "telegram" in p || "org.telegram" in p -> "Telegram"
            "instagram" in p -> "Instagram"
            "snapchat" in p -> "Snapchat"
            "tiktok" in p || "musically" in p || "com.zhiliaoapp" in p || "com.ss.android.ugc" in p -> "TikTok"
            "messenger" in p || "com.facebook.orca" in p -> "Messenger"
            "facebook" in p || "com.facebook" in p -> "Facebook"
            "imo" in p.split('/') || "com.imo.android" in p -> "IMO"
            "likee" in p || "video.like" in p -> "Likee"
            "youtube" in p -> "YouTube"
            "/.thumbnails" in p -> "Gallery thumbnails"
            "screenrecord" in p || "screen record" in p || "screenrecorder" in p -> "Screen recording"
            "/dcim/camera" in p || "/dcim/100" in p || "/dcim/video" in p -> "Camera"
            "bluetooth" in p -> "Bluetooth"
            "shareit" in p || "xender" in p -> "File sharing app"
            "/download" in p -> "Downloads"
            "/movies" in p -> "Movies"
            "/dcim" in p -> "Gallery"
            else -> ""
        }
    }
}

/**
 * Folders where phone makers' gallery apps keep their own "Recently deleted" bin.
 * Samsung: Android/data/com.sec.android.gallery3d/files/.Trash (readable on Android 10 and older)
 * Xiaomi:  MIUI/Gallery/cloud/.trashBin
 * Huawei/Honor: Pictures/.Gallery2/recycle
 * Oppo/Realme/OnePlus/Vivo: hidden .recycle / .trash / RecentlyDeleted folders under DCIM or Android/media
 */
object RecycleBins {
    private val markers = listOf(
        "/.trash", "trashbin", "/.recycle", "recyclebin", "recycle_bin", "/recycle/",
        "recentlydeleted", "recently deleted", "/.deleted", "/.dtrash", "/.gallerytrash"
    )

    fun matches(lowerPath: String): Boolean = markers.any { it in lowerPath }
}

/**
 * Checks the structure of an MP4 / 3GP / MOV file without decoding it.
 * PLAYABLE = has its index ("moov") and media data is complete.
 * BROKEN   = video data present but the index is missing or the file is cut short.
 */
object Mp4Inspector {

    fun isMp4Family(mime: String) =
        mime == "video/mp4" || mime == "video/3gpp" || mime == "video/quicktime"

    fun health(file: File): Health {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                var pos = 0L
                var hasMoov = false
                var hasMdat = false
                var truncated = false
                var boxes = 0
                val head = ByteArray(16)
                while (pos + 8 <= len && boxes < 1000) {
                    raf.seek(pos)
                    raf.readFully(head, 0, 8)
                    var size = u32(head, 0)
                    val type = String(head, 4, 4, Charsets.US_ASCII)
                    var header = 8L
                    if (size == 1L) {
                        if (pos + 16 > len) { truncated = true; break }
                        raf.readFully(head, 8, 8)
                        size = (u32(head, 8) shl 32) or u32(head, 12)
                        header = 16L
                    } else if (size == 0L) {
                        size = len - pos
                    }
                    if (size < header || !type.all { it in ' '..'~' }) { truncated = true; break }
                    when (type) {
                        "moov" -> hasMoov = true
                        "mdat" -> hasMdat = true
                    }
                    if (pos + size > len) { truncated = true; break }
                    pos += size
                    boxes++
                }
                when {
                    hasMoov && !truncated -> Health.PLAYABLE
                    hasMoov && truncated && !hasMdat -> Health.BROKEN
                    hasMoov -> Health.BROKEN
                    hasMdat -> Health.BROKEN
                    else -> Health.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Health.UNKNOWN
        }
    }

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)
}
