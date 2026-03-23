package com.d_drostes_apps.placestracker.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import java.io.File

class DayEntriesAdapter(
    private val entries: List<Entry>,
    private val onEntryClick: (Entry) -> Unit
) : RecyclerView.Adapter<DayEntriesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPic: ImageView = view.findViewById(R.id.ivDialogEntryPic)
        val tvTitle: TextView = view.findViewById(R.id.tvDialogEntryTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dialog_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.tvTitle.text = entry.title
        
        // Korrekt: Erst das coverImage (Favorit) prüfen, dann das erste Medien-Bild
        val coverPath = entry.coverImage ?: entry.media.firstOrNull()
        if (coverPath != null) {
            Glide.with(holder.itemView.context)
                .load(File(coverPath))
                .centerCrop()
                .into(holder.ivPic)
        } else {
            holder.ivPic.setImageResource(R.drawable.placeholder)
        }

        holder.itemView.setOnClickListener { onEntryClick(entry) }
    }

    override fun getItemCount(): Int = entries.size
}
