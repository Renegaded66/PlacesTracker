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

    /**
     * tripId des geladenen Stops — wird im Async-Load gesetzt. Der Zurück-Pfeil
     * braucht den Trip-Kontext, kann aber vor dem Load-Ende getippt werden;
     * deshalb zusätzlich DAO-Fallback im Click-Handler (race-sicher).
     */
    private var loadedTripId: Int? = null
    private lateinit var tvFlag: TextView
    private lateinit var tvCountryName: TextView

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
        
        tvFlag = view.findViewById(R.id.tvFlag)
        tvCountryName = view.findViewById(R.id.tvCountryName)

        // Info-Icons themefarben tinten (wie in EntryDetailFragment)
        val onSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
        view.findViewById<ImageView>(R.id.ivCalendarIcon)?.setColorFilter(onSurfaceVariant)
        view.findViewById<ImageView>(R.id.ivGlobeHintIcon)?.setColorFilter(onSurfaceVariant)
        
        val isInline = parentFragment is FeedFragment
        if (isInline) {
            // Keine eigene Karte: Der Globe oben im Dashboard zeigt den Stop
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener {
                // Stop-Detail vom Dashboard: zurück zum TRIP-DETAIL (nicht Dashboard schließen).
                // Liegt das Trip-Detail im Inline-Stack (Detail → Stop), einfach poppen;
                // sonst (Stop direkt vom Dashboard/Globe) das Trip-Detail inline öffnen.
                lifecycleScope.launch {
                    val tripId = loadedTripId ?: tripDao.getStopById(stopId)?.tripId
                    if (tripId != null) {
                        if (parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else {
                            (parentFragment as? FeedFragment)?.navigateToTripDetail(tripId)
                        }
                    } else if (isAdded) {
                        dismiss()
                    }
                }
            }
            // Bottom-Puffer + Globe-Hint (wie in EntryDetailFragment)
            view.findViewById<View>(R.id.llDetailContent)?.setPadding(0, 0, 0, (110 * resources.displayMetrics.density).toInt())
            view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvGlobeHint)?.visibility = View.VISIBLE
        } else {
            toolbar.setNavigationOnClickListener {
                // Zurück zum zugehörigen Trip-Detail (explizit, unabhängig vom Tab,
                // aus dem das Stop-Detail geöffnet wurde). Alte TripDetail-Instanz
                // wird per popUpTo im NavGraph-Action entfernt → kein Doppel-Detail.
                lifecycleScope.launch {
                    val tripId = loadedTripId ?: tripDao.getStopById(stopId)?.tripId
                    if (tripId != null) {
                        val bundle = Bundle().apply {
                            putInt("tripId", tripId)
                        }
                        dismiss()
                        (activity.supportFragmentManager
                            .findFragmentById(R.id.nav_host_fragment) as? androidx.navigation.fragment.NavHostFragment)
                            ?.navController
                            ?.navigate(R.id.action_tripStopDetailFragment_to_tripDetailFragment, bundle)
                    } else if (isAdded) {
                        dismiss()
                    }
                }
            }
        }

        // Schwebende Toolbar via DetailChrome (geteilt mit EntryDetailFragment)
        com.d_drostes_apps.placestracker.utils.DetailChrome.attach(view)
        com.d_drostes_apps.placestracker.utils.DetailChrome.tint(toolbar, dark = false)

        // Animation für den Content
        view.findViewById<View>(R.id.llDetailContent)?.apply {
            alpha = 0f
            translationY = 40f
            animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100).start()
        }

        view.findViewById<View>(R.id.detailRootLayout).setOnClickListener {
            // Kein Country-Popup mehr im Redesign (Land steht direkt in der Ortszeile)
        }

        lifecycleScope.launch {
            val dbStop = tripDao.getStopById(stopId)
            dbStop?.let {
                stop = it
                loadedTripId = it.tripId
                tvTitle.text = it.title
                
                if (!it.notes.isNullOrBlank()) {
                    tvNotes.text = it.notes
                    cvNotes.visibility = View.VISIBLE
                } else {
                    cvNotes.visibility = View.GONE
                }
                
                // Editorial-Datum: "Sonntag, 12. Mai 2024  ·  14:30"
                val dateOnly = java.text.SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.getDefault())
                val timeOnly = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateObj = Date(it.date)
                tvDate.text = dateOnly.format(dateObj) + "  ·  " + timeOnly.format(dateObj)

                // Hero-Galerie: fancy Bildershow mit morphenden Dots + Zähler (einzige Medien-Ansicht)
                if (it.media.isNotEmpty()) {
                    heroPager.visibility = View.VISIBLE
                    heroPager.adapter = HeroMediaAdapter(it.media) { path ->
                        val dialog = MediaDialogFragment().apply {
                            arguments = Bundle().apply {
                                putStringArrayList("mediaPaths", ArrayList(it.media))
                                putInt("initialPosition", it.media.indexOf(path))
                            }
                        }
                        dialog.show(parentFragmentManager, "MediaFullscreen")
                    }
                    setupPillDots(heroDots, it.media.size, heroPager, tvMediaCounter)
                    tvMediaCounter.visibility = View.VISIBLE
                } else {
                    // Leer-Zustand statt unsichtbarem Loch
                    heroPager.visibility = View.GONE
                    tvMediaCounter.visibility = View.GONE
                    val heroCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvHeroMedia)
                    val heroFrame = heroCard.getChildAt(0) as? android.widget.FrameLayout
                    if (heroFrame != null && heroFrame.findViewById<View>(R.id.emptyHero) == null) {
                        layoutInflater.inflate(R.layout.view_empty_hero, heroFrame, true)
                    }
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
                tvFlag.text = getFlagEmoji(code)
                tvCountryName.text = name.uppercase(Locale.getDefault())
            }
        }
    }

    /**
     * Instagram-Stil Dots: aktiver Dot ist eine weisse Pille, inaktive Punkte sind
     * halbtransparent. Morph-Animation (Breite + Alpha) beim Seitenwechsel.
     */
    private fun setupPillDots(
        heroDots: LinearLayout,
        count: Int,
        heroPager: androidx.viewpager2.widget.ViewPager2,
        tvMediaCounter: TextView
    ) {
        heroDots.removeAllViews()
        val density = resources.displayMetrics.density
        val dotSize = (7 * density).toInt()
        val pillWidth = (22 * density).toInt()
        val dotMargin = (3 * density).toInt()

        fun styleDot(dot: View, active: Boolean, animate: Boolean) {
            val targetWidth = if (active) pillWidth else dotSize
            val lp = dot.layoutParams as LinearLayout.LayoutParams
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 99f * density
                setColor(if (active) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#80FFFFFF"))
            }
            if (animate && lp.width != targetWidth) {
                val from = lp.width
                dot.animate().alpha(if (active) 1f else 0.4f).setDuration(200).start()
                android.animation.ValueAnimator.ofInt(from, targetWidth).apply {
                    duration = 220
                    addUpdateListener { animator ->
                        lp.width = animator.animatedValue as Int
                        dot.layoutParams = lp
                    }
                    start()
                }
            } else {
                lp.width = targetWidth
                dot.alpha = if (active) 1f else 0.4f
                dot.layoutParams = lp
            }
        }

        for (i in 0 until count) {
            val dot = View(requireContext())
            val lp = LinearLayout.LayoutParams(if (i == 0) pillWidth else dotSize, dotSize)
            lp.setMargins(dotMargin, 0, dotMargin, 0)
            dot.layoutParams = lp
            heroDots.addView(dot)
        }
        for (i in 0 until count) {
            styleDot(heroDots.getChildAt(i), i == 0, animate = false)
        }

        heroPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until heroDots.childCount) {
                    styleDot(heroDots.getChildAt(i), i == position, animate = true)
                }
                tvMediaCounter.text = "${position + 1}/$count"
            }
        })
        tvMediaCounter.text = "1/$count"
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