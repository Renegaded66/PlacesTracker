package com.d_drostes_apps.placestracker.ui.feed

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.utils.SharingManager
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class EntryDetailFragment : Fragment(R.layout.fragment_entry_detail) {

    private var entry: Entry? = null

    companion object {
        private const val HERO_HEIGHT_DP = 400f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val entryId = arguments?.getInt("entryId") ?: return
        val activity = activity ?: return
        val app = (activity.application as PlacesApplication)
        val repository = app.repository
        val userDao = app.userDao

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val appBar = view.findViewById<AppBarLayout>(R.id.appBar)
        val tvTitle = view.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvDetailDate)
        val tvNotes = view.findViewById<TextView>(R.id.tvDetailNotes)
        val cvNotes = view.findViewById<MaterialCardView>(R.id.cvDetailNotes)
        val tvFlag = view.findViewById<TextView>(R.id.tvFlag)
        val tvCountryName = view.findViewById<TextView>(R.id.tvCountryName)
        val heroPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.heroMediaPager)
        val heroDots = view.findViewById<LinearLayout>(R.id.heroDotsIndicator)
        val tvMediaCounter = view.findViewById<TextView>(R.id.tvMediaCounter)
        val cvGlobeHint = view.findViewById<MaterialCardView>(R.id.cvGlobeHint)

        // Info-Icons themefarben tinten (XML-Attr imageTintList ist hier nicht linkbar)
        val onSurfaceVariant = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
        view.findViewById<android.widget.ImageView>(R.id.ivCalendarIcon)?.setColorFilter(onSurfaceVariant)
        view.findViewById<android.widget.ImageView>(R.id.ivGlobeHintIcon)?.setColorFilter(onSurfaceVariant)

        // Keine eigene Karte mehr: Der Globe oben im Dashboard zeigt den Ort
        // (zoomGlobeTo wird nach dem Laden des Entries aufgerufen)
        val isInline = parentFragment is FeedFragment
        if (isInline) {
            // Karte bleibt sichtbar: Der Feed-Globe wird für Details wiederverwendet statt eines
            // zweiten WebGL-Kontexts. Drag-Handle bleibt sichtbar: signalisiert Ziehbarkeit.
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener {
                (parentFragment as? FeedFragment)?.handleBack()
            }
            // Bottom-Puffer inline: Space fuer Globe-Hint + Scrollkomfort
            view.findViewById<View>(R.id.llDetailContent)?.setPadding(0, 0, 0, (110 * resources.displayMetrics.density).toInt())
            // Globe-Hint einblenden: laedt ein, das Sheet nach unten zu ziehen
            cvGlobeHint.visibility = View.VISIBLE
        } else {
            toolbar.setNavigationOnClickListener {
                com.d_drostes_apps.placestracker.utils.BackNavigation.toDashboard(findNavController())
            }
        }

        // Schwebende Toolbar via DetailChrome: Hero laeuft dahinter durch, Scrim blendet ein
        com.d_drostes_apps.placestracker.utils.DetailChrome.attach(view)
        com.d_drostes_apps.placestracker.utils.DetailChrome.tint(toolbar, dark = false)

        // Globe-Hint: sanfter Puls + Tap zieht das Sheet nach unten (Globe voll sichtbar)
        if (isInline) {
            cvGlobeHint.alpha = 0f
            cvGlobeHint.scaleX = 0.9f
            cvGlobeHint.scaleY = 0.9f
            val pulse = android.view.animation.AccelerateDecelerateInterpolator()
            cvGlobeHint.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(400L)
                .setStartDelay(700L)
                .setInterpolator(pulse)
                .withEndAction {
                    cvGlobeHint.animate()
                        .scaleX(1.05f).scaleY(1.05f)
                        .setDuration(600L).setStartDelay(900L)
                        .setInterpolator(pulse)
                        .withEndAction {
                            cvGlobeHint.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(600L)
                                .setInterpolator(pulse)
                                .start()
                        }
                        .start()
                }
                .start()
            cvGlobeHint.setOnClickListener {
                // Detail-Sheet nach unten ziehen: Globe kommt voll zum Vorschein.
                // Das Sheet ist das PARENT des Detail-Fragments (BottomSheetBehavior auf dem
                // FragmentContainerView in fragment_feed.xml) — nicht Teil dieses Layouts.
                val sheet = view.parent as? View
                if (sheet != null) {
                    try {
                        val behavior = BottomSheetBehavior.from(sheet)
                        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    } catch (_: Exception) {
                        // Fullscreen-Modus: kein Sheet vorhanden
                    }
                }
            }
        }

        // Content-Fade-In beim Laden (sanft)
        view.findViewById<View>(R.id.llDetailContent)?.apply {
            alpha = 0f
            translationY = 24f
            animate().alpha(1f).translationY(0f).setDuration(450).setStartDelay(100)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)).start()
        }

        view.findViewById<View>(R.id.detailRootLayout).setOnClickListener {
            // Kein Country-Popup mehr im Redesign (Land steht direkt in der Ortszeile)
        }

        toolbar.inflateMenu(R.menu.menu_entry_detail)

        lifecycleScope.launch {
            userDao.getUserProfile().collectLatest { profile ->
                profile?.themeColor?.let { color ->
                    ThemeHelper.applyThemeColor(view, color)
                }
            }
        }

        lifecycleScope.launch {
            val dbEntry = app.database.entryDao().getEntryById(entryId)
            dbEntry?.let { e ->
                entry = e
                tvTitle.text = e.title

                if (!e.notes.isNullOrBlank()) {
                    tvNotes.text = e.notes
                    cvNotes.visibility = View.VISIBLE
                } else {
                    cvNotes.visibility = View.GONE
                }

                // Hide edit if it's a shared entry, show add button instead
                val isShared = e.friendId != null
                toolbar.menu.findItem(R.id.action_edit)?.isVisible = !isShared
                toolbar.menu.findItem(R.id.action_add_shared)?.isVisible = isShared

                // Add share button to menu
                toolbar.menu.add(0, R.id.action_share, 0, "Teilen").apply {
                    setIcon(R.drawable.ic_share)
                    setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                    isVisible = !isShared // Only share own entries
                }

                // Editorial-Datum: "Sonntag, 12. Mai 2024 · 14:30"
                val dateOnly = SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.getDefault())
                val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateObj = Date(e.date)
                tvDate.text = dateOnly.format(dateObj) + "  ·  " + timeOnly.format(dateObj)

                // Hero-Galerie: fancy Bildershow mit morphenden Dots + Zaehler (einzige Medien-Ansicht)
                if (e.media.isNotEmpty()) {
                    heroPager.visibility = View.VISIBLE
                    heroPager.adapter = HeroMediaAdapter(e.media) { path ->
                        val dialog = MediaDialogFragment().apply {
                            arguments = Bundle().apply {
                                putStringArrayList("mediaPaths", ArrayList(e.media))
                                putInt("initialPosition", e.media.indexOf(path))
                            }
                        }
                        dialog.show(parentFragmentManager, "MediaFullscreen")
                    }
                    setupPillDots(heroDots, e.media.size, heroPager, tvMediaCounter)
                    tvMediaCounter.visibility = View.VISIBLE
                } else {
                    // Leer-Zustand: freundliche Einladung statt unsichtbarem Loch
                    heroPager.visibility = View.GONE
                    tvMediaCounter.visibility = View.GONE
                    val heroCard = view.findViewById<MaterialCardView>(R.id.cvHeroMedia)
                    val heroFrame = heroCard.getChildAt(0) as? android.widget.FrameLayout
                    if (heroFrame != null && heroFrame.findViewById<View>(R.id.emptyHero) == null) {
                        layoutInflater.inflate(R.layout.view_empty_hero, heroFrame, true)
                    }
                }

                // Karte in beiden Modi mit Position versorgen (inline + fullscreen)
                updateGlobePosition()
                if (isInline) {
                    // Inline zusätzlich: Feed-Globe auf den Ort zoomen
                    e.location?.split(",")?.let { coords ->
                        if (coords.size == 2) {
                            val lat = coords[0].trim().toDoubleOrNull()
                            val lon = coords[1].trim().toDoubleOrNull()
                            if (lat != null && lon != null) {
                                (parentFragment as? FeedFragment)?.zoomGlobeTo(lat, lon)
                            }
                        }
                    }
                }
                loadCountryFlag(e.location, tvFlag, tvCountryName)

                // Weather display
                val ivWeatherIcon = view.findViewById<android.widget.ImageView>(R.id.ivWeatherIcon)
                val tvWeatherTemp = view.findViewById<TextView>(R.id.tvWeatherTemp)

                if (!e.location.isNullOrBlank()) {
                    val coords = e.location.split(",")
                    if (coords.size == 2) {
                        val lat = coords[0].toDoubleOrNull()
                        val lon = coords[1].toDoubleOrNull()

                        if (lat != null && lon != null) {
                            lifecycleScope.launch {
                                val weather = app.weatherRepository.getWeather(
                                    lat,
                                    lon,
                                    e.date,
                                    com.d_drostes_apps.placestracker.BuildConfig.OPENWEATHER_KEY
                                )

                                ivWeatherIcon.setImageResource(
                                    com.d_drostes_apps.placestracker.data.WeatherIconMapper
                                        .getIconResId(weather.iconCode)
                                )
                                tvWeatherTemp.text = "${weather.temperature.toInt()}°C"
                            }
                        }
                    }
                }
            }
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmation(entry, repository)
                    true
                }
                R.id.action_edit -> {
                    entry?.let {
                        val bundle = Bundle().apply {
                            putInt("entryId", it.id)
                            putString("title", getString(R.string.edit_entry))
                        }
                        // 🌟 FIX: Prüfen, ob es ein Tagebuch ist, und das richtige Fragment öffnen!
                        if (it.entryType == "diary") {
                            findNavController().navigate(R.id.newDiaryEntryFragment, bundle)
                        } else {
                            findNavController().navigate(R.id.newEntryFragment, bundle)
                        }
                    }
                    true
                }
                R.id.action_share -> {
                    handleShare()
                    true
                }
                R.id.action_add_shared -> {
                    showAddSharedConfirmation()
                    true
                }
                else -> false
            }
        }
    }

    /** Toolbar-Tint: Weiss ueber dem Hero, themenfarben nach dem Fade-in. */
    private fun applyToolbarTint(toolbar: MaterialToolbar, dark: Boolean) {
        val navIcon = toolbar.navigationIcon ?: return
        if (dark) {
            navIcon.mutate().setTint(MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface))
        } else {
            navIcon.mutate().setTint(Color.WHITE)
        }
        val onSurface = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        for (i in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(i).icon?.mutate()?.let { icon ->
                if (dark) icon.setTint(onSurface) else icon.setTint(Color.WHITE)
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
            dot.background = GradientDrawable().apply {
                cornerRadius = 99f * density
                setColor(if (active) Color.WHITE else Color.parseColor("#80FFFFFF"))
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

        // Dots anlegen: aktiver Dot startet als Pille, inaktive als Kreis
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

    private fun loadCountryFlag(location: String?, tvFlag: TextView, tvCountryName: TextView) {
        if (location.isNullOrBlank()) return
        lifecycleScope.launch {
            val info = getCountryInfo(requireContext(), location)
            info?.let { (code, name) ->
                tvFlag.text = getFlagEmoji(code)
                tvCountryName.text = name.uppercase(Locale.getDefault())
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
                addr.countryCode to addr.countryName
            } else null
        } catch (e: Exception) { null }
    }

    private fun getFlagEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    private fun handleShare() {
        val currentEntry = entry ?: return
        val app = (requireActivity().application as PlacesApplication)
        lifecycleScope.launch {
            val profile = app.userDao.getUserProfile().first()
            if (profile != null) {
                val sharingManager = SharingManager(requireContext(), app.database)
                sharingManager.shareEntry(currentEntry, profile)
            } else {
                Toast.makeText(requireContext(), "Bitte erstelle zuerst ein Profil", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddSharedConfirmation() {
        val currentEntry = entry ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_my_feed)
            .setMessage(R.string.add_to_my_feed_confirm_entry)
            .setPositiveButton(R.string.save) { _, _ ->
                lifecycleScope.launch {
                    val app = (requireActivity().application as PlacesApplication)
                    val newEntry = currentEntry.copy(id = 0, friendId = null)
                    app.database.entryDao().insert(newEntry)
                    Toast.makeText(requireContext(), R.string.added_to_my_feed_success, Toast.LENGTH_SHORT).show()
                    com.d_drostes_apps.placestracker.utils.BackNavigation.toDashboard(findNavController())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Ort auf dem FEED-Globe anzeigen (der Globe oben im Dashboard ist die Karte). */
    private fun updateGlobePosition() {
        lifecycleScope.launch {
            val currentEntry = entry ?: return@launch
            val coords = currentEntry.location?.split(",") ?: return@launch
            if (coords.size == 2) {
                val lat = coords[0].trim().toDoubleOrNull() ?: return@launch
                val lon = coords[1].trim().toDoubleOrNull() ?: return@launch
                (parentFragment as? FeedFragment)?.zoomGlobeTo(lat, lon)
            }
        }
    }

    private fun showDeleteConfirmation(entry: Entry?, repository: com.d_drostes_apps.placestracker.data.EntryRepository) {
        entry?.let { e ->
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.entry_delete_confirm_title)
                .setMessage(R.string.entry_delete_confirm_msg)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        repository.delete(e)
                        if (parentFragment is FeedFragment) {
                            (parentFragment as FeedFragment).handleBack()
                        } else {
                            com.d_drostes_apps.placestracker.utils.BackNavigation.toDashboard(findNavController())
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}