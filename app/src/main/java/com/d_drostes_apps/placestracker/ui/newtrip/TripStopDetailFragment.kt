package com.d_drostes_apps.placestracker.ui.newtrip

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import kotlin.math.roundToInt
import com.d_drostes_apps.placestracker.data.WeatherIconMapper

import androidx.lifecycle.lifecycleScope
import com.d_drostes_apps.placestracker.BuildConfig
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.ui.feed.FeedFragment
import com.d_drostes_apps.placestracker.ui.feed.HeroMediaAdapter
import com.d_drostes_apps.placestracker.data.WeatherRepository
import com.d_drostes_apps.placestracker.ui.feed.MediaDialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class TripStopDetailFragment : BottomSheetDialogFragment() {

    private var stop: TripStop? = null
    private lateinit var llFlags: LinearLayout
    private lateinit var cvCountryName: View
    private lateinit var tvCountryNamePopup: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_entry_detail, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val stopId = arguments?.getInt("stopId") ?: return
        val activity = activity ?: return
        val app = (activity.application as PlacesApplication)
        val tripDao = app.database.tripDao()

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val tvTitle = view.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvDetailDate)
        val tvNotes = view.findViewById<TextView>(R.id.tvDetailNotes)
        val cvNotes = view.findViewById<MaterialCardView>(R.id.cvDetailNotes)
        // Hero-Galerie (fancy Bildershow statt Karte)
        val heroPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.heroMediaPager)
        val heroDots = view.findViewById<LinearLayout>(R.id.heroDotsIndicator)
        val tvMediaCounter = view.findViewById<TextView>(R.id.tvMediaCounter)
        
        llFlags = view.findViewById(R.id.llDetailFlags)
        cvCountryName = view.findViewById(R.id.cvCountryName)
        tvCountryNamePopup = view.findViewById(R.id.tvCountryNamePopup)
        
        val isInline = parentFragment is FeedFragment
        if (isInline) {
            // Keine eigene Karte: Der Globe oben im Dashboard zeigt den Stop
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener {
                (parentFragment as? FeedFragment)?.handleBack()
            }
        } else {
            toolbar.setNavigationOnClickListener {
                stop?.let { s ->
                    val bundle = Bundle().apply {
                        putInt("tripId", s.tripId)
                    }
                    dismiss()
                    (activity.supportFragmentManager
                        .findFragmentById(R.id.nav_host_fragment) as? androidx.navigation.fragment.NavHostFragment)
                        ?.navController
                        ?.navigate(R.id.action_tripStopDetailFragment_to_tripDetailFragment, bundle)
                } ?: run {
                    if (isAdded) dismiss()
                }
            }
        }

        // Animation für den Content
        view.findViewById<View>(R.id.llDetailContent)?.apply {
            alpha = 0f
            translationY = 40f
            animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100).start()
        }

        view.findViewById<View>(R.id.detailRootLayout).setOnClickListener {
            cvCountryName.visibility = View.GONE
        }

        lifecycleScope.launch {
            val dbStop = tripDao.getStopById(stopId)
            dbStop?.let { 
                stop = it
                tvTitle.text = it.title
                
                if (!it.notes.isNullOrBlank()) {
                    tvNotes.text = it.notes
                    cvNotes.visibility = View.VISIBLE
                } else {
                    cvNotes.visibility = View.GONE
                }
                
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault())
                tvDate.text = sdf.format(Date(it.date))

                // Hero-Galerie: fancy Bildershow mit Dots + Zähler (einzige Medien-Ansicht)
                if (it.media.isNotEmpty()) {
                    heroPager.adapter = HeroMediaAdapter(it.media) { path ->
                        val dialog = MediaDialogFragment().apply {
                            arguments = Bundle().apply {
                                putStringArrayList("mediaPaths", ArrayList(it.media))
                                putInt("initialPosition", it.media.indexOf(path))
                            }
                        }
                        dialog.show(parentFragmentManager, "MediaFullscreen")
                    }
                    heroDots.removeAllViews()
                    val dotSize = (7 * resources.displayMetrics.density).toInt()
                    val dotMargin = (4 * resources.displayMetrics.density).toInt()
                    it.media.indices.forEach { i ->
                        val dot = View(requireContext())
                        dot.setBackgroundResource(R.drawable.bg_dot)
                        val lp = LinearLayout.LayoutParams(dotSize, dotSize)
                        lp.setMargins(dotMargin, 0, dotMargin, 0)
                        dot.layoutParams = lp
                        dot.alpha = if (i == 0) 1f else 0.35f
                        heroDots.addView(dot)
                    }
                    heroPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            for (i in 0 until heroDots.childCount) {
                                heroDots.getChildAt(i).alpha = if (i == position) 1f else 0.35f
                            }
                            tvMediaCounter.text = "${position + 1}/${heroDots.childCount}"
                        }
                    })
                    tvMediaCounter.text = "1/${it.media.size}"
                    tvMediaCounter.visibility = View.VISIBLE
                } else {
                    heroPager.visibility = View.GONE
                    view.findViewById<View>(R.id.cvHeroMedia)?.visibility = View.GONE
                }

                // Karte in beiden Modi mit Position versorgen (inline + fullscreen)
                updateGlobePosition()
                if (isInline) {
                    // Inline zusätzlich: Feed-Globe auf den Stop zoomen
                    it.location?.split(",")?.let { coords ->
                        if (coords.size == 2) {
                            val lat = coords[0].trim().toDoubleOrNull()
                            val lon = coords[1].trim().toDoubleOrNull()
                            if (lat != null && lon != null) {
                                (parentFragment as? FeedFragment)?.zoomGlobeTo(lat, lon)
                            }
                        }
                    }
                }
                loadCountryFlag(it.location)

                // Weather display for this stop
                val ivWeatherIcon = view.findViewById<ImageView>(R.id.ivWeatherIcon)
                val tvWeatherTemp = view.findViewById<TextView>(R.id.tvWeatherTemp)

                if (!it.location.isNullOrBlank()) {
                    val coords = it.location.split(",")
                    if (coords.size == 2) {
                        val lat = coords[0].toDoubleOrNull()
                        val lon = coords[1].toDoubleOrNull()

                        if (lat != null && lon != null) {
                            lifecycleScope.launch {
                                val weather = app.weatherRepository.getWeather(
                                    lat,
                                    lon,
                                    it.date,
                                    BuildConfig.OPENWEATHER_KEY
                                )
                                ivWeatherIcon.setImageResource(WeatherIconMapper.getIconResId(weather.iconCode))
                                tvWeatherTemp.text = "${weather.temperature.roundToInt()}°C"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadCountryFlag(location: String?) {
        if (location.isNullOrBlank()) return
        lifecycleScope.launch {
            val info = getCountryInfo(requireContext(), location)
            info?.let { (code, name) ->
                val flagView = TextView(requireContext()).apply {
                    text = getFlagEmoji(code)
                    textSize = 24f
                    setOnClickListener {
                        tvCountryNamePopup.text = name
                        cvCountryName.visibility = View.VISIBLE
                    }
                }
                llFlags.removeAllViews()
                llFlags.addView(flagView)
            }
        }
    }

    private suspend fun getCountryInfo(context: Context, location: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val coords = location.split(",")
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(coords[0].toDouble(), coords[1].toDouble(), 1)
            val addr = addresses?.firstOrNull()
            if (addr?.countryCode != null && addr.countryName != null) {
                addr.countryCode!! to addr.countryName!!
            } else null
        } catch (e: Exception) { null }
    }

    private fun getFlagEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    /** Ort auf dem FEED-Globe anzeigen (Inline) — eigene Karte gibt es nicht mehr. */
    private fun updateGlobePosition() {
        lifecycleScope.launch {
            val stopItem = stop ?: return@launch
            val coords = stopItem.location?.split(",") ?: return@launch
            if (coords.size == 2) {
                val lat = coords[0].trim().toDoubleOrNull() ?: return@launch
                val lon = coords[1].trim().toDoubleOrNull() ?: return@launch
                (parentFragment as? FeedFragment)?.zoomGlobeTo(lat, lon)
            }
        }
    }
}