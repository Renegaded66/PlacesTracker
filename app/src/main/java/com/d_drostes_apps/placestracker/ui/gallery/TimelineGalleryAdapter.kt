package com.d_drostes_apps.placestracker.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R

class TimelineGalleryAdapter(
    var items: List<GalleryItem>,
    private val onPhotoClick: (GalleryItem.Photo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PHOTO = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is GalleryItem.Header -> TYPE_HEADER
            is GalleryItem.Photo -> TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_header, parent, false))
        } else {
            PhotoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_photo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is GalleryItem.Header) {
            holder.tvHeader.text = item.date
        } else if (holder is PhotoViewHolder && item is GalleryItem.Photo) {
            Glide.with(holder.ivPhoto)
                .load(item.uri)
                .override(400,400)
                .centerCrop()
                .into(holder.ivPhoto)
            holder.itemView.setOnClickListener { onPhotoClick(item) }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<GalleryItem>) {
        val diffResult = DiffUtil.calculateDiff(GalleryDiffCallback(items, newItems))
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tvHeaderDate)
    }

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
    }

    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return if (getItemViewType(position) == TYPE_HEADER) 3 else 1
        }
    }

    class GalleryDiffCallback(
        private val oldList: List<GalleryItem>,
        private val newList: List<GalleryItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            val old = oldList[oldPos]
            val new = newList[newPos]
            return if (old is GalleryItem.Header && new is GalleryItem.Header) {
                old.date == new.date
            } else if (old is GalleryItem.Photo && new is GalleryItem.Photo) {
                old.id == new.id
            } else false
        }
        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean = oldList[oldPos] == newList[newPos]
    }
}
