package com.d_drostes_apps.placestracker.ui.statistics

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.data.TripStop
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        // Calculate Countries and Flags
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

        // Calculate Total Distance
        lifecycleScope.launch {
            val totalDistance = calculateTotalDistance(trips)
            tvTotalDistance.text = String.format(Locale.getDefault(), "%.1f", totalDistance / 1000.0)
        }
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
            val locations = tripDao.getLocationsForTripSync(trip.id)
            if (locations.size > 1) {
                for (i in 0 until locations.size - 1) {
                    val loc1 = locations[i]
                    val loc2 = locations[i + 1]
                    val results = FloatArray(1)
                    Location.distanceBetween(loc1.latitude, loc1.longitude, loc2.latitude, loc2.longitude, results)
                    totalDist += results[0]
                }
            }
        }
        totalDist
    }
}
