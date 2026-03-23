package com.d_drostes_apps.placestracker.ui.themes.newentry

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import java.io.File

class MediaAdapter(
    private val mediaPaths: List<String>,
    private var coverImagePath: String? = null,
    private val onAddClick: () -> Unit,
    private val onMediaClick: (String) -> Unit, // Neu: Für das Options-Menü
    private val onRemove: (String) -> Unit,
    private val onSetCover: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ADD = 0
        private const val TYPE_MEDIA = 1
    }

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivMediaItem)
        val btnRemove: ImageView = view.findViewById(R.id.btnRemoveMedia)
        val ivCoverIndicator: ImageView = view.findViewById(R.id.ivCoverIndicator)
    }

    class AddViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_ADD else TYPE_MEDIA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ADD) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_add_media, parent, false)
            AddViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_square, parent, false)
            MediaViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AddViewHolder) {
            holder.itemView.setOnClickListener { onAddClick() }
        } else if (holder is MediaViewHolder) {
            val path = mediaPaths[position - 1]
            
            Glide.with(holder.itemView.context)
                .load(File(path))
                .centerCrop()
                .into(holder.imageView)

            holder.btnRemove.setOnClickListener { onRemove(path) }
            holder.itemView.setOnClickListener { onMediaClick(path) } // Öffnet das Menü

            holder.ivCoverIndicator.visibility = if (path == coverImagePath) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount(): Int = mediaPaths.size + 1

    fun updateCoverImage(path: String?) {
        this.coverImagePath = path
        notifyDataSetChanged()
    }
}
