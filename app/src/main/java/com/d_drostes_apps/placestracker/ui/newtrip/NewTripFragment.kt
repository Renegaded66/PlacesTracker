package com.d_drostes_apps.placestracker.ui.newtrip

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.net.Uri
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.ui.newentry.LocationPickerDialog
import com.d_drostes_apps.placestracker.ui.newentry.MediaAdapter
import com.d_drostes_apps.placestracker.utils.GlobeUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class NewTripFragment : Fragment(R.layout.fragment_new_trip) {

    private val stops = mutableListOf<TripStop>()
    private lateinit var adapter: TripStopsAdapter
    private var editingTripId: Int = -1
    private var tripCoverImagePath: String? = null

    private var currentMediaAdapter: MediaAdapter? = null
    private var currentMediaList: MutableList<String>? = null
    private lateinit var mapWebView: WebView 

    private var lastZoomedStopIndex: Int = -1 
    
    private val stopMediaPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val file = copyToInternalStorage(uri)
            currentMediaList?.add(file.absolutePath)
        }
        currentMediaAdapter?.notifyDataSetChanged()
    }

    private val tripCoverPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = copyToInternalStorage(it)
            tripCoverImagePath = file.absolutePath
            val ivPreview = view?.findViewById<ImageView>(R.id.ivTripCoverPreview)
            ivPreview?.let { Glide.with(this).load(file).override(400,400).centerCrop().into(it) }
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
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveTrip)
        val btnAddCover = view.findViewById<MaterialButton>(R.id.btnAddTripCover)
        val ivCoverPreview = view.findViewById<ImageView>(R.id.ivTripCoverPreview)

        val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.tripScrollView)

        mapWebView = view.findViewById(R.id.tripMapPreview)
        setupCesiumWebView(mapWebView, null)

        mapWebView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        scrollView.setOnScrollChangeListener(androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            if (stops.isEmpty()) return@OnScrollChangeListener

            val childCount = rvStops.childCount
            for (i in 0 until childCount) {
                val child = rvStops.getChildAt(i)
                val absoluteTop = rvStops.top + child.top
                val absoluteBottom = rvStops.top + child.bottom

                if (absoluteTop <= scrollY + 100 && absoluteBottom > scrollY) {
                    val position = rvStops.getChildAdapterPosition(child)
                    if (position != RecyclerView.NO_POSITION && position != lastZoomedStopIndex) {
                        val item = adapter.getItemAt(position)
                        if (item is TripItem.Stop) {
                            lastZoomedStopIndex = position
                            zoomToStop(position)
                        }
                    }
                    break
                }
            }

            if (scrollY <= 10 && lastZoomedStopIndex != -1) {
                lastZoomedStopIndex = -1
                mapWebView.evaluateJavascript("javascript:if(window.resetGlobeView) window.resetGlobeView();", null)
            }
        })

        adapter = TripStopsAdapter(
            items = stops.map { TripItem.Stop(it) },
            onStopClick = { stop -> showAddStopDialog(stop) },
            onMiniStopClick = { /* No mini stops in creation mode */ },
            onDeleteMiniStop = { /* No mini stops in creation mode */ },
            onToggleExpand = { /* Not needed in creation mode */ },
            onTransportClick = { _, _ -> /* Transport not editable in basic creation mode yet */ },
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
                    //switchPublicTrip.isChecked = it.isPublic
                    tripCoverImagePath = it.coverImage
                    if (it.coverImage != null) {
                        Glide.with(this@NewTripFragment).load(File(it.coverImage)).override(400,400).centerCrop().into(ivCoverPreview)
                    }
                    btnSave.text = "Änderungen speichern"
                }
                
                val dbStops = tripDao.getStopsForTrip(editingTripId).first()
                stops.clear()
                stops.addAll(dbStops)
                updateAdapterItems()
                updateTripMap()

                if (arguments?.getBoolean("directAddStop") == true) {
                    val preLat = arguments?.getFloat("preLat") ?: 0.0f
                    val preLon = arguments?.getFloat("preLon") ?: 0.0f
                    val preTime = arguments?.getLong("preTime") ?: System.currentTimeMillis()
                    val preLocationId = arguments?.getLong("preLocationId") ?: -1L
                    
                    showAddStopDialog(initialLat = preLat.toDouble(), initialLon = preLon.toDouble(), initialTime = preTime, miniStopIdToDelete = preLocationId)
                    arguments?.remove("directAddStop")
                }
            }
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
                val trip = Trip(
                    id = if (editingTripId != -1) editingTripId else 0,
                    title = title, 
                    notes = if (notes.isBlank()) null else notes,
                    date = stops.minByOrNull { it.date }?.date ?: System.currentTimeMillis(),
                    coverImage = tripCoverImagePath,
                    isTrackingActive = if (editingTripId != -1) {
                        tripDao.getTripById(editingTripId)?.isTrackingActive ?: false
                    } else false,
                    isAutoTrip = switchAutoTrip.isChecked,
                    isPublic = false //switchPublicTrip.isChecked
                )
                
                val finalTripId = if (editingTripId != -1) {
                    tripDao.updateTrip(trip)
                    editingTripId
                } else {
                    tripDao.insertTrip(trip).toInt()
                }
                
                if (editingTripId != -1) {
                    tripDao.deleteStopsForTrip(editingTripId)
                }
                
                stops.forEach { stop ->
                    tripDao.insertStop(stop.copy(id = 0, tripId = finalTripId))
                }
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun updateAdapterItems() {
        val items = mutableListOf<TripItem>()
        stops.sortedBy { it.date }.forEachIndexed { index, stop ->
            items.add(TripItem.Stop(stop))
            if (index < stops.size - 1) {
                val nextStop = stops[index + 1]
                items.add(TripItem.Transport(stop.id, nextStop.id, nextStop.transportMode))
            }
        }
        adapter.updateItems(items)
    }

    private fun zoomToStop(position: Int) {
        val item = adapter.getItemAt(position)
        if (item is TripItem.Stop) {
            item.stop.location?.split(",")?.let { coords ->
                if (coords.size == 2) {
                    val lat = coords[0].toDoubleOrNull()
                    val lon = coords[1].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        mapWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($lat, $lon);", null)
                    }
                }
            }
        }
    }

    private fun updateTripMap() {
        if (!::mapWebView.isInitialized) return

        val jsonArray = org.json.JSONArray()
        val sortedStops = stops.sortedBy { it.date }
        sortedStops.forEach { stop ->
            stop.location?.split(",")?.let { coords ->
                if (coords.size == 2) {
                    val obj = org.json.JSONObject()
                    obj.put("lat", coords[0].toDoubleOrNull() ?: 0.0)
                    obj.put("lon", coords[1].toDoubleOrNull() ?: 0.0)
                    val stopImg = stop.coverImage ?: stop.media.firstOrNull()
                    obj.put("image", GlobeUtils.getBase64Thumbnail(stopImg))
                    obj.put("isMini", false)
                    obj.put("transportMode", stop.transportMode ?: "")
                    jsonArray.put(obj)
                }
            }
        }
        val script = "javascript:if(window.setTripPath) window.setTripPath('${jsonArray.toString()}');"
        mapWebView.evaluateJavascript(script, null)
    }

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

        setupCesiumWebView(webView, selectedLocation ?: existingStop?.location)

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
                        updateTripMap()
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
                updateTripMap()
                dialog.dismiss()

                Toast.makeText(requireContext(), getString(R.string.stop_saved), Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun setupCesiumWebView(webView: WebView, initialLocation: String?) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun checkAndMarkSpun(): Boolean = GlobeUtils.checkAndMarkSpun()
        }, "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                initialLocation?.split(",")?.let { coords ->
                    if (coords.size == 2) {
                        val base64 = GlobeUtils.getBase64Thumbnail(null) // Or get from existing if editing
                        webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation(${coords[0]}, ${coords[1]}, '${base64 ?: ""}');", null)
                    }
                    if (webView == mapWebView) {
                        updateTripMap()
                    }
                }
            }
        }
        val html = requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun showMediaOptionsForStop(
        path: String, 
        mediaList: MutableList<String>, 
        adapter: MediaAdapter,
        onLocationFound: (Double, Double) -> Unit,
        onDateTimeFound: (Date) -> Unit,
        onCoverSet: (String) -> Unit
    ) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_media_options, null)
        bottomSheet.setContentView(view)

        val btnSetAsCover = view.findViewById<MaterialButton>(R.id.btnSetAsCover)
        val btnUseLocation = view.findViewById<MaterialButton>(R.id.btnUseImageLocation)
        val btnUseDateTime = view.findViewById<MaterialButton>(R.id.btnUseImageDateTime)
        val btnRemove = view.findViewById<MaterialButton>(R.id.btnRemoveMedia)

        val exif = try { ExifInterface(path) } catch (e: Exception) { null }
        val latLong = FloatArray(2)
        val hasLocation = exif?.getLatLong(latLong) ?: false
        val dateTimeString = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)

        btnUseLocation.isEnabled = hasLocation
        btnUseLocation.alpha = if (hasLocation) 1.0f else 0.5f
        btnUseDateTime.isEnabled = dateTimeString != null
        btnUseDateTime.alpha = if (dateTimeString != null) 1.0f else 0.5f

        btnSetAsCover.setOnClickListener {
            onCoverSet(path)
            adapter.updateCoverImage(path)
            bottomSheet.dismiss()
        }

        btnUseLocation.setOnClickListener {
            if (hasLocation) onLocationFound(latLong[0].toDouble(), latLong[1].toDouble())
            bottomSheet.dismiss()
        }

        btnUseDateTime.setOnClickListener {
            dateTimeString?.let { dt ->
                try {
                    val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    sdf.parse(dt)?.let { onDateTimeFound(it) }
                } catch (e: Exception) { }
            }
            bottomSheet.dismiss()
        }

        btnRemove.setOnClickListener {
            mediaList.remove(path)
            adapter.notifyDataSetChanged()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun copyToInternalStorage(uri: Uri): File {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(requireContext().contentResolver.getType(uri))
        val file = File(requireContext().filesDir, "${UUID.randomUUID()}.${extension ?: "jpg"}")
        FileOutputStream(file).use { requireContext().contentResolver.openInputStream(uri)!!.copyTo(it) }
        return file
    }
}
