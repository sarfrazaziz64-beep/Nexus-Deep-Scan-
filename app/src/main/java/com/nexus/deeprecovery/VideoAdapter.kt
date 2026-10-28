package com.nexus.deeprecovery

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    private var items: List<VideoItem> = emptyList()
    private val cache = LruCache<String, Bitmap>(150)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.iv_thumb)
        val badge: TextView = v.findViewById(R.id.tv_badge)
        val name: TextView = v.findViewById(R.id.tv_name)
        val meta: TextView = v.findViewById(R.id.tv_meta)
        val location: TextView = v.findViewById(R.id.tv_location)
        var job: Job? = null
    }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<VideoItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.meta.text = listOf(Format.size(item.size), Format.duration(item.durationMs), Format.date(item.dateMillis))
            .filter { it.isNotEmpty() }
            .joinToString("  ·  ")
        holder.location.text = item.location
        holder.badge.text = context.getString(kindLabel(item.kind))
        holder.badge.setTextColor(ContextCompat.getColor(context, kindColor(item.kind)))
        holder.itemView.setOnClickListener { onClick(item) }

        holder.job?.cancel()
        val key = item.uri.toString()
        val cached = cache.get(key)
        if (cached != null) {
            holder.thumb.setImageBitmap(cached)
        } else {
            holder.thumb.setImageDrawable(null)
            holder.job = scope.launch {
                val bmp = withContext(Dispatchers.IO) { FileOps.thumbnail(context, item) }
                if (bmp != null) {
                    cache.put(key, bmp)
                    holder.thumb.setImageBitmap(bmp)
                }
            }
        }
    }

    companion object {
        fun kindLabel(kind: ItemKind): Int = when (kind) {
            ItemKind.VIDEO -> R.string.kind_video
            ItemKind.TRASHED -> R.string.kind_trashed
            ItemKind.HIDDEN -> R.string.kind_hidden
            ItemKind.SIGNATURE -> R.string.kind_signature
            ItemKind.THUMBNAIL -> R.string.kind_thumbnail
        }

        fun kindColor(kind: ItemKind): Int = when (kind) {
            ItemKind.VIDEO -> R.color.nexus_cyan
            ItemKind.TRASHED -> R.color.nexus_ok
            ItemKind.HIDDEN -> R.color.nexus_purple
            ItemKind.SIGNATURE -> R.color.nexus_warn
            ItemKind.THUMBNAIL -> R.color.nexus_muted
        }
    }
}
