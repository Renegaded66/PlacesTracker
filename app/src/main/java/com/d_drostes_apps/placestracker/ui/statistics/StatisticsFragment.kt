package com.d_drostes_apps.placestracker.ui.statistics

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.ui.feed.MediaDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    private lateinit var tvCountryCount: TextView
    private lateinit var tvCountryPercentage: TextView
    private lateinit var progressCountries: LinearProgressIndicator
    private lateinit var tvFlagList: TextView
    private lateinit var tvTripCount: TextView
    private lateinit var tvEntryCount: TextView
    private lateinit var tvStopCount: TextView
    private lateinit var tvTotalDistance: TextView
    private lateinit var btnYearSummary: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCountryCount = view.findViewById(R.id.tvCountryCount)
        tvCountryPercentage = view.findViewById(R.id.tvCountryPercentage)
        progressCountries = view.findViewById(R.id.progressCountries)
        tvFlagList = view.findViewById(R.id.tvFlagList)
        tvTripCount = view.findViewById(R.id.tvTripCount)
        tvEntryCount = view.findViewById(R.id.tvEntryCount)
        tvStopCount = view.findViewById(R.id.tvStopCount)
        tvTotalDistance = view.findViewById(R.id.tvTotalDistance)
        btnYearSummary = view.findViewById(R.id.btnYearSummary)

        btnYearSummary.setOnClickListener {
            showYearSummaryDialog()
        }

        val app = requireActivity().application as PlacesApplication
        val entryRepository = app.repository
        val tripRepository = app.tripRepository
        val tripDao = app.database.tripDao()

        lifecycleScope.launch {
            combine(
                entryRepository.allEntries,
                tripRepository.allTrips,
                tripRepository.allTripStops,
                tripDao.getAllTripLocations()
            ) { entries, trips, stops, _ ->
                Triple(entries, trips, stops)
            }.collect { (entries, trips, stops) ->
                updateStatistics(entries, trips, stops)
            }
        }
    }

    private fun updateStatistics(entries: List<Entry>, trips: List<Trip>, stops: List<TripStop>) {
        tvTripCount.text = trips.size.toString()
        tvEntryCount.text = entries.size.toString()
        tvStopCount.text = stops.size.toString()

        lifecycleScope.launch {
            val countries = getVisitedCountries(entries, stops)
            val countryCount = countries.size
            val percentage = (countryCount.toFloat() / 195f * 100f).toInt()

            tvCountryCount.text = countryCount.toString()
            tvCountryPercentage.text = "$percentage%"
            progressCountries.setProgress(percentage, true)

            val flagsText = countries.entries.joinToString(", ") { (code, name) ->
                "${getFlagEmoji(code)} $name"
            }
            tvFlagList.text = if (flagsText.isEmpty()) "Noch keine Länder besucht" else flagsText
        }

        lifecycleScope.launch {
            val totalDistance = calculateTotalDistance(trips)
            tvTotalDistance.text = String.format(Locale.getDefault(), "%.1f", totalDistance / 1000.0)
        }
    }

    private fun showYearSummaryDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_year_summary, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()

        val toolbar = dialogView.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarYearSummary)
        val btnSelectYear = dialogView.findViewById<MaterialButton>(R.id.btnSelectYear)
        val tvYearTitle = dialogView.findViewById<TextView>(R.id.tvYearTitle)
        val statsGrid = dialogView.findViewById<View>(R.id.yearStatsGrid)
        val tvYearTrips = dialogView.findViewById<TextView>(R.id.tvYearTrips)
        val tvYearEntries = dialogView.findViewById<TextView>(R.id.tvYearEntries)
        val tvYearDistance = dialogView.findViewById<TextView>(R.id.tvYearDistance)
        val tvYearCountries = dialogView.findViewById<TextView>(R.id.tvYearCountries)
        val tvYearFlags = dialogView.findViewById<TextView>(R.id.tvYearFlags)
        val tvPhotosTitle = dialogView.findViewById<TextView>(R.id.tvYearPhotosTitle)
        val tvNoData = dialogView.findViewById<TextView>(R.id.tvNoData)
        val listContainer = dialogView.findViewById<LinearLayout>(R.id.llYearListContainer)

        toolbar.setNavigationOnClickListener { dialog.dismiss() }

        btnSelectYear.setOnClickListener {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val years = (currentYear downTo 2020).map { it.toString() }.toTypedArray()
            
            AlertDialog.Builder(requireContext())
                .setTitle("Jahr auswählen")
                .setItems(years) { _, which ->
                    val selectedYear = years[which].toInt()
                    loadYearSummary(selectedYear, listContainer, tvYearTitle, statsGrid, tvYearTrips, tvYearEntries, tvYearDistance, tvYearCountries, tvYearFlags, tvPhotosTitle, tvNoData)
                }.show()
        }

        dialog.show()
    }

    private fun loadYearSummary(
        year: Int,
        listContainer: LinearLayout,
        tvTitle: TextView,
        statsGrid: View,
        tvTrips: TextView,
        tvEntries: TextView,
        tvDistance: TextView,
        tvCountries: TextView,
        tvFlags: TextView,
        tvPhotosTitle: TextView,
        tvNoData: TextView
    ) {
        val calendar = Calendar.getInstance()
        calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        val startOfYear = calendar.timeInMillis
        calendar.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        val endOfYear = calendar.timeInMillis

        lifecycleScope.launch {
            val app = requireActivity().application as PlacesApplication
            val allEntries = app.repository.allEntries.first().filter { it.date in startOfYear..endOfYear }
            val allTrips = app.tripRepository.allTrips.first().filter { it.date in startOfYear..endOfYear }
            val allStops = app.tripRepository.allTripStops.first()

            listContainer.removeAllViews()

            if (allEntries.isEmpty() && allTrips.isEmpty()) {
                tvTitle.visibility = View.GONE
                statsGrid.visibility = View.GONE
                tvFlags.visibility = View.GONE
                tvPhotosTitle.visibility = View.GONE
                listContainer.visibility = View.GONE
                tvNoData.visibility = View.VISIBLE
                return@launch
            }

            tvTitle.text = "Dein Jahr $year"
            tvTitle.visibility = View.VISIBLE
            statsGrid.visibility = View.VISIBLE
            tvNoData.visibility = View.GONE
            listContainer.visibility = View.VISIBLE

            tvTrips.text = allTrips.size.toString()
            tvEntries.text = allEntries.size.toString()
            
            // Pass all trips of this year
            val distance = calculateTotalDistance(allTrips)
            tvDistance.text = String.format(Locale.getDefault(), "%.1f", distance / 1000.0)

            val countries = getVisitedCountries(allEntries, allStops.filter { it.date in startOfYear..endOfYear })
            tvCountries.text = countries.size.toString()
            
            val flagsText = countries.keys.joinToString(" ") { getFlagEmoji(it) }
            tvFlags.text = flagsText
            tvFlags.visibility = if (flagsText.isNotEmpty()) View.VISIBLE else View.GONE
            tvPhotosTitle.visibility = View.VISIBLE

            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

            // Trips and Stops rendering
            allTrips.forEach { trip ->
                val tripStops = allStops.filter { it.tripId == trip.id }.sortedBy { it.date }
                val sectionView = LayoutInflater.from(requireContext()).inflate(R.layout.item_summary_section, listContainer, false)
                sectionView.findViewById<TextView>(R.id.tvSummaryTitle).text = trip.title
                
                val dateStr = if (tripStops.isNotEmpty()) {
                    "${sdf.format(Date(tripStops.first().date))} - ${sdf.format(Date(tripStops.last().date))}"
                } else {
                    sdf.format(Date(trip.date))
                }
                sectionView.findViewById<TextView>(R.id.tvSummarySubtitle).text = dateStr
                
                val ivCover = sectionView.findViewById<ImageView>(R.id.ivSummaryPhoto)
                trip.coverImage?.let { path -> Glide.with(this@StatisticsFragment).load(File(path)).centerCrop().into(ivCover) }
                ivCover.setOnClickListener { trip.coverImage?.let { p -> showFullscreen(p) } }

                val stopsLayout = sectionView.findViewById<LinearLayout>(R.id.llStopsContainer)
                tripStops.forEach { stop ->
                    val stopView = LayoutInflater.from(requireContext()).inflate(R.layout.item_summary_stop, stopsLayout, false)
                    stopView.findViewById<TextView>(R.id.tvStopTitle).text = stop.title
                    stopView.findViewById<TextView>(R.id.tvStopDate).text = sdf.format(Date(stop.date))
                    
                    val countryInfo = getCountryInfo(requireContext(), stop.location)
                    stopView.findViewById<TextView>(R.id.tvStopFlag).text = countryInfo?.first?.let { getFlagEmoji(it) } ?: ""
                    
                    val ivStop = stopView.findViewById<ImageView>(R.id.ivStopPhoto)
                    val stopPhoto = stop.coverImage ?: stop.media.firstOrNull()
                    stopPhoto?.let { path -> Glide.with(this@StatisticsFragment).load(File(path)).centerCrop().into(ivStop) }
                    ivStop.setOnClickListener { stopPhoto?.let { p -> showFullscreen(p) } }
                    
                    stopsLayout.addView(stopView)
                }
                listContainer.addView(sectionView)
            }

            // Entries rendering
            allEntries.forEach { entry ->
                val sectionView = LayoutInflater.from(requireContext()).inflate(R.layout.item_summary_section, listContainer, false)
                sectionView.findViewById<TextView>(R.id.tvSummaryTitle).text = entry.title
                sectionView.findViewById<TextView>(R.id.tvSummarySubtitle).text = sdf.format(Date(entry.date))
                
                val ivCover = sectionView.findViewById<ImageView>(R.id.ivSummaryPhoto)
                entry.coverImage?.let { path -> Glide.with(this@StatisticsFragment).load(File(path)).centerCrop().into(ivCover) }
                ivCover.setOnClickListener { entry.coverImage?.let { p -> showFullscreen(p) } }
                
                sectionView.findViewById<View>(R.id.llStopsContainer).visibility = View.GONE
                listContainer.addView(sectionView)
            }
        }
    }

    private fun showFullscreen(path: String) {
        MediaDialogFragment.newInstance(arrayListOf(path), 0).show(parentFragmentManager, "media_detail")
    }

    private suspend fun getVisitedCountries(entries: List<Entry>, stops: List<TripStop>): Map<String, String> = withContext(Dispatchers.IO) {
        val countries = mutableMapOf<String, String>()
        val context = context ?: return@withContext emptyMap()

        entries.forEach { entry ->
            getCountryInfo(context, entry.location)?.let { countries[it.first] = it.second }
        }
        stops.forEach { stop ->
            getCountryInfo(context, stop.location)?.let { countries[it.first] = it.second }
        }

        countries.toList().sortedBy { it.second }.toMap()
    }

    private fun getCountryInfo(context: Context, location: String?): Pair<String, String>? {
        if (location.isNullOrBlank()) return null
        return try {
            val coords = location.split(",")
            if (coords.size != 2) return null
            val lat = coords[0].toDouble()
            val lon = coords[1].toDouble()
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val address = addresses?.firstOrNull()
            if (address?.countryCode != null && address.countryName != null) {
                address.countryCode to address.countryName
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    private suspend fun calculateTotalDistance(trips: List<Trip>): Double = withContext(Dispatchers.IO) {
        var totalDist = 0.0
        val tripDao = (requireActivity().application as PlacesApplication).database.tripDao()

        trips.forEach { trip ->
            // 1. Try GPS locations first
            val locations = tripDao.getLocationsForTripSync(trip.id)
            if (locations.size > 1) {
                for (i in 0 until locations.size - 1) {
                    val loc1 = locations[i]
                    val loc2 = locations[i + 1]
                    val results = FloatArray(1)
                    try {
                        Location.distanceBetween(loc1.latitude, loc1.longitude, loc2.latitude, loc2.longitude, results)
                        totalDist += results[0]
                    } catch (e: Exception) {}
                }
            } else {
                // 2. Fallback: Use Stopps with coordinates
                val stops = tripDao.getStopsForTripSync(trip.id).sortedBy { it.date }
                val validCoords = stops.mapNotNull { stop ->
                    stop.location?.split(",")?.let { coords ->
                        if (coords.size == 2) {
                            val lat = coords[0].toDoubleOrNull()
                            val lon = coords[1].toDoubleOrNull()
                            if (lat != null && lon != null) lat to lon else null
                        } else null
                    }
                }
                
                if (validCoords.size > 1) {
                    for (i in 0 until validCoords.size - 1) {
                        val p1 = validCoords[i]
                        val p2 = validCoords[i + 1]
                        val results = FloatArray(1)
                        try {
                            Location.distanceBetween(p1.first, p1.second, p2.first, p2.second, results)
                            totalDist += results[0]
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        totalDist
    }
}
