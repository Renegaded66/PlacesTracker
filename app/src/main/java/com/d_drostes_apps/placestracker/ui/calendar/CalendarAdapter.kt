package com.d_drostes_apps.placestracker.ui.calendar

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import java.io.File
import java.util.*

class CalendarAdapter(
    private val days: List<CalendarDay>,
    private val onDayClick: (CalendarDay) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDayNumber: TextView = view.findViewById(R.id.tvDayNumber)
        val ivDayEntry: ImageView = view.findViewById(R.id.ivDayEntry)
        val vDimmer: View = view.findViewById(R.id.vDimmer)
        val tvMoreCount: TextView = view.findViewById(R.id.tvMoreCount)
        val card: MaterialCardView = view as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        val cal = Calendar.getInstance()
        cal.time = day.date
        
        holder.tvDayNumber.text = cal.get(Calendar.DAY_OF_MONTH).toString()
        holder.tvDayNumber.visibility = View.VISIBLE
        
        // Highlight today
        val today = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                     cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        
        if (isToday) {
            holder.card.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, holder.itemView.resources.displayMetrics).toInt()
            val colorPrimary = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimary)
            holder.card.strokeColor = colorPrimary
        } else {
            holder.card.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, holder.itemView.resources.displayMetrics).toInt()
            holder.card.setStrokeColor(android.graphics.Color.parseColor("#EEEEEE"))
        }

        if (!day.isCurrentMonth) {
            holder.itemView.alpha = 0.3f
        } else {
            holder.itemView.alpha = 1.0f
        }

        if (day.items.isNotEmpty()) {
            val firstItem = day.items.first()
            val coverPath = firstItem.coverImage ?: firstItem.media.firstOrNull()
            
            holder.ivDayEntry.visibility = View.VISIBLE
            holder.vDimmer.visibility = View.VISIBLE
            holder.tvDayNumber.setTextColor(android.graphics.Color.WHITE)

            if (coverPath != null) {
                Glide.with(holder.itemView.context)
                    .load(File(coverPath))
                    .centerCrop()
                    .into(holder.ivDayEntry)
            }

            if (day.items.size > 1) {
                holder.tvMoreCount.visibility = View.VISIBLE
                holder.tvMoreCount.text = "+${day.items.size - 1}"
            } else {
                holder.tvMoreCount.visibility = View.GONE
            }
        } else {
            holder.ivDayEntry.visibility = View.GONE
            holder.vDimmer.visibility = View.GONE
            holder.tvMoreCount.visibility = View.GONE
            
            val colorOnSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
            holder.tvDayNumber.setTextColor(colorOnSurface)
        }

        holder.itemView.setOnClickListener { onDayClick(day) }
    }

    override fun getItemCount(): Int = days.size
}
