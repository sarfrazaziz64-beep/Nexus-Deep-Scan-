package com.nexus.deeprecovery

import java.io.InputStream

/**
 * Decides whether a file is a video (normal, hidden, trashed, unlabeled) or a leftover thumbnail.
 * Shared by the SD-card scanner and the whole-phone deep scanner. Read-only.
 */
object Classifier {

    private val videoExts = setOf(
        "mp4", "m4v", "3gp", "3gpp", "mkv", "webm", "avi", "mov", "ts", "flv", "wmv", "mpg", "mpeg"
    )
    private val imageExts = setOf("jpg", "jpeg", "png", "webp")
    private val suspectExts = setOf("tmp", "bin", "dat", "cache", "chk", "exo", "part", "0", "1")

    /**
     * @param path folder path ending with "/"
     * @param open opens the file for reading (only the first 16 bytes are read)
     */
    fun classify(
        name: String,
        mime: String,
        path: String,
        size: Long,
        open: () -> InputStream?
    ): Pair<ItemKind, String>? {
        val lower = name.lowercase()
        val ext = if (lower.contains('.')) lower.substringAfterLast('.') else ""
        val inThumbFolder = path.contains("/.thumbnails/", ignoreCase = true)
        val inBin = RecycleBins.matches(path.lowercase())

        if (inThumbFolder && (mime.startsWith("image/") || ext in imageExts)) {
            return ItemKind.THUMBNAIL to (Format.mimeFromName(name) ?: "image/jpeg")
        }

        val looksVideo = mime.startsWith("video/") || ext in videoExts
        if (looksVideo) {
            val kind = when {
                lower.startsWith(".trashed-") -> ItemKind.TRASHED
                inBin -> ItemKind.RECYCLE_BIN
                lower.startsWith(".") || path.contains("/.") -> ItemKind.HIDDEN
                else -> ItemKind.VIDEO
            }
            val realMime = if (mime.startsWith("video/")) mime else (Format.mimeFromName(name) ?: "video/mp4")
            return kind to realMime
        }

        // No extension / odd extension / app cache chunk: check the real file signature.
        val unlabeled = ext.isEmpty() || ext.length > 5 || ext in suspectExts ||
            mime == "application/octet-stream" || mime.isEmpty()
        if (unlabeled && (size >= 64 * 1024 || (inBin && size >= 8 * 1024))) {
            val sig = sniff(open)
            if (sig != null) return (if (inBin) ItemKind.RECYCLE_BIN else ItemKind.SIGNATURE) to sig
        }
        return null
    }

    private fun sniff(open: () -> InputStream?): String? {
        return try {
            open()?.use { input ->
                val h = ByteArray(16)
                var read = 0
                while (read < h.size) {
                    val n = input.read(h, read, h.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < 12) {
                    null
                } else {
                    val box = String(h, 4, 4, Charsets.US_ASCII)
                    val brand = String(h, 8, 4, Charsets.US_ASCII).lowercase()
                    val head = String(h, 0, 4, Charsets.US_ASCII)
                    when {
                        box == "ftyp" -> when {
                            brand.startsWith("hei") || brand.startsWith("mif") ||
                                brand.startsWith("msf") || brand.startsWith("avi") -> null // HEIC/AVIF photos
                            brand.startsWith("3g") -> "video/3gpp"
                            brand.startsWith("qt") -> "video/quicktime"
                            else -> "video/mp4"
                        }
                        box == "moov" || box == "mdat" || box == "wide" -> "video/mp4"
                        h[0] == 0x1A.toByte() && h[1] == 0x45.toByte() &&
                            h[2] == 0xDF.toByte() && h[3] == 0xA3.toByte() -> "video/x-matroska"
                        head == "RIFF" && String(h, 8, 4, Charsets.US_ASCII) == "AVI " -> "video/x-msvideo"
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
