package com.d_drostes_apps.placestracker.ui.newtrip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.TripLocation
import com.d_drostes_apps.placestracker.data.TripStop
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class TripItem {
    data class Stop(val stop: TripStop) : TripItem()
    data class MiniStop(val location: TripLocation) : TripItem()
    data class MiniStopExpand(val isExpanded: Boolean, val count: Int, val id: String) : TripItem()

    val timestamp: Long
        get() = when (this) {
            is Stop -> stop.date
            is MiniStop -> location.timestamp
            is MiniStopExpand -> 0L
        }
}

class TripStopsAdapter(
    private var items: List<TripItem>,
    private val onStopClick: (TripStop) -> Unit,
    private val onMiniStopClick: (TripLocation) -> Unit,
    private val onDeleteMiniStop: (TripLocation) -> Unit,
    private val onToggleExpand: (String) -> Unit,
    private val onConfirmDraft: (TripStop) -> Unit = {},
    private val onRemoveDraft: (TripStop) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_STOP = 0
        private const val TYPE_MINI_STOP = 1
        private const val TYPE_EXPAND = 2
    }

    fun updateItems(newItems: List<TripItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): TripItem? {
        return if (position in items.indices) items[position] else null
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TripItem.Stop -> TYPE_STOP
            is TripItem.MiniStop -> TYPE_MINI_STOP
            is TripItem.MiniStopExpand -> TYPE_EXPAND
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_STOP -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip_stop, parent, false)
                StopViewHolder(view)
            }
            TYPE_EXPAND -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mini_stop_expand, parent, false)
                ExpandViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mini_stop, parent, false)
                MiniStopViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TripItem.Stop -> (holder as StopViewHolder).bind(item.stop)
            is TripItem.MiniStop -> (holder as MiniStopViewHolder).bind(item.location)
            is TripItem.MiniStopExpand -> (holder as ExpandViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class StopViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivPic: ImageView = view.findViewById(R.id.ivStopPic)
        private val tvTitle: TextView = view.findViewById(R.id.tvStopTitle)
        private val tvDate: TextView = view.findViewById(R.id.tvStopDate)
        private val draftOverlay: View = view.findViewById(R.id.draftOverlay)
        private val draftBadge: View = view.findViewById(R.id.draftBadge)
        private val btnConfirm: Button = view.findViewById(R.id.btnConfirmStopDraft)
        private val btnRemove: Button = view.findViewById(R.id.btnRemoveStopDraft)

        fun bind(stop: TripStop) {
            tvTitle.text = stop.title
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            tvDate.text = sdf.format(Date(stop.date))
            
            draftOverlay.visibility = if (stop.isDraft) View.VISIBLE else View.GONE
            draftBadge.visibility = if (stop.isDraft) View.VISIBLE else View.GONE
            btnConfirm.visibility = if (stop.isDraft) View.VISIBLE else View.GONE
            btnRemove.visibility = if (stop.isDraft) View.VISIBLE else View.GONE
            
            btnConfirm.setOnClickListener { onConfirmDraft(stop) }
            btnRemove.setOnClickListener { onRemoveDraft(stop) }

            val coverPath = stop.coverImage ?: stop.media.firstOrNull()
            if (coverPath != null) {
                Glide.with(itemView.context)
                    .load(File(coverPath))
                    .centerCrop()
                    .into(ivPic)
            } else {
                ivPic.setImageResource(R.drawable.vorschaubild)
            }

            itemView.setOnClickListener { onStopClick(stop) }
        }
    }

    inner class MiniStopViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvMiniStopTitle)
        private val tvDate: TextView = view.findViewById(R.id.tvMiniStopDate)
        private val ivDelete: ImageView = view.findViewById(R.id.ivDeleteMiniStop)

        fun bind(location: TripLocation) {
            tvTitle.text = "Mini-Stopp"
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            tvDate.text = sdf.format(Date(location.timestamp))

            itemView.setOnClickListener { onMiniStopClick(location) }
            ivDelete.setOnClickListener { onDeleteMiniStop(location) }
        }
    }

    inner class ExpandViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvInfo: TextView = view.findViewById(R.id.tvExpandInfo)
        private val ivIcon: ImageView = view.findViewById(R.id.ivExpandIcon)

        fun bind(item: TripItem.MiniStopExpand) {
            tvInfo.text = "${item.count} Mini-Stopps"
            ivIcon.setImageResource(if (item.isExpanded) android.R.drawable.ic_input_delete else android.R.drawable.ic_input_add)
            itemView.setOnClickListener { onToggleExpand(item.id) }
        }
    }
}
