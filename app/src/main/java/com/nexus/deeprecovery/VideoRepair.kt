package com.nexus.deeprecovery

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class RepairImpossibleException : Exception("index missing")

/**
 * Tries to rebuild a damaged / oddly packaged video into a clean, fresh MP4.
 * It reads every video and audio sample Android can still parse and writes them
 * into a new container (the original is untouched). This fixes many files with a
 * damaged container, wrong extension or unusual layout. It cannot rebuild a file
 * whose index (moov) is completely gone: then RepairImpossibleException is thrown.
 */
object VideoRepair {

    suspend fun rebuild(context: Context, item: VideoItem): String = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "repair_" + System.currentTimeMillis() + ".mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        var samples = 0
        try {
            try {
                extractor.setDataSource(context, item.uri, null)
            } catch (e: Exception) {
                throw RepairImpossibleException()
            }
            if (extractor.trackCount == 0) throw RepairImpossibleException()

            val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val map = IntArray(extractor.trackCount) { -1 }
            var maxSize = 4 * 1024 * 1024
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                try {
                    map[i] = mux.addTrack(fmt)
                    extractor.selectTrack(i)
                    if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxSize = maxOf(maxSize, fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                } catch (e: Exception) {
                    map[i] = -1 // codec not supported by the MP4 writer: skip this track
                }
            }
            if (map.none { it >= 0 }) throw RepairImpossibleException()

            mux.start()
            started = true
            val buf = ByteBuffer.allocate(maxSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                ensureActive()
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val n = extractor.readSampleData(buf, 0)
                if (n < 0) break
                val target = map[track]
                if (target >= 0) {
                    info.offset = 0
                    info.size = n
                    info.presentationTimeUs = maxOf(0L, extractor.sampleTime)
                    info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    mux.writeSampleData(target, buf, info)
                    samples++
                }
                if (!extractor.advance()) break
            }
            if (samples == 0) throw RepairImpossibleException()
            mux.stop()
            started = false
            mux.release()
            muxer = null

            val base = item.name.substringBeforeLast('.').trimStart('.').ifBlank { "video" }
            val repaired = item.copy(
                uri = Uri.fromFile(out),
                name = "Repaired_$base.mp4",
                mime = "video/mp4",
                size = out.length()
            )
            FileOps.copyToRecovery(context, repaired)
        } finally {
            try { if (started) muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            try { extractor.release() } catch (_: Exception) { }
            out.delete()
        }
    }
}
