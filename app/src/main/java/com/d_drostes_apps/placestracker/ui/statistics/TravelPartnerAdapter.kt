package com.d_drostes_apps.placestracker.ui.statistics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.R

class TravelPartnerAdapter(
    private val items: List<Pair<String, Int>>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<TravelPartnerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvPartnerName)
        val count: TextView = view.findViewById(R.id.tvPartnerCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_travel_partner, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, count) = items[position]
        holder.name.text = name
        holder.count.text = count.toString()

        holder.itemView.setOnClickListener { onClick(name) }
    }
}