package com.nexus.deeprecovery

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Read-only scan of a folder or SD card picked with the system file picker
 * (Storage Access Framework). Files are only opened for reading.
 * Android gives apps no raw-sector access, so erased (overwritten) data is out of reach.
 */
class SafScanner(private val context: Context) {

    suspend fun scan(treeUri: Uri, onProgress: (ScanProgress) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        ThumbCarver.clear(context)
        val results = ArrayList<VideoItem>()
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(DocumentsContract.getTreeDocumentId(treeUri) to "/")
        var scanned = 0
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )

        while (stack.isNotEmpty()) {
            ensureActive()
            val (parentId, path) = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            try {
                resolver.query(childrenUri, columns, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        ensureActive()
                        val docId = c.getString(0) ?: ""
                        val name = c.getString(1) ?: "unknown"
                        val mime = c.getString(2) ?: ""
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        val modified = if (c.isNull(4)) 0L else c.getLong(4)

                        if (mime == Document.MIME_TYPE_DIR) {
                            stack.addLast(docId to "$path$name/")
                        } else if (docId.isNotEmpty()) {
                            scanned++
                            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            if (ThumbCarver.isThumbCache(name, path, size)) {
                                val job = coroutineContext[Job]
                                results.addAll(
                                    ThumbCarver.carve(
                                        context, name, path, modified,
                                        open = { resolver.openInputStream(uri) },
                                        isActive = { job?.isActive != false }
                                    )
                                )
                                continue
                            }
                            val found = Classifier.classify(name, mime, path, size) {
                                resolver.openInputStream(uri)
                            }
                            if (found != null) {
                                results.add(
                                    VideoItem(
                                        uri = uri,
                                        name = Format.cleanName(name),
                                        size = size,
                                        dateMillis = modified,
                                        location = path,
                                        durationMs = 0L,
                                        mime = found.second,
                                        kind = found.first,
                                        fromMediaStore = false,
                                        source = SourceDetector.fromPath(path + name)
                                    )
                                )
                            }
                            if (scanned % 50 == 0) onProgress(ScanProgress(scanned, results.size))
                        }
                    }
                }
            } catch (e: SecurityException) {
                // unreadable folder: skip, keep scanning
            } catch (e: IllegalArgumentException) {
                // provider rejected this folder: skip
            }
        }
        onProgress(ScanProgress(scanned, results.size))
        ScanResult(results, scanned)
    }
}
