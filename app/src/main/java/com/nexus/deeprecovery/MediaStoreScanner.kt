package com.nexus.deeprecovery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

/**
 * Lists every video Android's media index still exposes.
 * On Android 11+ it also includes items in the system trash (IS_TRASHED = 1),
 * which can genuinely be restored with MediaStore.createTrashRequest().
 * This is read-only: it only queries the index.
 */
object MediaStoreScanner {

    fun scan(context: Context): List<VideoItem> {
        val resolver = context.contentResolver
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            projection.add(MediaStore.MediaColumns.DATA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.MediaColumns.IS_TRASHED)
        }

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Video.Media.DATE_MODIFIED)
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
            }
            resolver.query(collection, projection.toTypedArray(), args, null)
        } else {
            resolver.query(
                collection, projection.toTypedArray(), null, null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )
        }

        val out = ArrayList<VideoItem>()
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
            val modCol = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
            val addCol = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
            val durCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
            val mimeCol = c.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                c.getColumnIndex(MediaStore.MediaColumns.DATA)
            }
            val trashCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
            } else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val rawName = if (nameCol >= 0) c.getString(nameCol) else null
                val size = if (sizeCol >= 0 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L
                var dateSec = if (modCol >= 0 && !c.isNull(modCol)) c.getLong(modCol) else 0L
                if (dateSec <= 0 && addCol >= 0 && !c.isNull(addCol)) dateSec = c.getLong(addCol)
                val duration = if (durCol >= 0 && !c.isNull(durCol)) c.getLong(durCol) else 0L
                val mime = if (mimeCol >= 0) c.getString(mimeCol) else null
                var location = if (pathCol >= 0) c.getString(pathCol) ?: "" else ""
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // DATA is the full file path; keep only the folder
                    location = location.substringBeforeLast('/', location)
                }
                val trashed = trashCol >= 0 && c.getInt(trashCol) == 1
                val name = Format.cleanName(rawName ?: "video_$id")
                val hidden = !trashed && ((rawName ?: "").startsWith(".") || location.contains("/."))
                val kind = when {
                    trashed -> ItemKind.TRASHED
                    hidden -> ItemKind.HIDDEN
                    else -> ItemKind.VIDEO
                }
                out.add(
                    VideoItem(
                        uri = uri,
                        name = name,
                        size = size,
                        dateMillis = dateSec * 1000L,
                        location = location,
                        durationMs = duration,
                        mime = mime ?: (Format.mimeFromName(name) ?: "video/mp4"),
                        kind = kind,
                        fromMediaStore = true
                    )
                )
            }
        }
        return out
    }
}
