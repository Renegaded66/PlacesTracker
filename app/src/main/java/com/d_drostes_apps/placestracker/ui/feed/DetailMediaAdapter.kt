package com.d_drostes_apps.placestracker.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import java.io.File

class DetailMediaAdapter(
    private val mediaPaths: List<String>,
    private val layoutResId: Int = R.layout.item_detail_media,
    private val onMediaClick: (String, View) -> Unit
) : RecyclerView.Adapter<DetailMediaAdapter.MediaViewHolder>() {

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivMediaItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val path = mediaPaths[position]
        
        if (path == "placeholder") {
            Glide.with(holder.itemView.context)
                .load(R.drawable.placeholder)
                .centerCrop()
                .into(holder.imageView)
        } else {
            Glide.with(holder.itemView.context)
                .load(File(path))
                .centerCrop()
                .into(holder.imageView)
        }

        holder.imageView.transitionName = "media_$path"

        holder.itemView.setOnClickListener {
            onMediaClick(path, holder.imageView)
        }
    }

    override fun getItemCount(): Int = mediaPaths.size
}
