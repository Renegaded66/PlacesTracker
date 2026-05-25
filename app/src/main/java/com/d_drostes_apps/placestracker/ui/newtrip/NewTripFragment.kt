package com.d_drostes_apps.placestracker.ui.newtrip

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.RatingBar
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.service.TrackingService
import com.d_drostes_apps.placestracker.ui.newentry.LocationPickerDialog
import com.d_drostes_apps.placestracker.ui.newentry.MediaAdapter
import com.d_drostes_apps.placestracker.utils.GlobeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class NewTripFragment : Fragment(R.layout.fragment_new_trip) {

    private val stops = mutableListOf<TripStop>()
    private lateinit var adapter: TripStopsAdapter
    private var editingTripId: Int = -1
    private var tripCoverImagePath: String? = null
    private val selectedPeople = mutableSetOf<String>()
    private lateinit var ratingBar: RatingBar
    private lateinit var etPeople: AutoCompleteTextView
    private lateinit var chipGroupPeople: ChipGroup

    private var currentMediaAdapter: MediaAdapter? = null
    private var currentMediaList: MutableList<String>? = null
    private lateinit var mapWebView: WebView 

    private var lastZoomedStopIndex: Int = -1 
    private var stopsExpanded = false

    private val stopMediaPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        lifecycleScope.launch(Dispatchers.IO) {
            val paths = uris.map { copyToInternalStorage(it).absolutePath }
            withContext(Dispatchers.Main) {
                currentMediaList?.addAll(paths)
                currentMediaAdapter?.notifyDataSetChanged()
            }
        }
    }

    private val tripCoverPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                val file = copyToInternalStorage(it)
                withContext(Dispatchers.Main) {
                    tripCoverImagePath = file.absolutePath
                    val ivCoverPreview = view?.findViewById<ImageView>(R.id.ivTripCoverPreview)
                    ivCoverPreview?.let { iv ->
                        Glide.with(this@NewTripFragment).load(file).centerCrop().into(iv)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editingTripId = arguments?.getInt("tripId") ?: -1
        val tripDao = (requireActivity().application as PlacesApplication).database.tripDao()
        
        val inputTitle = view.findViewById<android.widget.EditText>(R.id.inputTripTitle)
        val inputNotes = view.findViewById<android.widget.EditText>(R.id.inputTripNotes)
        val switchAutoTrip = view.findViewById<SwitchMaterial>(R.id.switchAutoTrip)
        //val switchPublicTrip = view.findViewById<SwitchMaterial>(R.id.switchPublicTrip)
        val rvStops = view.findViewById<RecyclerView>(R.id.rvTripStops)
        val btnAddStop = view.findViewById<MaterialButton>(R.id.btnAddStop)
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarNewTrip)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveTrip)
        val btnAddCover = view.findViewById<MaterialButton>(R.id.btnAddTripCover)
        val ivCoverPreview = view.findViewById<ImageView>(R.id.ivTripCoverPreview)

        // rating + people (same behaviour as diary)
        ratingBar = view.findViewById(R.id.ratingTrip)
        etPeople = view.findViewById(R.id.etTripPeople)
        chipGroupPeople = view.findViewById(R.id.chipGroupTripPeople)

        setupPeopleAutocomplete()

        val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.tripScrollView)

        // keyboard‑aware auto scroll so focused field is always visible
        val rootView = view.rootView
        val rect = android.graphics.Rect()
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight > screenHeight * 0.15) { // keyboard visible
                val focused = view.findFocus()
                focused?.let {
                    scrollView.post {
                        scrollView.smoothScrollTo(0, it.bottom)
                    }
                }
            }
        }

        // ensure people input is visible above keyboard
        etPeople.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                scrollView.post {
                    scrollView.smoothScrollTo(0, v.bottom)
                }
            }
        }

        // Map preview removed from TripEdit screen; keep scrollView only for layout behavior
        // map scroll logic removed

        adapter = TripStopsAdapter(emptyList(), 
            onStopClick = { showAddStopDialog(it) },
            onMiniStopClick = { location ->
                val bundle = Bundle().apply {
                    putInt("tripId", editingTripId)
                    putFloat("preLat", location.latitude.toFloat())
                    putFloat("preLon", location.longitude.toFloat())
                    putLong("preTime", location.timestamp)
                    putLong("preLocationId", location.id)
                    putBoolean("directAddStop", true)
                }
                findNavController().navigate(R.id.newTripFragment, bundle)
            },
            onDeleteMiniStop = { location ->
                lifecycleScope.launch {
                    tripDao.deleteLocation(location)
                    //updateTripMap()
                }
            },
            onToggleExpand = { id ->
                if (id == "stops") {
                    stopsExpanded = !stopsExpanded
                    updateAdapterItems()
                }
            },
            onTransportClick = { _, _ -> },
            onConfirmDraft = { _ -> },
            onRemoveDraft = { _ -> },
            onAddStopClick = { showAddStopDialog() },
            onMediaClick = null,
            scope = lifecycleScope
        )
        rvStops.layoutManager = LinearLayoutManager(requireContext())
        rvStops.adapter = adapter

        if (editingTripId != -1) {
            lifecycleScope.launch {
                val trip = tripDao.getTripById(editingTripId)
                trip?.let {
                    inputTitle.setText(it.title)
                    inputNotes.setText(it.notes)
                    switchAutoTrip.isChecked = it.isAutoTrip

                    // load rating
                    ratingBar.rating = it.rating

                    // load people chips
                    selectedPeople.clear()
                    chipGroupPeople.removeAllViews()
                    it.people.forEach { person ->
                        if (person.isNotBlank()) {
                            selectedPeople.add(person)
                            val chip = com.google.android.material.chip.Chip(requireContext())
                            chip.text = person
                            chip.isCloseIconVisible = true
                            chip.setOnCloseIconClickListener {
                                selectedPeople.remove(person)
                                chipGroupPeople.removeView(chip)
                            }
                            chipGroupPeople.addView(chip)
                        }
                    }

                    tripCoverImagePath = it.coverImage
                    it.coverImage?.let { path ->
                        Glide.with(this@NewTripFragment).load(File(path)).centerCrop().into(ivCoverPreview)
                    }

                    val dbStops = tripDao.getStopsForTrip(editingTripId).first()
                    stops.clear()
                    stops.addAll(dbStops)
                    updateAdapterItems()
                    //updateTripMap()

                    val directAddStop = arguments?.getBoolean("directAddStop") ?: false
                    if (directAddStop && editingTripId != -1) {
                        val stopId = arguments?.getInt("stopId") ?: -1
                        if (stopId != -1) {
                            val stop = tripDao.getStopById(stopId)
                            showAddStopDialog(stop)
                        } else {
                            val preLat = arguments?.getFloat("preLat") ?: 0.0f
                            val preLon = arguments?.getFloat("preLon") ?: 0.0f
                            val preTime = arguments?.getLong("preTime") ?: System.currentTimeMillis()
                            val preLocationId = arguments?.getLong("preLocationId") ?: -1L
                            
                            showAddStopDialog(initialLat = preLat.toDouble(), initialLon = preLon.toDouble(), initialTime = preTime, miniStopIdToDelete = preLocationId)
                        }
                    }
                }
            }
        } else if (arguments?.getBoolean("directAddStop") ?: false) {
            showAddStopDialog()
        }

        btnAddCover.setOnClickListener { tripCoverPicker.launch("image/*") }
        btnAddStop.setOnClickListener { showAddStopDialog() }

        btnSave.setOnClickListener {
            val title = inputTitle.text.toString()
            val notes = inputNotes.text.toString()
            if (title.isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.title_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val isTrackingNow = switchAutoTrip.isChecked
                
                if (isTrackingNow) {
                    tripDao.deactivateAllTracking()
                }

                val trip = Trip(
                    id = if (editingTripId != -1) editingTripId else 0,
                    title = title,
                    notes = if (notes.isBlank()) null else notes,
                    date = stops.minByOrNull { it.date }?.date ?: System.currentTimeMillis(),
                    coverImage = tripCoverImagePath,
                    isTrackingActive = isTrackingNow,
                    isAutoTrip = isTrackingNow,
                    rating = ratingBar.rating,
                    people = selectedPeople.toList(),
                    isPublic = false
                )
                
                val finalTripId = if (editingTripId != -1) {
                    tripDao.updateTrip(trip)
                    editingTripId
                } else {
                    tripDao.insertTrip(trip).toInt()
                }
                
                if (isTrackingNow) {
                    val intent = Intent(requireContext(), TrackingService::class.java).apply {
                        action = TrackingService.ACTION_START_TRACKING
                        putExtra(TrackingService.EXTRA_TRIP_ID, finalTripId)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(intent)
                    } else {
                        requireContext().startService(intent)
                    }
                } else if (editingTripId != -1) {
                    // Stop service only if we were tracking this trip
                    val intent = Intent(requireContext(), TrackingService::class.java).apply {
                        action = TrackingService.ACTION_STOP_TRACKING
                    }
                    requireContext().stopService(intent)
                }
                
                if (editingTripId != -1) {
                    tripDao.deleteStopsForTrip(editingTripId)
                }
                
                stops.forEach { stop ->
                    tripDao.insertStop(stop.copy(id = 0, tripId = finalTripId))
                }

                val bundle = Bundle().apply {
                    putInt("tripId", finalTripId)
                }
                findNavController().navigate(
                    R.id.tripDetailFragment,
                    bundle
                )
            }
        }
    }

    private fun updateAdapterItems() {
        val items = mutableListOf<TripItem>()

        items.add(TripItem.MiniStopExpand(stopsExpanded, stops.size, "stops"))

        if (stopsExpanded) {
            stops.forEachIndexed { index, stop ->
                items.add(TripItem.Stop(stop))
                if (index < stops.size - 1) {
                    items.add(TripItem.Transport(stop.id, stops[index+1].id, stops[index+1].transportMode))
                }
            }
        }

        adapter.updateItems(items)
    }

    private fun zoomToStop(index: Int) {
        if (index < 0 || index >= stops.size) return
        val stop = stops[index]
        stop.location?.split(",")?.let { coords ->
            if (coords.size == 2) {
                val lat = coords[0].toDouble()
                val lon = coords[1].toDouble()
                mapWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($lat, $lon);", null)
                lastZoomedStopIndex = index
            }
        }
    }

    /*
    private fun updateTripMap() {
        lifecycleScope.launch {
            val tripDao = (requireActivity().application as PlacesApplication).database.tripDao()
            val locations = tripDao.getLocationsForTrip(editingTripId).first().filter { !it.isConvertedToStop }
            
            val points = (stops.map { true to it.location } + locations.map { false to "${it.latitude},${it.longitude}" })
                .mapNotNull { (isStop, loc) -> 
                    loc?.split(",")?.let { coords ->
                        if (coords.size == 2) {
                            val lat = coords[0].toDouble()
                            val lon = coords[1].toDouble()
                            mapOf("lat" to lat, "lon" to lon, "isMini" to !isStop)
                        } else null
                    }
                }
            
            if (points.isNotEmpty()) {
                view?.findViewById<View>(R.id.tripMapPreview)?.parent?.let { (it as View).visibility = View.VISIBLE }
                val json = com.google.gson.Gson().toJson(points)
                mapWebView.evaluateJavascript("javascript:if(window.setTripPath) window.setTripPath($json);", null)
            }
        }
    }

     */

    @SuppressLint("ClickableViewAccessibility")
    private fun showAddStopDialog(
        existingStop: TripStop? = null,
        initialLat: Double? = null,
        initialLon: Double? = null,
        initialTime: Long? = null,
        miniStopIdToDelete: Long = -1L
    ) {
        val dialog = Dialog(requireContext(), R.style.Theme_PlacesTracker)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_trip_stop)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val inputStopTitle = dialog.findViewById<TextInputEditText>(R.id.inputStopTitle)
        val inputStopNotes = dialog.findViewById<TextInputEditText>(R.id.inputStopNotes)
        val tvDate = dialog.findViewById<TextView>(R.id.tvStopDateDisplay)
        val tvTime = dialog.findViewById<TextView>(R.id.tvStopTimeDisplay)
        val tvCoords = dialog.findViewById<TextView>(R.id.tvStopCoordinates)
        val btnLocation = dialog.findViewById<MaterialButton>(R.id.btnStopLocation)
        val btnConfirm = dialog.findViewById<MaterialButton>(R.id.btnConfirmStop)
        val btnDelete = dialog.findViewById<MaterialButton>(R.id.btnDeleteStop)
        val btnBack = dialog.findViewById<View>(R.id.btnBackToTrip)
        val rvMedia = dialog.findViewById<RecyclerView>(R.id.rvStopMedia)
        val webView = dialog.findViewById<WebView>(R.id.stopCesiumWebView)
        val cardMap = dialog.findViewById<View>(R.id.cardStopPreviewMap)

        val tempMediaFiles = mutableListOf<String>()
        val selectedDate = Calendar.getInstance()
        var selectedLocation: String? = null
        var selectedCoverImage: String? = null

        if (initialLat != null && initialLon != null) {
            selectedLocation = "$initialLat,$initialLon"
            tvCoords.text = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", initialLat, initialLon)
            cardMap.visibility = View.VISIBLE
        }
        if (initialTime != null) {
            selectedDate.timeInMillis = initialTime
            tvDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(initialTime)
            tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(initialTime)
        }

        setupCesiumWebView(webView, selectedLocation ?: existingStop?.location) { lat, lon ->
            selectedLocation = "$lat,$lon"
            tvCoords.text = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", lat, lon)
            cardMap.visibility = View.VISIBLE
            val base64 = GlobeUtils.getBase64Thumbnail(selectedCoverImage)
            webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation($lat, $lon, '${base64 ?: ""}');", null)
        }

        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        existingStop?.let {
            inputStopTitle.setText(it.title)
            inputStopNotes.setText(it.notes)
            selectedDate.timeInMillis = it.date
            tvDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(it.date)
            tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.date)
            selectedLocation = it.location
            tempMediaFiles.addAll(it.media)
            selectedCoverImage = it.coverImage
            btnConfirm.text = getString(R.string.save)
            btnDelete.visibility = View.VISIBLE
            
            if (!it.location.isNullOrBlank()) {
                val coords = it.location.split(",")
                if (coords.size == 2) {
                    tvCoords.text = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", coords[0].toDouble(), coords[1].toDouble())
                    cardMap.visibility = View.VISIBLE
                }
            }
        }

        btnBack.setOnClickListener { dialog.dismiss() }

        val mediaAdapter = MediaAdapter(
            tempMediaFiles, 
            selectedCoverImage, 
            onAddClick = {
                currentMediaList = tempMediaFiles
                stopMediaPicker.launch(arrayOf("image/*", "video/*"))
                currentMediaAdapter = rvMedia.adapter as? MediaAdapter
            },
            onMediaClick = { path ->
                showMediaOptionsForStop(path, tempMediaFiles, rvMedia.adapter as MediaAdapter, { lat, lon ->
                    selectedLocation = "$lat,$lon"
                    tvCoords.text = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", lat, lon)
                    cardMap.visibility = View.VISIBLE
                    val base64 = GlobeUtils.getBase64Thumbnail(selectedCoverImage)
                    webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation($lat, $lon, '${base64 ?: ""}');", null)
                }, { date ->
                    selectedDate.time = date
                    tvDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(selectedDate.time)
                    tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedDate.time)
                }, { cover ->
                    selectedCoverImage = cover
                })
            },
            onRemove = { path ->
                tempMediaFiles.remove(path)
                if (selectedCoverImage == path) selectedCoverImage = tempMediaFiles.firstOrNull()
                rvMedia.adapter?.notifyDataSetChanged()
            },
            onSetCover = { path ->
                selectedCoverImage = path
            }
        )

        rvMedia.layoutManager = GridLayoutManager(requireContext(), 4)
        rvMedia.adapter = mediaAdapter
        currentMediaAdapter = mediaAdapter

        dialog.findViewById<View>(R.id.ivStopCalendar).setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate.set(y, m, d)
                tvDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(selectedDate.time)
            }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialog.findViewById<View>(R.id.ivStopTime).setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                selectedDate.set(Calendar.HOUR_OF_DAY, h)
                selectedDate.set(Calendar.MINUTE, m)
                tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedDate.time)
            }, 12, 0, true).show()
        }

        btnLocation.setOnClickListener {
            LocationPickerDialog { lat, lon ->
                selectedLocation = "$lat,$lon"
                tvCoords.text = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", lat, lon)
                cardMap.visibility = View.VISIBLE
                val base64 = GlobeUtils.getBase64Thumbnail(selectedCoverImage)
                webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation($lat, $lon, '${base64 ?: ""}');", null)
            }.show(parentFragmentManager, "StopLocation")
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.stop_delete_confirm_title)
                .setMessage(R.string.stop_delete_confirm_msg)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        val tripDao = (requireActivity().application as PlacesApplication).database.tripDao()
                        existingStop?.let { tripDao.deleteStop(it) }
                        
                        val dbStops = tripDao.getStopsForTrip(editingTripId).first()
                        stops.clear()
                        stops.addAll(dbStops)
                        updateAdapterItems()
                       // updateTripMap()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        btnConfirm.setOnClickListener {
            val titleStr = inputStopTitle.text.toString()
            val notesStr = inputStopNotes.text.toString()
            if (titleStr.isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.title_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            lifecycleScope.launch {
                val newStop = TripStop(
                    id = existingStop?.id ?: 0,
                    tripId = editingTripId,
                    title = titleStr,
                    notes = if (notesStr.isBlank()) null else notesStr,
                    date = selectedDate.timeInMillis,
                    location = selectedLocation,
                    media = tempMediaFiles.toList(),
                    coverImage = selectedCoverImage ?: tempMediaFiles.firstOrNull(),
                    transportMode = existingStop?.transportMode
                )
                
                val tripDao = (requireActivity().application as PlacesApplication).database.tripDao()
                tripDao.insertStop(newStop)
                
                if (miniStopIdToDelete != -1L) {
                    tripDao.deleteLocationById(miniStopIdToDelete)
                }

                val dbStops = tripDao.getStopsForTrip(editingTripId).first()
                stops.clear()
                stops.addAll(dbStops)
                updateAdapterItems()
               // updateTripMap()
                dialog.dismiss()

                Toast.makeText(requireContext(), getString(R.string.stop_saved), Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun setupCesiumWebView(webView: WebView, initialLocation: String?, onLocationPicked: ((Double, Double) -> Unit)? = null) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun checkAndMarkSpun(): Boolean = GlobeUtils.checkAndMarkSpun()

            @JavascriptInterface
            fun onLocationPicked(lat: Double, lon: Double) {
                webView.post {
                    onLocationPicked?.invoke(lat, lon)
                }
            }
        }, "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (onLocationPicked != null) {
                    webView.evaluateJavascript("javascript:if(window.setPickerMode) window.setPickerMode(true);", null)
                }
                initialLocation?.split(",")?.let { coords ->
                    if (coords.size == 2) {
                        val base64 = GlobeUtils.getBase64Thumbnail(null) // Or get from existing if editing
                        webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation(${coords[0]}, ${coords[1]}, '${base64 ?: ""}');", null)
                    }
                    //if (webView == mapWebView) {
                    //    updateTripMap()
                    //}
                }
            }
        }
        val html = requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun showMediaOptionsForStop(path: String, media: MutableList<String>, adapter: MediaAdapter, onLocation: (Double, Double) -> Unit, onDate: (Date) -> Unit, onCover: (String) -> Unit) {
        val options = arrayOf("Als Titelbild festlegen", "Datum & Standort aus EXIF laden", "Löschen")
        AlertDialog.Builder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onCover(path)
                    1 -> {
                        try {
                            val exif = ExifInterface(path)
                            val latLong = FloatArray(2)
                            if (exif.getLatLong(latLong)) {
                                onLocation(latLong[0].toDouble(), latLong[1].toDouble())
                            }
                            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME)
                            if (dateStr != null) {
                                val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                                sdf.parse(dateStr)?.let { onDate(it) }
                            }
                        } catch (e: Exception) {}
                    }
                    2 -> {
                        media.remove(path)
                        adapter.notifyDataSetChanged()
                    }
                }
            }.show()
    }

    private fun setupPeopleAutocomplete() {
        lifecycleScope.launch {
            val app = requireActivity().application as PlacesApplication
            val allPeopleRaw = app.database.entryDao().getAllPeopleRaw()
            val allPeople = allPeopleRaw.flatMap { it.split(",") }.filter { it.isNotBlank() }.map { it.trim() }.distinct()

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allPeople)
            etPeople.setAdapter(adapter)

            etPeople.setOnItemClickListener { parent, _, position, _ ->
                val person = parent.getItemAtPosition(position) as String
                addPersonChip(person)
                etPeople.setText("")
            }

            etPeople.setOnEditorActionListener { _, _, _ ->
                val person = etPeople.text.toString().trim()
                if (person.isNotEmpty()) {
                    addPersonChip(person)
                    etPeople.setText("")
                }
                true
            }
        }
    }

    private fun addPersonChip(name: String) {
        if (selectedPeople.contains(name)) return
        selectedPeople.add(name)

        val chip = Chip(requireContext()).apply {
            text = name
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                selectedPeople.remove(name)
                chipGroupPeople.removeView(this)
            }
        }

        chipGroupPeople.addView(chip)
    }

    private fun copyToInternalStorage(uri: Uri): File {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val file = File(requireContext().filesDir, "media_${System.currentTimeMillis()}.${MimeTypeMap.getSingleton().getExtensionFromMimeType(requireContext().contentResolver.getType(uri))}")
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        return file
    }
}
