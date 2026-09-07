package com.d_drostes_apps.placestracker.ui.feed

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.utils.SharingManager
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class EntryDetailFragment : Fragment(R.layout.fragment_entry_detail) {

    private var entry: Entry? = null
    private lateinit var llFlags: LinearLayout
    private lateinit var cvCountryName: MaterialCardView
    private lateinit var tvCountryNamePopup: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val entryId = arguments?.getInt("entryId") ?: return
        val activity = activity ?: return
        val app = (activity.application as PlacesApplication)
        val repository = app.repository
        val userDao = app.userDao

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val tvTitle = view.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvDetailDate)
        val tvNotes = view.findViewById<TextView>(R.id.tvDetailNotes)
        val cvNotes = view.findViewById<MaterialCardView>(R.id.cvDetailNotes)
        val rvMedia = view.findViewById<RecyclerView>(R.id.rvDetailMedia)
        val appBar = view.findViewById<AppBarLayout>(R.id.appBar)
        // Hero-Galerie (fancy Bildershow statt Karte)
        val heroPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.heroMediaPager)
        val heroDots = view.findViewById<LinearLayout>(R.id.heroDotsIndicator)
        val tvMediaCounter = view.findViewById<TextView>(R.id.tvMediaCounter)
        
        llFlags = view.findViewById(R.id.llDetailFlags)
        cvCountryName = view.findViewById(R.id.cvCountryName)
        tvCountryNamePopup = view.findViewById(R.id.tvCountryNamePopup)
        
        // Keine eigene Karte mehr: Der Globe oben im Dashboard zeigt den Ort
        // (zoomGlobeTo wird nach dem Laden des Entries aufgerufen)
        val isInline = parentFragment is FeedFragment
        if (isInline) {
            // Karte bleibt sichtbar: Der Feed-Globe wird für Details wiederverwendet statt eines zweiten WebGL-Kontexts
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener {
                (parentFragment as? FeedFragment)?.handleBack()
            }
            // Inline: keinen eigenen WebGL-Kontext starten — der Feed-Globe zeigt den Ort,
            // der Zoom passiert nach dem Laden des Entries (siehe Lade-Block unten).
            view.findViewById<View>(R.id.drag_handle)?.visibility = View.GONE
            // Increase top padding to account for missing appBar space if needed
            view.findViewById<View>(R.id.llDetailContent)?.setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                0,
                (16 * resources.displayMetrics.density).toInt(),
                (100 * resources.displayMetrics.density).toInt()
            )
        } else {
            toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        }

        // Animation für den Content beim Laden
        view.findViewById<View>(R.id.llDetailContent)?.apply {
            alpha = 0f
            translationY = 50f
            animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100).start()
        }

        view.findViewById<View>(R.id.detailRootLayout).setOnClickListener {
            cvCountryName.visibility = View.GONE
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

                val sdf = SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault())
                tvDate.text = sdf.format(Date(e.date))

                rvMedia.layoutManager = GridLayoutManager(requireContext(), 3)
                rvMedia.adapter = DetailMediaAdapter(e.media) { path, transitionView ->
                    val dialog = MediaDialogFragment().apply {
                        arguments = Bundle().apply {
                            putStringArrayList("mediaPaths", ArrayList(e.media))
                            putInt("initialPosition", e.media.indexOf(path))
                        }
                    }
                    dialog.show(parentFragmentManager, "MediaFullscreen")
                }

                // Hero-Galerie: fancy Bildershow mit Dots + Zähler
                if (e.media.isNotEmpty()) {
                    heroPager.adapter = HeroMediaAdapter(e.media) { path ->
                        val dialog = MediaDialogFragment().apply {
                            arguments = Bundle().apply {
                                putStringArrayList("mediaPaths", ArrayList(e.media))
                                putInt("initialPosition", e.media.indexOf(path))
                            }
                        }
                        dialog.show(parentFragmentManager, "MediaFullscreen")
                    }
                    heroDots.removeAllViews()
                    val dotSize = (7 * resources.displayMetrics.density).toInt()
                    val dotMargin = (4 * resources.displayMetrics.density).toInt()
                    e.media.indices.forEach { i ->
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
                    tvMediaCounter.text = "1/${e.media.size}"
                    tvMediaCounter.visibility = View.VISIBLE
                } else {
                    heroPager.visibility = View.GONE
                    view.findViewById<View>(R.id.cvHeroMedia)?.visibility = View.GONE
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
                loadCountryFlag(e.location)

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
                    findNavController().navigateUp()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    private fun toggleFullscreen(cardMap: MaterialCardView, appBar: AppBarLayout, btn: android.widget.ImageButton) {
        // Karte aus der Detailansicht entfernt (Globe oben übernimmt) — Funktion bewusst deaktiviert
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
                addr.countryCode to addr.countryName
            } else null
        } catch (e: Exception) { null }
    }

    private fun getFlagEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
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
                            findNavController().navigateUp()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}