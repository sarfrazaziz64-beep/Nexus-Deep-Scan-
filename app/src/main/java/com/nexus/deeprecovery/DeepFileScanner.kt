package com.nexus.deeprecovery

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Whole-phone deep scan. Walks every folder of internal shared storage and every
 * SD card / USB volume, file by file, including hidden folders, ".trashed" files,
 * app media and cache folders (WhatsApp, Telegram, etc.), ".thumbnails", and files
 * whose name or extension was changed (checked by video signature).
 *
 * Needs "All files access" on Android 11+ (READ_EXTERNAL_STORAGE on older phones).
 * Read-only. It finds every video file that still exists on the phone.
 * It cannot reach sectors that Android has already erased or that are encrypted.
 */
class DeepFileScanner(private val context: Context) {

    fun roots(): List<File> {
        val set = LinkedHashSet<String>()
        @Suppress("DEPRECATION")
        set.add(Environment.getExternalStorageDirectory().absolutePath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sm = context.getSystemService(StorageManager::class.java)
            sm?.storageVolumes?.forEach { v -> v.directory?.let { set.add(it.absolutePath) } }
        } else {
            context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                val p = dir.absolutePath
                val i = p.indexOf("/Android/")
                if (i > 0) set.add(p.substring(0, i))
            }
        }
        return set.map { File(it) }.filter { it.exists() && it.canRead() }
    }

    suspend fun scan(onProgress: (ScanProgress) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val results = ArrayList<VideoItem>()
        var scanned = 0
        for (root in roots()) {
            val skip = setOf(
                File(root, "Android/data").absolutePath,
                File(root, "Android/obb").absolutePath
            )
            val stack = ArrayDeque<Pair<File, Int>>()
            stack.addLast(root to 0)
            while (stack.isNotEmpty()) {
                ensureActive()
                val (dir, depth) = stack.removeLast()
                if (depth > 40 || dir.absolutePath in skip) continue
                val children = try { dir.listFiles() } catch (e: SecurityException) { null } ?: continue
                for (f in children) {
                    ensureActive()
                    if (f.isDirectory) {
                        stack.addLast(f to depth + 1)
                        continue
                    }
                    scanned++
                    val name = f.name
                    val path = (f.parent ?: "") + "/"
                    val size = f.length()
                    val found = Classifier.classify(name, Format.mimeFromName(name) ?: "", path, size) {
                        FileInputStream(f)
                    }
                    if (found != null) {
                        results.add(
                            VideoItem(
                                uri = Uri.fromFile(f),
                                name = Format.cleanName(name),
                                size = size,
                                dateMillis = f.lastModified(),
                                location = f.parent ?: "",
                                durationMs = 0L,
                                mime = found.second,
                                kind = found.first,
                                fromMediaStore = false
                            )
                        )
                    }
                    if (scanned % 200 == 0) onProgress(ScanProgress(scanned, results.size))
                }
            }
        }
        onProgress(ScanProgress(scanned, results.size))
        ScanResult(results, scanned)
    }
}
