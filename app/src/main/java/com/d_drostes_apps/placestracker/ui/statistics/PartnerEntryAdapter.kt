package com.d_drostes_apps.placestracker.ui.statistics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.R
import java.text.SimpleDateFormat
import java.util.*

class PartnerEntryAdapter(
    private val items: List<Triple<String, Long, String>>, // title, date, target
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<PartnerEntryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvPartnerItemTitle)
        val tvDate: TextView = view.findViewById(R.id.tvPartnerItemDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_partner_entry, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (title, date, target) = items[position]
        holder.tvTitle.text = title

        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(date))

        holder.itemView.setOnClickListener { onClick(target) }
    }
}
