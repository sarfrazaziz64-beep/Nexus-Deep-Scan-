package com.nexus.deeprecovery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object FileOps {

    private const val FOLDER = "NexusRecovery"

    private fun targetName(item: VideoItem): String {
        var base = item.name.trimStart('.').ifBlank { "video" }
        val ext = if (base.contains('.')) base.substringAfterLast('.').lowercase() else ""
        if (ext.isEmpty() || Format.mimeFromName(base) == null) {
            base = "$base.${Format.extensionFor(item.mime)}"
        }
        return "Recovered_$base"
    }

    /**
     * Copies the selected file into Movies/NexusRecovery (or Pictures/NexusRecovery for thumbnails).
     * The original file is only read, never changed. A partial copy is deleted on failure or cancel.
     * Returns the saved location for display.
     */
    suspend fun copyToRecovery(context: Context, item: VideoItem): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val isImage = item.mime.startsWith("image/")
        val name = targetName(item)
        val publicDir = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isImage) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$publicDir/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val out = resolver.insert(collection, values)
                ?: throw IllegalStateException("Could not create the recovery file")
            try {
                val input = resolver.openInputStream(item.uri)
                    ?: throw IllegalStateException("Source file cannot be read")
                val output = resolver.openOutputStream(out)
                    ?: throw IllegalStateException("Recovery file cannot be written")
                copy(input, output)
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(out, done, null, null)
            } catch (t: Throwable) {
                try { resolver.delete(out, null, null) } catch (_: Exception) { }
                throw t
            }
            "$publicDir/$FOLDER/$name"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(publicDir), FOLDER)
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Cannot create ${dir.path}")
            var target = File(dir, name)
            var n = 1
            while (target.exists()) {
                target = File(dir, name.substringBeforeLast('.') + "_" + n + "." + name.substringAfterLast('.'))
                n++
            }
            try {
                val input = resolver.openInputStream(item.uri)
                    ?: throw IllegalStateException("Source file cannot be read")
                copy(input, FileOutputStream(target))
            } catch (t: Throwable) {
                target.delete()
                throw t
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
            target.absolutePath
        }
    }

    private suspend fun copy(input: InputStream, output: OutputStream) = withContext(Dispatchers.IO) {
        input.use { i ->
            output.use { o ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    ensureActive()
                    val n = i.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                }
                o.flush()
            }
        }
    }

    /** Small preview picture for the result list. Returns null if the file cannot be decoded. */
    fun thumbnail(context: Context, item: VideoItem): Bitmap? {
        try {
            if (item.mime.startsWith("image/")) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = context.contentResolver.openInputStream(item.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return null
                return ThumbnailUtils.extractThumbnail(bmp, 240, 180)
            }
            if (item.fromMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    return context.contentResolver.loadThumbnail(item.uri, Size(240, 180), null)
                } catch (_: Exception) {
                    // fall through to frame extraction
                }
            }
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, item.uri)
                val frame = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: r.frameAtTime
                    ?: return null
                return ThumbnailUtils.extractThumbnail(frame, 240, 180)
            } finally {
                try { r.release() } catch (_: Exception) { }
            }
        } catch (t: Throwable) {
            return null
        }
    }
}
