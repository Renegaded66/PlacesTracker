package com.d_drostes_apps.placestracker.ui.statistics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import java.io.File

data class YearPhotoItem(
    val path: String,
    val label: String,
    val allPaths: List<String>
)

class YearPhotoAdapter(
    private val items: List<YearPhotoItem>,
    private val onPhotoClick: (String, List<String>) -> Unit
) : RecyclerView.Adapter<YearPhotoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivYearPhoto)
        val tvLabel: TextView = view.findViewById(R.id.tvPhotoLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_year_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        Glide.with(holder.itemView.context)
            .load(File(item.path))
            .centerCrop()
            .into(holder.imageView)
            
        holder.tvLabel.text = item.label
        holder.itemView.setOnClickListener { onPhotoClick(item.path, item.allPaths) }
    }

    override fun getItemCount(): Int = items.size
}
