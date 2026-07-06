package com.example.gifkeyboard.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.gifkeyboard.R
import com.example.gifkeyboard.data.MediaItem

class MediaGridAdapter(
    private val onItemTapped: (MediaItem) -> Unit,
    private val onItemLongPressed: (MediaItem) -> Unit,
    private val isFavorite: (String) -> Boolean
) : RecyclerView.Adapter<MediaGridAdapter.ViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    fun currentItems(): List<MediaItem> = items.toList()

    fun submitList(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val audioBadge: ImageView = view.findViewById(R.id.audioBadge)
        val favoriteBadge: ImageView = view.findViewById(R.id.favoriteBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        Glide.with(holder.thumbnail.context)
            .load(item.previewUrl)
            .centerCrop()
            .into(holder.thumbnail)

        holder.audioBadge.visibility = if (item.hasAudio) View.VISIBLE else View.GONE
        holder.favoriteBadge.visibility = if (isFavorite(item.id)) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onItemTapped(item) }
        holder.itemView.setOnLongClickListener {
            onItemLongPressed(item)
            true
        }
    }

    override fun getItemCount() = items.size
}
