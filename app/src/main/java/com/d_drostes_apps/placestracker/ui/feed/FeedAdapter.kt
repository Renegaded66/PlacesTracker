package com.d_drostes_apps.placestracker.ui.feed

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.FeedItem
import com.d_drostes_apps.placestracker.data.TripStop
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FeedAdapter(
    private var items: List<FeedItem> = emptyList(),
    private val onItemClick: (FeedItem, Int?) -> Unit,
    private val onConfirmDraft: (FeedItem) -> Unit = {},
    private val onRemoveDraft: (FeedItem) -> Unit = {},
    private val showDrafts: Boolean = true
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    private val expandedTrips = mutableSetOf<Int>()
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    class FeedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.feedTitle)
        val image: ImageView = view.findViewById(R.id.feedImage)
        val image2: ImageView = view.findViewById(R.id.feedImage2)
        val image3: ImageView = view.findViewById(R.id.feedImage3)
        val extraMediaContainer: View = view.findViewById(R.id.extraMediaContainer)
        val tvMediaCount: TextView = view.findViewById(R.id.tvMediaCount)
        val tvFeedDateTime: TextView = view.findViewById(R.id.tvFeedDateTime)
        val btnExpandTrip: ImageButton = view.findViewById(R.id.btnExpandTrip)
        val rvStopsPreview: RecyclerView = view.findViewById(R.id.rvTripStopsPreview)
        val ivTypeIcon: ImageView = view.findViewById(R.id.ivTypeIcon)
        val tvLocationFlags: TextView = view.findViewById(R.id.tvLocationFlags)
        val tvTrackingBadge: TextView = view.findViewById(R.id.tvTrackingActiveBadge)
        
        val draftBadge: View = view.findViewById(R.id.draftBadge)
        val draftOverlay: View = view.findViewById(R.id.draftOverlay)
        val draftActions: View = view.findViewById(R.id.draftActions)
        val btnConfirmDraft: Button = view.findViewById(R.id.btnConfirmDraft)
        val btnRemoveDraft: Button = view.findViewById(R.id.btnRemoveDraft)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed_entry, parent, false)
        return FeedViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<FeedItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        
        holder.title.text = item.title

        val isDraft = showDrafts && if (item is FeedItem.Experience) item.entry.isDraft else false
        holder.draftBadge.visibility = if (isDraft) View.VISIBLE else View.GONE
        holder.draftOverlay.visibility = if (isDraft) View.VISIBLE else View.GONE
        holder.draftActions.visibility = if (isDraft) View.VISIBLE else View.GONE
        
        holder.btnConfirmDraft.setOnClickListener { onConfirmDraft(item) }
        holder.btnRemoveDraft.setOnClickListener { onRemoveDraft(item) }

        if (item is FeedItem.Experience) {
            holder.ivTypeIcon.setImageResource(R.drawable.ic_feed)
            holder.tvTrackingBadge.visibility = View.GONE
        } else {
            holder.ivTypeIcon.setImageResource(R.drawable.ic_marker)
            if (item is FeedItem.TripItem) {
                holder.tvTrackingBadge.visibility = if (item.trip.isTrackingActive) View.VISIBLE else View.GONE
            }
        }

        holder.tvLocationFlags.text = ""
        adapterScope.launch {
            val flags = getFlagsForItem(context, item)
            holder.tvLocationFlags.text = flags
        }

        val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val sdfDateTime = SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault())
        
        holder.tvFeedDateTime.visibility = View.VISIBLE
        holder.btnExpandTrip.visibility = View.GONE
        holder.rvStopsPreview.visibility = View.GONE
        holder.image2.visibility = View.GONE
        holder.image3.visibility = View.GONE
        holder.tvMediaCount.visibility = View.GONE
        holder.extraMediaContainer.visibility = View.GONE

        if (item is FeedItem.Experience) {
            holder.tvFeedDateTime.text = sdfDateTime.format(Date(item.date))
            
            val media = item.entry.media
            val coverPath = item.coverImage
            val otherMedia = media.filter { it != coverPath }
            
            if (otherMedia.isNotEmpty()) {
                holder.extraMediaContainer.visibility = View.VISIBLE
                holder.image2.visibility = View.VISIBLE
                Glide.with(context).load(File(otherMedia[0])).centerCrop().into(holder.image2)
                if (otherMedia.size > 1) {
                    holder.image3.visibility = View.VISIBLE
                    Glide.with(context).load(File(otherMedia[1])).centerCrop().into(holder.image3)
                }
                val remaining = media.size - 3
                if (remaining > 0) {
                    holder.tvMediaCount.visibility = View.VISIBLE
                    holder.tvMediaCount.text = "+$remaining"
                }
            }
        } else if (item is FeedItem.TripItem) {
            var tripInfo = ""
            if (item.stops.isNotEmpty()) {
                val minDate = item.stops.minOf { it.date }
                val maxDate = item.stops.maxOf { it.date }
                val dateStr = if (sdfDate.format(Date(minDate)) == sdfDate.format(Date(maxDate))) {
                    sdfDate.format(Date(minDate))
                } else {
                    "${sdfDate.format(Date(minDate))} - ${sdfDate.format(Date(maxDate))}"
                }
                
                val diffInMillis = maxDate - minDate
                val days = TimeUnit.MILLISECONDS.toDays(diffInMillis) + 1
                tripInfo = "$dateStr | $days Tage"
                
                // Trip Media Preview
                val allMedia = item.stops.flatMap { it.media }.distinct()
                val coverPath = item.coverImage
                val otherMedia = allMedia.filter { it != coverPath }
                
                if (otherMedia.isNotEmpty()) {
                    holder.extraMediaContainer.visibility = View.VISIBLE
                    holder.image2.visibility = View.VISIBLE
                    Glide.with(context).load(File(otherMedia[0])).centerCrop().into(holder.image2)
                    if (otherMedia.size > 1) {
                        holder.image3.visibility = View.VISIBLE
                        Glide.with(context).load(File(otherMedia[1])).centerCrop().into(holder.image3)
                    }
                    val totalMediaCount = allMedia.size
                    if (totalMediaCount > 3) {
                        holder.tvMediaCount.visibility = View.VISIBLE
                        holder.tvMediaCount.text = "+${totalMediaCount - 3}"
                    }
                }
            } else {
                tripInfo = sdfDate.format(Date(item.date))
            }
            
            holder.tvFeedDateTime.text = tripInfo
            
            // Async distance calculation
            adapterScope.launch {
                val app = (context.applicationContext as PlacesApplication)
                val locations = app.database.tripDao().getLocationsForTripSync(item.id)
                if (locations.size > 1) {
                    var totalDist = 0.0
                    for (i in 0 until locations.size - 1) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            locations[i].latitude, locations[i].longitude,
                            locations[i+1].latitude, locations[i+1].longitude,
                            results
                        )
                        totalDist += results[0]
                    }
                    val km = totalDist / 1000.0
                    if (km > 0.1) {
                        holder.tvFeedDateTime.text = "$tripInfo | ${String.format("%.1f", km)} km"
                    }
                }
            }

            holder.btnExpandTrip.visibility = View.VISIBLE
            val isExpanded = expandedTrips.contains(item.id)
            holder.btnExpandTrip.rotation = if (isExpanded) 180f else 0f
            holder.rvStopsPreview.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            if (isExpanded) {
                holder.rvStopsPreview.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                holder.rvStopsPreview.adapter = TripStopPreviewAdapter(item.stops) { stop ->
                    onItemClick(item, stop.id)
                }
            }

            holder.btnExpandTrip.setOnClickListener {
                if (expandedTrips.contains(item.id)) {
                    expandedTrips.remove(item.id)
                } else {
                    expandedTrips.add(item.id)
                }
                notifyItemChanged(position)
            }
        }

        val coverPath = item.coverImage
        if (coverPath != null) {
            Glide.with(context)
                .load(File(coverPath))
                .centerCrop()
                .placeholder(R.drawable.placeholder)
                .into(holder.image)
        } else {
            holder.image.setImageResource(R.drawable.vorschaubild)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item, null)
        }
    }

    private suspend fun getFlagsForItem(context: Context, item: FeedItem): String = withContext(Dispatchers.IO) {
        val countryCodes = mutableSetOf<String>()
        when (item) {
            is FeedItem.Experience -> {
                getCountryCode(context, item.entry.location)?.let { countryCodes.add(it) }
            }
            is FeedItem.TripItem -> {
                item.stops.forEach { stop ->
                    getCountryCode(context, stop.location)?.let { countryCodes.add(it) }
                }
            }
        }
        countryCodes.joinToString(" ") { getFlagEmoji(it) }
    }

    private fun getCountryCode(context: Context, location: String?): String? {
        if (location.isNullOrBlank()) return null
        return try {
            val coords = location.split(",")
            if (coords.size != 2) return null
            val lat = coords[0].toDouble()
            val lon = coords[1].toDouble()
            val geocoder = Geocoder(context, Locale.ENGLISH)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.countryCode
        } catch (e: Exception) {
            null
        }
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
    }
}

class TripStopPreviewAdapter(
    private val stops: List<TripStop>,
    private val onStopClick: (TripStop) -> Unit
) : RecyclerView.Adapter<TripStopPreviewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPic: ImageView = view.findViewById(R.id.ivStopPreview)
        val tvDate: TextView = view.findViewById(R.id.tvStopPreviewDate)
        val draftOverlay: View = view.findViewById(R.id.draftOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip_stop_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stop = stops[position]
        val sdf = SimpleDateFormat("dd.yy HH:mm", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(stop.date))
        
        holder.draftOverlay.visibility = if (stop.isDraft) View.VISIBLE else View.GONE

        val coverPath = stop.coverImage ?: stop.media.firstOrNull()
        if (coverPath != null) {
            Glide.with(holder.itemView.context)
                .load(File(coverPath))
                .centerCrop()
                .into(holder.ivPic)
        } else {
            holder.ivPic.setImageResource(R.drawable.vorschaubild)
        }
        
        holder.itemView.setOnClickListener { onStopClick(stop) }
    }

    override fun getItemCount(): Int = stops.size
}
