package com.d_drostes_apps.placestracker.ui.newtrip

import android.content.Context
import android.location.Geocoder
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class TripItem {
    data class Stop(val stop: TripStop) : TripItem()
    data class MiniStop(val location: TripLocation) : TripItem()
    data class MiniStopExpand(val isExpanded: Boolean, val count: Int, val id: String) : TripItem()
    data class Transport(val fromStopId: Int, val toStopId: Int, val mode: String?) : TripItem()

    val timestamp: Long
        get() = when (this) {
            is Stop -> stop.date
            is MiniStop -> location.timestamp
            is MiniStopExpand -> 0L
            is Transport -> 0L
        }
}

class TripStopsAdapter(
    private var items: List<TripItem>,
    private val onStopClick: (TripStop) -> Unit,
    private val onMiniStopClick: (TripLocation) -> Unit,
    private val onDeleteMiniStop: (TripLocation) -> Unit,
    private val onToggleExpand: (String) -> Unit,
    private val onTransportClick: (Int, String?) -> Unit,
    private val onConfirmDraft: (TripStop) -> Unit = {},
    private val onRemoveDraft: (TripStop) -> Unit = {},
    private val scope: CoroutineScope? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_STOP = 0
        private const val TYPE_MINI_STOP = 1
        private const val TYPE_EXPAND = 2
        private const val TYPE_TRANSPORT = 3
    }

    private val flagCache = mutableMapOf<String, String>()

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
            is TripItem.Transport -> TYPE_TRANSPORT
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
            TYPE_TRANSPORT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transport_plus, parent, false)
                TransportViewHolder(view)
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
            is TripItem.Transport -> (holder as TransportViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class StopViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivPic: ImageView = view.findViewById(R.id.ivStopPic)
        private val tvTitle: TextView = view.findViewById(R.id.tvStopTitle)
        private val tvDate: TextView = view.findViewById(R.id.tvStopDate)
        private val tvFlag: TextView = view.findViewById(R.id.tvStopFlag)
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

            // Load Flag
            tvFlag.visibility = View.GONE
            stop.location?.let { loc ->
                if (flagCache.containsKey(loc)) {
                    tvFlag.text = flagCache[loc]
                    tvFlag.visibility = View.VISIBLE
                } else {
                    scope?.launch {
                        val flag = getFlagForLocation(itemView.context, loc)
                        if (flag != null) {
                            flagCache[loc] = flag
                            withContext(Dispatchers.Main) {
                                tvFlag.text = flag
                                tvFlag.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }

            itemView.setOnClickListener { onStopClick(stop) }
        }
    }

    private suspend fun getFlagForLocation(context: Context, location: String): String? = withContext(Dispatchers.IO) {
        try {
            val coords = location.split(",")
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(coords[0].toDouble(), coords[1].toDouble(), 1)
            val code = addresses?.firstOrNull()?.countryCode
            if (code != null) getFlagEmoji(code) else null
        } catch (e: Exception) { null }
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    inner class TransportViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val btnTransport: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnTransportAction)

        fun bind(item: TripItem.Transport) {
            val iconRes = when(item.mode) {
                "car" -> android.R.drawable.ic_menu_directions
                "bike" -> android.R.drawable.ic_menu_mylocation
                "plane" -> android.R.drawable.ic_menu_send
                "train" -> android.R.drawable.ic_menu_slideshow
                "walk" -> android.R.drawable.ic_menu_compass
                else -> R.drawable.ic_add
            }
            btnTransport.setIconResource(iconRes)
            btnTransport.setOnClickListener { onTransportClick(item.toStopId, item.mode) }
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
