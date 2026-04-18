package com.d_drostes_apps.placestracker.ui.feed

import android.annotation.SuppressLint
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.FeedItem
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.ui.newtrip.TripDetailFragment
import com.d_drostes_apps.placestracker.ui.newtrip.TripStopDetailFragment
import com.d_drostes_apps.placestracker.utils.GlobeUtils
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class FeedFragment : Fragment(R.layout.fragment_feed) {

    private lateinit var recycler: RecyclerView
    private lateinit var cesiumWebView: WebView
    private var adapter: FeedAdapter? = null
    private var isFabMenuOpen = false
    private var lastItems: List<FeedItem> = emptyList()

    private var filterType: String? = null
    private var filterDateStart: Long? = null
    private var filterDateEnd: Long? = null
    private var filterCountries: MutableSet<String> = mutableSetOf()
    private var searchQuery: String = ""
    private val geocoderCache = mutableMapOf<String, Pair<String?, String?>>()

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var lastZoomedId: String? = null

    private lateinit var feedListLayout: View
    private lateinit var detailContainer: View

    private val autoTripPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            showAutoTripConfirmation(uris)
        }
    }

    fun getGlobe(): WebView = cesiumWebView

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = (requireActivity().application as PlacesApplication)
        val repository = app.repository
        val tripRepository = app.tripRepository
        val userDao = app.userDao

        feedListLayout = view.findViewById(R.id.feedListLayout)
        detailContainer = view.findViewById(R.id.detailFragmentContainer)

        val ivGlobalAvatar = view.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.ivGlobalUserAvatar)
        val tvGlobalUsername = view.findViewById<TextView>(R.id.tvGlobalUsername)
        val tvGlobalFlag = view.findViewById<TextView>(R.id.tvGlobalUserFlag)
        val btnFriends = view.findViewById<MaterialButton>(R.id.btnFriends)
        val btnTimelineGallery = view.findViewById<ImageButton>(R.id.btnTimelineGallery)

        btnFriends.setOnClickListener {
            findNavController().navigate(R.id.action_feedFragment_to_friendsFragment)
        }

        btnTimelineGallery.setOnClickListener {
            findNavController().navigate(R.id.timelineGalleryFragment)
        }

        // --- Cesium 3D Globe Setup ---
        cesiumWebView = view.findViewById(R.id.feedCesiumWebView)
        cesiumWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        cesiumWebView.settings.loadsImagesAutomatically = true
        cesiumWebView.settings.blockNetworkImage = false
        setupCesiumWebView()

        // --- Bottom Sheet Setup ---
        val bottomSheet = view.findViewById<View>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            isHideable = false
            peekHeight = (resources.displayMetrics.density * 120).toInt()
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
            halfExpandedRatio = 0.6f
            isFitToContents = false
        }

        recycler = view.findViewById(R.id.feedRecycler)

        // 1. Größe beim Wischen anpassen
        bottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val layoutParams = cesiumWebView.layoutParams
                layoutParams.height = bottomSheet.top
                cesiumWebView.layoutParams = layoutParams
            }
        })

        // 2. Größe beim allerersten Layout-Aufbau anpassen
        view.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val sheetTop = bottomSheet.top
                if (sheetTop > 0) {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val layoutParams = cesiumWebView.layoutParams
                    layoutParams.height = sheetTop
                    cesiumWebView.layoutParams = layoutParams
                }
            }
        })

        recycler.setOnTouchListener { _, event ->
            val behavior = bottomSheetBehavior ?: return@setOnTouchListener false
            if (behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                behavior.isDraggable = false
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    behavior.isDraggable = true
                }
            } else if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                behavior.isDraggable = !recycler.canScrollVertically(-1)
            }
            false
        }

        view.findViewById<View>(R.id.sheetHeaderArea)?.setOnTouchListener { _, _ ->
            bottomSheetBehavior?.isDraggable = true
            false
        }
        
        // Handle Back Press to close details
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (detailContainer.visibility == View.VISIBLE) {
                    handleBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressed()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userDao.getUserProfile().collectLatest { profile ->
                    profile?.themeColor?.let { color ->
                        ThemeHelper.applyThemeColor(view, color)
                        val colorStateList = android.content.res.ColorStateList.valueOf(color)
                        ivGlobalAvatar.strokeColor = colorStateList
                        btnFriends.setTextColor(color)
                        btnFriends.iconTint = colorStateList
                        btnTimelineGallery.imageTintList = colorStateList
                        adapter?.setThemeColor(color)
                    }
                    tvGlobalUsername.text = profile?.username ?: getString(R.string.default_username)
                    if (profile?.profilePicturePath != null) {
                        Glide.with(this@FeedFragment).load(File(profile.profilePicturePath)).centerCrop().into(ivGlobalAvatar)
                    } else {
                        ivGlobalAvatar.setImageResource(R.drawable.placeholder)
                    }
                    profile?.countryCode?.let { tvGlobalFlag.text = getFlagEmoji(it) }

                    btnTimelineGallery.visibility = if (profile?.isTimelineGalleryEnabled == true) View.VISIBLE else View.GONE
                }
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        recycler.layoutManager = layoutManager

        adapter = FeedAdapter(emptyList(),
            onItemClick = { item, stopId -> navigateToDetail(item, stopId) },
            onConfirmDraft = { item -> confirmDraft(item) },
            onRemoveDraft = { item -> removeDraft(item) }
        )
        recycler.adapter = adapter

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val behavior = bottomSheetBehavior ?: return
                if (detailContainer.visibility == View.VISIBLE) return

                if (!recyclerView.canScrollVertically(-1)) {
                    if (lastZoomedId != "top") {
                        lastZoomedId = "top"
                        cesiumWebView.evaluateJavascript("javascript:if(window.resetGlobeView) window.resetGlobeView();", null)
                    }
                    return
                }

                val pos = layoutManager.findFirstVisibleItemPosition()
                if (pos != RecyclerView.NO_POSITION && pos < lastItems.size) {
                    val item = lastItems[pos]
                    val loc = when(item) {
                        is FeedItem.Experience -> item.entry.location
                        is FeedItem.TripItem -> item.stops.firstOrNull()?.location
                    }
                    val id = when(item) {
                        is FeedItem.Experience -> "exp_${item.id}"
                        is FeedItem.TripItem -> "trip_${item.id}"
                    }

                    if (id != lastZoomedId) {
                        lastZoomedId = id
                        val offset = if (behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED) 0.6 else 0.0

                        if (item is FeedItem.TripItem && item.stops.isNotEmpty()) {
                            val lats = item.stops.mapNotNull { it.location?.split(",")?.getOrNull(0)?.toDoubleOrNull() }
                            val lons = item.stops.mapNotNull { it.location?.split(",")?.getOrNull(1)?.toDoubleOrNull() }

                            if (lats.isNotEmpty() && lons.isNotEmpty()) {
                                val minLat = lats.minOrNull() ?: 0.0
                                val maxLat = lats.maxOrNull() ?: 0.0
                                val minLon = lons.minOrNull() ?: 0.0
                                val maxLon = lons.maxOrNull() ?: 0.0

                                if (minLat == maxLat && minLon == maxLon) {
                                    cesiumWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($minLat, $minLon, $offset);", null)
                                } else {
                                    cesiumWebView.evaluateJavascript("javascript:if(window.zoomToBounds) window.zoomToBounds($minLat, $minLon, $maxLat, $maxLon, $offset);", null)
                                }
                            }
                        } else if (item is FeedItem.Experience && loc != null) {
                            val coords = loc.split(",")
                            if (coords.size == 2) {
                                val lat = coords[0].toDoubleOrNull() ?: 0.0
                                val lon = coords[1].toDoubleOrNull() ?: 0.0
                                cesiumWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($lat, $lon, $offset);", null)
                            }
                        }
                    }
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repository.allEntries,
                    tripRepository.allTrips,
                    tripRepository.allTripStops
                ) { entries, trips, allStops ->
                    val items = mutableListOf<FeedItem>()
                    entries.forEach { items.add(FeedItem.Experience(it)) }
                    trips.forEach { trip ->
                        val stops = allStops.filter { it.tripId == trip.id }
                        items.add(FeedItem.TripItem(trip, stops))
                    }

                    items.sortWith(compareByDescending<FeedItem> { it.isLive }.thenByDescending { it.sortDate })
                    items
                }.collect { combinedItems ->
                    lastItems = combinedItems
                    applyFilters()
                }
            }
        }

        setupExpandableFab(view)
        setupFilters(view)
    }

    private fun showAutoTripConfirmation(uris: List<Uri>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Automatischer Trip")
            .setMessage("Möchtest du aus den ${uris.size} ausgewählten Bildern automatisch einen Trip erstellen lassen? Bilder werden nach Standorten (Radius 1km) gebündelt.")
            .setPositiveButton("Ja, erstellen") { _, _ -> createAutoTrip(uris) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun createAutoTrip(uris: List<Uri>) {
        val progressDialog = AlertDialog.Builder(requireContext()).setMessage("Bilder werden verarbeitet...").setCancelable(false).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = requireActivity().application as PlacesApplication
                val tripDao = app.database.tripDao()

                val imageDataList = uris.mapNotNull { uri ->
                    val file = copyToInternalStorage(uri)
                    val exif = try { ExifInterface(file.absolutePath) } catch (e: Exception) { null }
                    val dateStr = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)
                    val date = if (dateStr != null) { try { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).parse(dateStr)?.time } catch (e: Exception) { null } } else file.lastModified()
                    val latLong = FloatArray(2)
                    val location = if (exif?.getLatLong(latLong) == true) "${latLong[0]},${latLong[1]}" else null
                    if (date != null) Triple(file.absolutePath, date, location) else null
                }.sortedBy { it.second }

                if (imageDataList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), "Keine gültigen Bilder gefunden.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val stops = mutableListOf<List<Triple<String, Long, String?>>>()
                var currentGroup = mutableListOf<Triple<String, Long, String?>>()
                var currentRefLocation: android.location.Location? = null

                for (image in imageDataList) {
                    val imgLocStr = image.third
                    if (imgLocStr != null) {
                        val coords = imgLocStr.split(",")
                        if (coords.size == 2) {
                            val imgLat = coords[0].toDoubleOrNull()
                            val imgLon = coords[1].toDoubleOrNull()

                            if (imgLat != null && imgLon != null) {
                                val imgLoc = android.location.Location("").apply {
                                    latitude = imgLat
                                    longitude = imgLon
                                }

                                if (currentRefLocation == null) {
                                    currentRefLocation = imgLoc
                                    currentGroup.add(image)
                                } else {
                                    val distanceInMeters = currentRefLocation.distanceTo(imgLoc)
                                    if (distanceInMeters > 1000f) {
                                        if (currentGroup.isNotEmpty()) stops.add(currentGroup)
                                        currentGroup = mutableListOf(image)
                                        currentRefLocation = imgLoc
                                    } else {
                                        currentGroup.add(image)
                                    }
                                }
                                continue
                            }
                        }
                    }
                    currentGroup.add(image)
                }
                if (currentGroup.isNotEmpty()) stops.add(currentGroup)

                val firstDate = imageDataList.first().second
                val tripId = tripDao.insertTrip(Trip(title = "Automatische Reise", date = firstDate, coverImage = imageDataList.first().first)).toInt()

                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val sdf = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

                stops.forEach { images ->
                    val refImageWithLocation = images.find { it.third != null }
                    val stopLocation = refImageWithLocation?.third
                    val stopDate = images.first().second
                    val stopMedia = images.map { it.first }

                    var stopName = "Stopp am ${sdf.format(Date(stopDate))}"

                    if (stopLocation != null) {
                        try {
                            val coords = stopLocation.split(",")
                            if (coords.size == 2) {
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(coords[0].toDouble(), coords[1].toDouble(), 1)
                                val address = addresses?.firstOrNull()

                                if (address != null) {
                                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                                    if (city != null) {
                                        stopName = city
                                    }
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }

                    tripDao.insertStop(TripStop(
                        tripId = tripId,
                        title = stopName,
                        date = stopDate,
                        location = stopLocation,
                        media = stopMedia,
                        coverImage = stopMedia.first()
                    ))
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    findNavController().navigate(R.id.newTripFragment, Bundle().apply {
                        putInt("tripId", tripId)
                        putString("title", "Automatische Reise bearbeiten")
                    })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun copyToInternalStorage(uri: Uri): File {
        val file = File(requireContext().filesDir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { output -> requireContext().contentResolver.openInputStream(uri)?.use { input -> input.copyTo(output) } }
        return file
    }

    private fun setupFilters(view: View) {
        val btnSearch = view.findViewById<ImageButton>(R.id.btnSearchFeed)
        val inputSearch = view.findViewById<android.widget.EditText>(R.id.inputSearchFeed)
        val searchDivider = view.findViewById<View>(R.id.searchDivider)
        val chipType = view.findViewById<Chip>(R.id.chipFilterType)
        val chipDate = view.findViewById<Chip>(R.id.chipFilterDate)
        val chipLocation = view.findViewById<Chip>(R.id.chipFilterLocation)
        val btnReset = view.findViewById<MaterialButton>(R.id.btnResetFilter)
        chipType.setOnClickListener {
            val options = arrayOf("Alle", "Erlebnisse", "Trips")
            AlertDialog.Builder(requireContext()).setTitle("Typ wählen").setItems(options) { _, which -> filterType = when(which) { 1 -> "Experience"; 2 -> "Trip"; else -> null }; chipType.text = if (filterType == null) "Typ" else options[which]; chipType.isChecked = filterType != null; updateResetButton(btnReset); applyFilters() }.show()
        }
        chipDate.setOnClickListener {
            val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Zeitraum wählen").build()
            dateRangePicker.addOnPositiveButtonClickListener { selection -> filterDateStart = selection.first; filterDateEnd = selection.second; val sdf = SimpleDateFormat("dd.MM.yy", Locale.getDefault()); chipDate.text = "${sdf.format(Date(filterDateStart!!))} - ${sdf.format(Date(filterDateEnd!!))}"; chipDate.isChecked = true; updateResetButton(btnReset); applyFilters() }
            dateRangePicker.show(parentFragmentManager, "DateRangePicker")
        }
        chipLocation.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val countries = getAvailableCountries()
                if (countries.isEmpty()) { AlertDialog.Builder(requireContext()).setMessage("Noch keine Standorte vorhanden.").setPositiveButton("OK", null).show(); return@launch }
                val countryCodes = countries.keys.toTypedArray(); val countryNames = countries.values.mapIndexed { index, name -> "${getFlagEmoji(countryCodes[index])} $name" }.toTypedArray(); val checkedItems = countryCodes.map { filterCountries.contains(it) }.toBooleanArray()
                AlertDialog.Builder(requireContext()).setTitle("Länder wählen").setMultiChoiceItems(countryNames, checkedItems) { _, which, isChecked -> if (isChecked) filterCountries.add(countryCodes[which]) else filterCountries.remove(countryCodes[which]) }.setPositiveButton("Filtern") { _, _ -> chipLocation.isChecked = filterCountries.isNotEmpty(); chipLocation.text = if (filterCountries.isEmpty()) "Standort" else "${filterCountries.size} Länder"; updateResetButton(btnReset); applyFilters() }.setNeutralButton("Alle abwählen") { _, _ -> filterCountries.clear(); chipLocation.isChecked = false; chipLocation.text = "Standort"; updateResetButton(btnReset); applyFilters() }.show()
            }
        }
        btnReset.setOnClickListener { filterType = null; filterDateStart = null; filterDateEnd = null; filterCountries.clear(); searchQuery = ""; inputSearch.setText(""); inputSearch.visibility = View.GONE; searchDivider.visibility = View.GONE; chipType.text = "Typ"; chipType.isChecked = false; chipDate.text = "Datum"; chipDate.isChecked = false; chipLocation.text = "Standort"; chipLocation.isChecked = false; btnReset.visibility = View.GONE; applyFilters() }
        btnSearch.setOnClickListener { if (inputSearch.visibility == View.GONE) { inputSearch.visibility = View.VISIBLE; searchDivider.visibility = View.VISIBLE; inputSearch.requestFocus() } else { inputSearch.visibility = View.GONE; searchDivider.visibility = View.GONE; inputSearch.setText(""); searchQuery = ""; applyFilters() } }
        inputSearch.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}; override fun afterTextChanged(s: android.text.Editable?) { searchQuery = s?.toString()?.trim() ?: ""; updateResetButton(btnReset); applyFilters() } })
    }

    private suspend fun getAvailableCountries(): Map<String, String> = withContext(Dispatchers.IO) {
        val countries = mutableMapOf<String, String>(); val geocoder = Geocoder(requireContext(), Locale.getDefault())
        lastItems.forEach { item -> val location = when (item) { is FeedItem.Experience -> item.entry.location; is FeedItem.TripItem -> item.stops.firstOrNull()?.location }; location?.split(",")?.let { coords -> if (coords.size == 2) { try { @Suppress("DEPRECATION") val addresses = geocoder.getFromLocation(coords[0].toDouble(), coords[1].toDouble(), 1) ; addresses?.firstOrNull()?.let { addr -> if (addr.countryCode != null && addr.countryName != null) countries[addr.countryCode] = addr.countryName } } catch (e: Exception) {} } } }
        countries.toList().sortedBy { it.second }.toMap()
    }

    private fun checkSearchMatch(item: FeedItem, query: String, geocoder: Geocoder): Boolean {
        val q = query.lowercase()
        when (item) {
            is FeedItem.Experience -> { if (item.entry.title.lowercase().contains(q)) return true; if (item.entry.notes?.lowercase()?.contains(q) == true) return true; item.entry.location?.let { if (getGeocodedData(it, geocoder).second?.lowercase()?.contains(q) == true) return true } }
            is FeedItem.TripItem -> { if (item.trip.title.lowercase().contains(q)) return true; for (stop in item.stops) { if (stop.title.lowercase().contains(q)) return true; stop.location?.let { if (getGeocodedData(it, geocoder).second?.lowercase()?.contains(q) == true) return true } } }
        }
        return false
    }

    private fun getGeocodedData(location: String, geocoder: Geocoder): Pair<String?, String?> {
        if (geocoderCache.containsKey(location)) return geocoderCache[location]!!
        val coords = location.split(","); var result = Pair<String?, String?>(null, null)
        if (coords.size == 2) { try { @Suppress("DEPRECATION") val addresses = geocoder.getFromLocation(coords[0].toDouble(), coords[1].toDouble(), 1); val addr = addresses?.firstOrNull(); result = Pair(addr?.countryCode, addr?.countryName) } catch (e: Exception) {} }
        geocoderCache[location] = result; return result
    }

    private fun updateResetButton(btn: MaterialButton) { btn.visibility = if (filterType != null || filterDateStart != null || filterCountries.isNotEmpty() || searchQuery.isNotEmpty()) View.VISIBLE else View.GONE }

    private fun applyFilters() {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            var filtered = lastItems; filterType?.let { type -> filtered = filtered.filter { if (type == "Experience") it is FeedItem.Experience else it is FeedItem.TripItem } }; if (filterDateStart != null && filterDateEnd != null) filtered = filtered.filter { it.date in filterDateStart!!..filterDateEnd!! }
            if (filterCountries.isNotEmpty() || searchQuery.isNotEmpty()) { val geocoder = Geocoder(requireContext(), Locale.getDefault()); filtered = withContext(Dispatchers.IO) { filtered.filter { item -> var matchesSearch = true; var matchesCountry = true; if (searchQuery.isNotEmpty()) matchesSearch = checkSearchMatch(item, searchQuery, geocoder); if (filterCountries.isNotEmpty()) { val loc = when (item) { is FeedItem.Experience -> item.entry.location; is FeedItem.TripItem -> item.stops.firstOrNull()?.location }; val code = loc?.let { getGeocodedData(it, geocoder).first }; matchesCountry = code != null && filterCountries.contains(code) }; matchesSearch && matchesCountry } } }
            adapter?.updateItems(filtered); updateGlobeData()
        }
    }

    private fun confirmDraft(item: FeedItem) { if (item is FeedItem.Experience) findNavController().navigate(R.id.newEntryFragment, Bundle().apply { putInt("entryId", item.id); putBoolean("isFromDraft", true) }) else if (item is FeedItem.TripItem) item.stops.find { it.isDraft }?.let { findNavController().navigate(R.id.tripStopDetailFragment, Bundle().apply { putInt("stopId", it.id); putBoolean("isFromDraft", true) }) } }

    private fun removeDraft(item: FeedItem) { lifecycleScope.launch { if (item is FeedItem.Experience) (requireActivity().application as PlacesApplication).repository.delete(item.entry) else if (item is FeedItem.TripItem) item.stops.filter { it.isDraft }.forEach { (requireActivity().application as PlacesApplication).database.tripDao().deleteStop(it) } } }

    private fun setupCesiumWebView() {
        cesiumWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        cesiumWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onMarkerClicked(id: String) {
                activity?.runOnUiThread {
                    if (id.startsWith("exp_")) {
                        val entryId = id.substring(4).toIntOrNull()
                        lastItems.find { it is FeedItem.Experience && it.id == entryId }?.let { navigateToDetail(it) }
                    } else if (id.startsWith("stop_")) {
                        val parts = id.split("_")
                        if (parts.size >= 3) {
                            val tripId = parts[1].toIntOrNull()
                            val stopId = parts[2].toIntOrNull()
                            lastItems.find { it is FeedItem.TripItem && it.id == tripId }?.let { navigateToDetail(it, stopId) }
                        }
                    }
                }
            }
            @JavascriptInterface
            fun checkAndMarkSpun(): Boolean = GlobeUtils.checkAndMarkSpun()
        }, "Android")
        cesiumWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                updateGlobeData()
                cesiumWebView.evaluateJavascript("javascript:if(window.startIntroSpin) window.startIntroSpin();", null)
            }
        }
        val html = try { requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
        cesiumWebView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun updateGlobeData() {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val jsonArray = withContext(Dispatchers.Default) {
                val array = JSONArray()
                lastItems.forEach { item ->
                    if (item is FeedItem.Experience && !item.entry.location.isNullOrBlank()) {
                        val obj = JSONObject()
                        if (item.entry.entryType == "Tagebuch") {
                            obj.put("type", "diary")
                        } else {
                            obj.put("type", "experience")
                        }
                        obj.put("id", item.id)
                        obj.put("title", item.entry.title)
                        val sdf = SimpleDateFormat("dd.MM.yy - HH:mm", Locale.getDefault())
                        obj.put("date", sdf.format(Date(item.entry.date)))
                        val extraImagesArray = JSONArray()
                        item.entry.media.filter { it != item.coverImage }.take(2).forEach {
                            extraImagesArray.put(GlobeUtils.getBase64Thumbnail(it))
                        }
                        if (extraImagesArray.length() > 0) obj.put("extraImages", extraImagesArray)
                        item.entry.location!!.split(",").let { coords ->
                            if (coords.size == 2) {
                                obj.put("lat", coords[0].toDouble())
                                obj.put("lon", coords[1].toDouble())
                                obj.put("image", GlobeUtils.getBase64Thumbnail(item.coverImage))
                                array.put(obj)
                            }
                        }
                    } else if (item is FeedItem.TripItem) {
                        val obj = JSONObject()
                        obj.put("type", "trip")
                        obj.put("id", item.id)
                        obj.put("title", item.trip.title)
                        val sdf = SimpleDateFormat("dd.MM.yy - HH:mm", Locale.getDefault())
                        obj.put("date", sdf.format(Date(item.date)))
                        val stopsArray = JSONArray()
                        item.stops.forEach { stop ->
                            if (!stop.location.isNullOrBlank()) {
                                val sObj = JSONObject()
                                sObj.put("id", stop.id)
                                stop.location.split(",").let { coords ->
                                    if (coords.size == 2) {
                                        sObj.put("lat", coords[0].toDouble())
                                        sObj.put("lon", coords[1].toDouble())
                                        sObj.put("image", GlobeUtils.getBase64Thumbnail(stop.coverImage ?: stop.media.firstOrNull()))
                                        stopsArray.put(sObj)
                                    }
                                }
                            }
                        }; if (stopsArray.length() > 0) { obj.put("stops", stopsArray); array.put(obj) }
                    }
                }; array
            }
            cesiumWebView.evaluateJavascript("javascript:if(window.setGlobalData) window.setGlobalData('${jsonArray}');", null)
        }
    }

    fun handleBack() {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
        } else {
            closeDetail()
        }
    }

    fun navigateToDetail(item: FeedItem, stopId: Int? = null) {
        val fragment = when {
            stopId != null -> TripStopDetailFragment().apply {
                arguments = Bundle().apply { putInt("stopId", stopId) }
            }
            item is FeedItem.Experience -> EntryDetailFragment().apply {
                arguments = Bundle().apply { putInt("entryId", item.id) }
            }
            item is FeedItem.TripItem -> TripDetailFragment().apply {
                arguments = Bundle().apply { putInt("tripId", item.id) }
            }
            else -> return
        }

        when (item) {
            is FeedItem.Experience -> {
                item.entry.location?.split(",")?.let { coords ->
                    if (coords.size == 2) {
                        val lat = coords[0].toDoubleOrNull() ?: return@let
                        val lon = coords[1].toDoubleOrNull() ?: return@let
                        cesiumWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($lat,$lon,0.0);", null)
                    }
                }
            }
            is FeedItem.TripItem -> {
                val stops = if (stopId != null) item.stops.filter { it.id == stopId } else item.stops
                val lats = stops.mapNotNull { it.location?.split(",")?.getOrNull(0)?.toDoubleOrNull() }
                val lons = stops.mapNotNull { it.location?.split(",")?.getOrNull(1)?.toDoubleOrNull() }

                if (lats.isNotEmpty() && lons.isNotEmpty()) {
                    if (lats.size == 1) {
                        cesiumWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint(${lats[0]},${lons[0]},0.0);", null)
                    } else {
                        val minLat = lats.minOrNull() ?: return
                        val maxLat = lats.maxOrNull() ?: return
                        val minLon = lons.minOrNull() ?: return
                        val maxLon = lons.maxOrNull() ?: return
                        cesiumWebView.evaluateJavascript("javascript:if(window.zoomToBounds) window.zoomToBounds($minLat,$minLon,$maxLat,$maxLon,0.0);", null)
                    }
                }
            }
        }

        feedListLayout.visibility = View.GONE
        detailContainer.visibility = View.VISIBLE
        
        val transaction = childFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
            .replace(R.id.detailFragmentContainer, fragment)
        
        if (childFragmentManager.findFragmentById(R.id.detailFragmentContainer) != null) {
            transaction.addToBackStack(null)
        }
        
        transaction.commit()

        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED
    }

    fun closeDetail() {
        // Clear backstack when closing detail view entirely
        while (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStackImmediate()
        }

        feedListLayout.visibility = View.VISIBLE
        detailContainer.visibility = View.GONE
        val fragment = childFragmentManager.findFragmentById(R.id.detailFragmentContainer)
        if (fragment != null) {
            childFragmentManager.beginTransaction().remove(fragment).commit()
        }
        lastZoomedId = null
        cesiumWebView.evaluateJavascript("javascript:if(window.resetGlobeView) window.resetGlobeView();", null)
    }

    private fun setupExpandableFab(view: View) {
        val fabAdd = view.findViewById<FloatingActionButton>(R.id.fabAdd)
        val layoutHelp = view.findViewById<View>(R.id.layoutHelp)
        val layoutExperience = view.findViewById<View>(R.id.layoutAddExperience)
        val layoutTrip = view.findViewById<View>(R.id.layoutAddTrip)
        val layoutAutoTrip = view.findViewById<View>(R.id.layoutAutoTrip)
        val layoutDiary = view.findViewById<View>(R.id.layoutAddDiary)

        val fabHelp = view.findViewById<FloatingActionButton>(R.id.fabHelp)
        val fabExperience = view.findViewById<FloatingActionButton>(R.id.fabExperience)
        val fabTrip = view.findViewById<FloatingActionButton>(R.id.fabTrip)
        val fabAutoTrip = view.findViewById<FloatingActionButton>(R.id.fabAutoTrip)
        val fabDiary = view.findViewById<FloatingActionButton>(R.id.fabDiary)


        val layouts = listOf(layoutHelp, layoutExperience, layoutTrip, layoutAutoTrip, layoutDiary)
        layouts.forEach {
            it.visibility = View.GONE
            it.alpha = 0f
            it.translationY = 50f
        }

        fabAdd.setOnClickListener {
            isFabMenuOpen = !isFabMenuOpen
            if (isFabMenuOpen) {
                fabAdd.animate().rotation(135f).setDuration(300).start()
                layouts.forEachIndexed { index, layout ->
                    layout.visibility = View.VISIBLE
                    layout.animate().alpha(1f).translationY(0f).setDuration(300).setStartDelay((index * 50).toLong()).start()
                }
            } else {
                closeFabMenu(fabAdd, layoutHelp, layoutExperience, layoutTrip, layoutAutoTrip)
            }
        }

        fabHelp.setOnClickListener {
            showHelpDialog()
            closeFabMenu(fabAdd, layoutHelp, layoutExperience, layoutTrip, layoutAutoTrip)
        }

        fabExperience.setOnClickListener {
            findNavController().navigate(R.id.newEntryFragment)
            closeFabMenu(fabAdd, layoutHelp, layoutExperience, layoutTrip, layoutAutoTrip)
        }

        fabTrip.setOnClickListener {
            findNavController().navigate(R.id.newTripFragment)
            closeFabMenu(fabAdd, layoutHelp, layoutExperience, layoutTrip, layoutAutoTrip)
        }

        fabDiary.setOnClickListener {
            findNavController().navigate(R.id.newDiaryEntryFragment)
            closeFabMenu(fabAdd, layoutHelp, layoutDiary, layoutExperience, layoutTrip, layoutAutoTrip)
        }

        fabAutoTrip.setOnClickListener {
            autoTripPicker.launch("image/*")
            closeFabMenu(fabAdd, layoutHelp, layoutExperience, layoutTrip, layoutAutoTrip)
        }
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.help_title))
            .setMessage(getString(R.string.help_content))
            .setPositiveButton("Verstanden", null)
            .show()
    }

    private fun closeFabMenu(mainFab: FloatingActionButton, vararg layouts: View) {
        isFabMenuOpen = false
        mainFab.animate().rotation(0f).setDuration(300).start()
        layouts.reversed().forEachIndexed { index, layout ->
            layout.animate()
                .alpha(0f)
                .translationY(50f)
                .setDuration(200)
                .setStartDelay((index * 50).toLong())
                .withEndAction { layout.visibility = View.GONE }
                .start()
        }
    }

    private fun getFlagEmoji(countryCode: String): String { if (countryCode.length != 2) return ""; val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6; val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6; return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}