package com.d_drostes_apps.placestracker.ui.feed

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class FeedFragment : Fragment(R.layout.fragment_feed) {

    private lateinit var recycler: RecyclerView
    private lateinit var mapboxWebView: WebView
    private var adapter: FeedAdapter? = null
    private var isFabMenuOpen = false
    private var lastItems: List<FeedItem> = emptyList()

    private var filterType: String? = null // "Experience", "Trip"
    private var filterDateStart: Long? = null
    private var filterDateEnd: Long? = null
    private var filterCountries: MutableSet<String> = mutableSetOf()

    private val autoTripPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            showAutoTripConfirmation(uris)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = (requireActivity().application as PlacesApplication)
        val repository = app.repository
        val tripRepository = app.tripRepository
        val userDao = app.userDao

        val ivGlobalAvatar = view.findViewById<ImageView>(R.id.ivGlobalUserAvatar)
        val tvGlobalUsername = view.findViewById<TextView>(R.id.tvGlobalUsername)
        val tvGlobalFlag = view.findViewById<TextView>(R.id.tvGlobalUserFlag)
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupView)
        val btnFriends = view.findViewById<MaterialButton>(R.id.btnFriends)

        btnFriends.setOnClickListener {
            findNavController().navigate(R.id.action_feedFragment_to_friendsFragment)
        }

        mapboxWebView = view.findViewById(R.id.feedCesiumWebView)
        mapboxWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setupMapboxWebView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userDao.getUserProfile().collectLatest { profile ->
                    profile?.themeColor?.let { color ->
                        ThemeHelper.applyThemeColor(view, color)
                    }
                    tvGlobalUsername.text = profile?.username ?: getString(R.string.default_username)
                    if (profile?.profilePicturePath != null) {
                        Glide.with(this@FeedFragment).load(File(profile.profilePicturePath)).centerCrop().into(ivGlobalAvatar)
                    } else {
                        ivGlobalAvatar.setImageResource(R.drawable.placeholder)
                    }
                    profile?.countryCode?.let { tvGlobalFlag.text = getFlagEmoji(it) }
                }
            }
        }

        recycler = view.findViewById(R.id.feedRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        
        val feedAdapter = FeedAdapter(emptyList(), 
            onItemClick = { item, stopId -> navigateToDetail(item, stopId) },
            onConfirmDraft = { item -> confirmDraft(item) },
            onRemoveDraft = { item -> removeDraft(item) }
        )
        adapter = feedAdapter
        recycler.adapter = feedAdapter
        
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
                    items.sortByDescending { it.date }
                    items
                }.collect { combinedItems ->
                    lastItems = combinedItems
                    applyFilters()
                }
            }
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnGlobeView) {
                    recycler.visibility = View.GONE
                    mapboxWebView.visibility = View.VISIBLE
                    updateGlobeData()
                } else if (checkedId == R.id.btnListView) {
                    mapboxWebView.visibility = View.GONE
                    recycler.visibility = View.VISIBLE
                }
            }
        }

        setupExpandableFab(view)
        setupFilters(view)
    }

    private fun showAutoTripConfirmation(uris: List<Uri>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Automatischer Trip")
            .setMessage("Möchtest du aus den ${uris.size} ausgewählten Bildern automatisch einen Trip erstellen lassen? Bilder werden nach Tagen gruppiert.")
            .setPositiveButton("Ja, erstellen") { _, _ ->
                createAutoTrip(uris)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun createAutoTrip(uris: List<Uri>) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setMessage("Bilder werden verarbeitet...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = requireActivity().application as PlacesApplication
                val tripDao = app.database.tripDao()
                
                val imageDataList = uris.mapNotNull { uri ->
                    val file = copyToInternalStorage(uri)
                    val exif = try { ExifInterface(file.absolutePath) } catch (e: Exception) { null }
                    
                    val dateStr = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)
                    val date = if (dateStr != null) {
                        try {
                            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).parse(dateStr)?.time
                        } catch (e: Exception) { null }
                    } else file.lastModified()

                    val latLong = FloatArray(2)
                    val location = if (exif?.getLatLong(latLong) == true) {
                        "${latLong[0]},${latLong[1]}"
                    } else null

                    if (date != null) {
                        Triple(file.absolutePath, date, location)
                    } else null
                }.sortedBy { it.second }

                if (imageDataList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), "Keine gültigen Bilder gefunden.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Group by day
                val groupedByDay = imageDataList.groupBy { 
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.second))
                }

                val firstDate = imageDataList.first().second
                val tripId = tripDao.insertTrip(Trip(
                    title = "Automatische Reise",
                    date = firstDate,
                    coverImage = imageDataList.first().first
                )).toInt()

                groupedByDay.forEach { (day, images) ->
                    val stopLocation = images.find { it.third != null }?.third
                    val stopDate = images.first().second
                    val stopMedia = images.map { it.first }
                    
                    tripDao.insertStop(TripStop(
                        tripId = tripId,
                        title = "Stopp am $day",
                        date = stopDate,
                        location = stopLocation,
                        media = stopMedia,
                        coverImage = stopMedia.first()
                    ))
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    val bundle = Bundle().apply {
                        putInt("tripId", tripId)
                        putString("title", "Automatische Reise bearbeiten")
                    }
                    findNavController().navigate(R.id.newTripFragment, bundle)
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
        val extension = "jpg" // Simplified
        val file = File(requireContext().filesDir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { output ->
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(output)
            }
        }
        return file
    }

    private fun setupFilters(view: View) {
        val chipType = view.findViewById<Chip>(R.id.chipFilterType)
        val chipDate = view.findViewById<Chip>(R.id.chipFilterDate)
        val chipLocation = view.findViewById<Chip>(R.id.chipFilterLocation)
        val btnReset = view.findViewById<MaterialButton>(R.id.btnResetFilter)

        chipType.setOnClickListener {
            val options = arrayOf("Alle", "Erlebnisse", "Trips")
            AlertDialog.Builder(requireContext())
                .setTitle("Typ wählen")
                .setItems(options) { _, which ->
                    filterType = when(which) {
                        1 -> "Experience"
                        2 -> "Trip"
                        else -> null
                    }
                    chipType.text = if (filterType == null) "Typ" else options[which]
                    chipType.isChecked = filterType != null
                    updateResetButton(btnReset)
                    applyFilters()
                }.show()
        }

        chipDate.setOnClickListener {
            val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Zeitraum wählen")
                .build()

            dateRangePicker.addOnPositiveButtonClickListener { selection ->
                filterDateStart = selection.first
                filterDateEnd = selection.second
                val sdf = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                chipDate.text = "${sdf.format(Date(filterDateStart!!))} - ${sdf.format(Date(filterDateEnd!!))}"
                chipDate.isChecked = true
                updateResetButton(btnReset)
                applyFilters()
            }
            dateRangePicker.show(parentFragmentManager, "DateRangePicker")
        }

        chipLocation.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val countries = getAvailableCountries()
                if (countries.isEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setMessage("Noch keine Standorte vorhanden.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                val countryCodes = countries.keys.toTypedArray()
                val countryNames = countries.values.mapIndexed { index, name -> 
                    "${getFlagEmoji(countryCodes[index])} $name"
                }.toTypedArray()
                
                val checkedItems = countryCodes.map { filterCountries.contains(it) }.toBooleanArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("Länder wählen")
                    .setMultiChoiceItems(countryNames, checkedItems) { _, which, isChecked ->
                        if (isChecked) filterCountries.add(countryCodes[which])
                        else filterCountries.remove(countryCodes[which])
                    }
                    .setPositiveButton("Filtern") { _, _ ->
                        chipLocation.isChecked = filterCountries.isNotEmpty()
                        chipLocation.text = if (filterCountries.isEmpty()) "Standort" else "${filterCountries.size} Länder"
                        updateResetButton(btnReset)
                        applyFilters()
                    }
                    .setNeutralButton("Alle abwählen") { _, _ ->
                        filterCountries.clear()
                        chipLocation.isChecked = false
                        chipLocation.text = "Standort"
                        updateResetButton(btnReset)
                        applyFilters()
                    }
                    .show()
            }
        }

        btnReset.setOnClickListener {
            filterType = null
            filterDateStart = null
            filterDateEnd = null
            filterCountries.clear()
            chipType.text = "Typ"
            chipType.isChecked = false
            chipDate.text = "Datum"
            chipDate.isChecked = false
            chipLocation.text = "Standort"
            chipLocation.isChecked = false
            btnReset.visibility = View.GONE
            applyFilters()
        }
    }

    private suspend fun getAvailableCountries(): Map<String, String> = withContext(Dispatchers.IO) {
        val countries = mutableMapOf<String, String>()
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        
        lastItems.forEach { item ->
            val location = when (item) {
                is FeedItem.Experience -> item.entry.location
                is FeedItem.TripItem -> item.stops.firstOrNull()?.location
            }
            
            location?.split(",")?.let { coords ->
                if (coords.size == 2) {
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(coords[0].toDouble(), coords[1].toDouble(), 1)
                        addresses?.firstOrNull()?.let { addr ->
                            if (addr.countryCode != null && addr.countryName != null) {
                                countries[addr.countryCode] = addr.countryName
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        countries.toList().sortedBy { it.second }.toMap()
    }

    private fun updateResetButton(btn: MaterialButton) {
        btn.visibility = if (filterType != null || filterDateStart != null || filterCountries.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyFilters() {
        var filtered = lastItems
        
        filterType?.let { type ->
            filtered = filtered.filter { 
                if (type == "Experience") it is FeedItem.Experience else it is FeedItem.TripItem 
            }
        }
        
        if (filterDateStart != null && filterDateEnd != null) {
            filtered = filtered.filter { it.date in filterDateStart!!..filterDateEnd!! }
        }
        
        if (filterCountries.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val geocoder = Geocoder(requireContext(), Locale.ENGLISH)
                val filteredByCountry = withContext(Dispatchers.IO) {
                    filtered.filter { item ->
                        val location = when (item) {
                            is FeedItem.Experience -> item.entry.location
                            is FeedItem.TripItem -> item.stops.firstOrNull()?.location
                        }
                        val code = location?.split(",")?.let { coords ->
                            if (coords.size == 2) {
                                try {
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocation(coords[0].toDouble(), coords[1].toDouble(), 1)
                                    addresses?.firstOrNull()?.countryCode
                                } catch (e: Exception) { null }
                            } else null
                        }
                        code != null && filterCountries.contains(code)
                    }
                }
                adapter?.updateItems(filteredByCountry)
            }
            return
        }

        adapter?.updateItems(filtered)
        if (mapboxWebView.visibility == View.VISIBLE) {
            updateGlobeData()
        }
    }

    private fun confirmDraft(item: FeedItem) {
        if (item is FeedItem.Experience) {
            val bundle = Bundle().apply {
                putInt("entryId", item.id)
                putBoolean("isFromDraft", true)
            }
            findNavController().navigate(R.id.newEntryFragment, bundle)
        } else if (item is FeedItem.TripItem) {
            val draftStop = item.stops.find { it.isDraft }
            draftStop?.let {
                val bundle = Bundle().apply {
                    putInt("stopId", it.id)
                    putBoolean("isFromDraft", true)
                }
                findNavController().navigate(R.id.tripStopDetailFragment, bundle)
            }
        }
    }

    private fun removeDraft(item: FeedItem) {
        val app = (requireActivity().application as PlacesApplication)
        lifecycleScope.launch {
            if (item is FeedItem.Experience) {
                app.repository.delete(item.entry)
            } else if (item is FeedItem.TripItem) {
                val draftStops = item.stops.filter { it.isDraft }
                draftStops.forEach { app.database.tripDao().deleteStop(it) }
            }
        }
    }

    private fun setupMapboxWebView() {
        mapboxWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        mapboxWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onMarkerClicked(id: String) {
                activity?.runOnUiThread {
                    if (id.startsWith("exp_")) {
                        val entryId = id.substring(4).toIntOrNull()
                        val item = lastItems.find { it is FeedItem.Experience && it.id == entryId }
                        item?.let { navigateToDetail(item) }
                    } else if (id.startsWith("stop_")) {
                        val parts = id.split("_")
                        if (parts.size >= 3) {
                            val tripId = parts[1].toIntOrNull()
                            val stopId = parts[2].toIntOrNull()
                            val item = lastItems.find { it is FeedItem.TripItem && it.id == tripId }
                            item?.let { navigateToDetail(it, stopId) }
                        }
                    }
                }
            }
        }, "Android")
        
        mapboxWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (mapboxWebView.visibility == View.VISIBLE) {
                    updateGlobeData()
                }
            }
        }
        
        val html = try {
            requireContext().assets.open("mapbox_globe.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }
        mapboxWebView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun updateGlobeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val jsonArray = withContext(Dispatchers.Default) {
                val array = JSONArray()
                lastItems.forEach { item ->
                    if (item is FeedItem.Experience && !item.entry.location.isNullOrBlank()) {
                        val obj = JSONObject()
                        obj.put("type", "experience")
                        obj.put("id", item.id)
                        val coords = item.entry.location!!.split(",")
                        if (coords.size == 2) {
                            obj.put("lat", coords[0].toDouble())
                            obj.put("lon", coords[1].toDouble())
                            obj.put("image", item.coverImage?.let { getBase64Thumbnail(it) })
                            array.put(obj)
                        }
                    } else if (item is FeedItem.TripItem) {
                        val obj = JSONObject()
                        obj.put("type", "trip")
                        obj.put("id", item.id)
                        val stopsArray = JSONArray()
                        item.stops.forEach { stop ->
                            if (!stop.location.isNullOrBlank()) {
                                val sObj = JSONObject()
                                sObj.put("id", stop.id)
                                val coords = stop.location!!.split(",")
                                if (coords.size == 2) {
                                    sObj.put("lat", coords[0].toDouble())
                                    sObj.put("lon", coords[1].toDouble())
                                    val stopImg = stop.coverImage ?: stop.media.firstOrNull()
                                    sObj.put("image", stopImg?.let { getBase64Thumbnail(it) })
                                    stopsArray.put(sObj)
                                }
                            }
                        }
                        if (stopsArray.length() > 0) {
                            obj.put("stops", stopsArray)
                            array.put(obj)
                        }
                    }
                }
                array
            }
            mapboxWebView.evaluateJavascript("javascript:if(window.setGlobalData) window.setGlobalData('${jsonArray}');", null)
        }
    }

    private fun getBase64Thumbnail(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
            val resized = Bitmap.createScaledBitmap(bitmap, 120, 120, true)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun navigateToDetail(item: FeedItem, stopId: Int? = null) {
        val bundle = Bundle().apply {
            if (item is FeedItem.Experience) {
                putInt("entryId", item.id)
            } else {
                putInt("tripId", item.id)
                stopId?.let { putInt("stopId", it) }
            }
        }
        val destination = if (item is FeedItem.Experience) R.id.action_feedFragment_to_entryDetailFragment else R.id.action_feedFragment_to_tripDetailFragment
        findNavController().navigate(destination, bundle)
    }

    private fun setupExpandableFab(view: View) {
        val fabAdd = view.findViewById<FloatingActionButton>(R.id.fabAdd)
        val layoutExperience = view.findViewById<View>(R.id.layoutAddExperience)
        val layoutTrip = view.findViewById<View>(R.id.layoutAddTrip)
        val layoutAutoTrip = view.findViewById<View>(R.id.layoutAutoTrip)
        val fabExperience = view.findViewById<FloatingActionButton>(R.id.fabExperience)
        val fabTrip = view.findViewById<FloatingActionButton>(R.id.fabTrip)
        val fabAutoTrip = view.findViewById<FloatingActionButton>(R.id.fabAutoTrip)

        fabAdd.setOnClickListener {
            if (isFabMenuOpen) {
                layoutExperience.visibility = View.GONE
                layoutTrip.visibility = View.GONE
                layoutAutoTrip.visibility = View.GONE
                fabAdd.setImageResource(R.drawable.ic_add)
            } else {
                layoutExperience.visibility = View.VISIBLE
                layoutTrip.visibility = View.VISIBLE
                layoutAutoTrip.visibility = View.VISIBLE
                fabAdd.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            }
            isFabMenuOpen = !isFabMenuOpen
        }

        fabExperience.setOnClickListener {
            findNavController().navigate(R.id.newEntryFragment)
            closeFabMenu(fabAdd, layoutExperience, layoutTrip, layoutAutoTrip)
        }

        fabTrip.setOnClickListener {
            findNavController().navigate(R.id.newTripFragment)
            closeFabMenu(fabAdd, layoutExperience, layoutTrip, layoutAutoTrip)
        }

        fabAutoTrip.setOnClickListener {
            autoTripPicker.launch("image/*")
            closeFabMenu(fabAdd, layoutExperience, layoutTrip, layoutAutoTrip)
        }
    }

    private fun closeFabMenu(mainFab: FloatingActionButton, vararg layouts: View) {
        layouts.forEach { it.visibility = View.GONE }
        mainFab.setImageResource(R.drawable.ic_add)
        isFabMenuOpen = false
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}
