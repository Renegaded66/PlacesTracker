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

        // 🌟 FIX 1: Die ID exakt so benannt wie in deinem XML!
        val tvLabel: TextView = view.findViewById(R.id.tvYearPhotoTitle)

        // 🌟 FIX 2: Das Datumsfeld aus dem XML hinzugefügt
        val tvDate: TextView = view.findViewById(R.id.tvYearPhotoDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Stellt sicher, dass er genau deine Datei 'item_year_photo.xml' lädt
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_year_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        Glide.with(holder.itemView.context)
            .load(File(item.path))
            .centerCrop()
            .into(holder.imageView)

        // Füllt den Titel mit deinem "label" aus der Datenklasse
        holder.tvLabel.text = item.label

        // Da 'YearPhotoItem' aktuell kein Datum enthält, blenden wir das Textfeld
        // vorerst aus, damit da nicht der Platzhalter "12. Mai 2024" stehen bleibt.
        // (Wenn du das später nutzen willst, füge einfach 'val date: String' zu YearPhotoItem hinzu!)
        holder.tvDate.visibility = View.GONE

        holder.itemView.setOnClickListener { onPhotoClick(item.path, item.allPaths) }
    }

    override fun getItemCount(): Int = items.size
}