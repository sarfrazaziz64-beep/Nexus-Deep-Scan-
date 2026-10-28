package com.nexus.deeprecovery

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Old Android galleries keep thumbnails of every photo and video in big cache files
 * (for example DCIM/.thumbnails/.thumbdata3-..., .thumbdata4-...). These thumbnails
 * often survive long after the video itself was deleted. This carves every JPEG
 * picture out of such a file, so you can at least see what the old video showed.
 * Output goes to the app's private cache; the cache file itself is only read.
 */
object ThumbCarver {

    private const val MAX_JPEG = 3 * 1024 * 1024
    private const val MIN_JPEG = 2 * 1024
    private const val MAX_SOURCE = 400L * 1024 * 1024

    fun isThumbCache(name: String, path: String, size: Long): Boolean {
        val n = name.lowercase()
        if (n.startsWith(".thumbdata")) return true
        if (size < 256 * 1024 || size > MAX_SOURCE) return false
        val ext = if (n.contains('.')) n.substringAfterLast('.') else ""
        val inThumbs = path.contains("/.thumbnails/", ignoreCase = true)
        return inThumbs && ext !in setOf("jpg", "jpeg", "png", "webp") &&
            (n.contains("thumb") || n.contains("cache") || ext.isEmpty() || ext.length > 5)
    }

    fun outDir(context: Context): File = File(context.cacheDir, "carved")

    fun clear(context: Context) {
        outDir(context).deleteRecursively()
    }

    /** Returns one THUMBNAIL item per picture found. */
    fun carve(
        context: Context,
        sourceName: String,
        location: String,
        dateMillis: Long,
        open: () -> InputStream?,
        isActive: () -> Boolean
    ): List<VideoItem> {
        val results = ArrayList<VideoItem>()
        val dir = File(outDir(context), sourceName.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_" + location.hashCode())
        dir.mkdirs()
        try {
            open()?.use { raw ->
                val input = BufferedInputStream(raw, 256 * 1024)
                var prev2 = -1
                var prev1 = -1
                var current: ByteArrayOutputStream? = null
                var count = 0
                var counter = 0L
                while (true) {
                    val b = input.read()
                    if (b < 0) break
                    counter++
                    if ((counter and 0xFFFFF) == 0L && !isActive()) break
                    val cur = current
                    if (cur == null) {
                        if (prev2 == 0xFF && prev1 == 0xD8 && b == 0xFF) {
                            val start = ByteArrayOutputStream(64 * 1024)
                            start.write(0xFF); start.write(0xD8); start.write(0xFF)
                            current = start
                        }
                    } else {
                        cur.write(b)
                        if (prev1 == 0xFF && b == 0xD9) {
                            if (cur.size() >= MIN_JPEG) {
                                count++
                                val f = File(dir, "thumb_" + count + ".jpg")
                                f.writeBytes(cur.toByteArray())
                                results.add(
                                    VideoItem(
                                        uri = Uri.fromFile(f),
                                        name = "$sourceName #$count",
                                        size = f.length(),
                                        dateMillis = dateMillis,
                                        location = location,
                                        durationMs = 0L,
                                        mime = "image/jpeg",
                                        kind = ItemKind.THUMBNAIL,
                                        fromMediaStore = false,
                                        source = "Old thumbnail cache"
                                    )
                                )
                            }
                            current = null
                        } else if (cur.size() > MAX_JPEG) {
                            current = null
                        }
                    }
                    prev2 = prev1
                    prev1 = b
                }
            }
        } catch (e: Exception) {
            // unreadable cache: keep what was carved so far
        }
        return results
    }
}
