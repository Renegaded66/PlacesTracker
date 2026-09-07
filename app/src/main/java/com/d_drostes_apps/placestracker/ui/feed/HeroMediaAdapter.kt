package com.d_drostes_apps.placestracker.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import java.io.File

/**
 * Hero-Galerie für die Detailansicht: volle Bilder ohne Rahmen,
 * mit Tap-to-Fullscreen.
 */
class HeroMediaAdapter(
    private val mediaPaths: List<String>,
    private val onImageClick: (String) -> Unit
) : RecyclerView.Adapter<HeroMediaAdapter.HeroViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hero_media, parent, false)
        return HeroViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        val path = mediaPaths[position]
        Glide.with(holder.itemView.context)
            .load(File(path))
            .centerCrop()
            .into(holder.imageView)
        holder.imageView.setOnClickListener { onImageClick(path) }
    }

    override fun getItemCount(): Int = mediaPaths.size

    class HeroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.heroImageView)
    }
}