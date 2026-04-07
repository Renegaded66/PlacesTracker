package com.d_drostes_apps.placestracker.ui.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.utils.GlobeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class TimelineGalleryFragment : Fragment(R.layout.fragment_timeline_gallery) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var mapWebView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: TimelineGalleryAdapter
    
    private var allPhotos = mutableListOf<GalleryItem.Photo>()
    private var filterDateStart: Long? = null
    private var filterDateEnd: Long? = null
    private var isMapLoaded = false
    private var loadingJob: Job? = null
    private var mapUpdateJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val readGranted = results[if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (readGranted) {
            loadPhotos()
        } else {
            Toast.makeText(requireContext(), "Berechtigung für Fotos wird benötigt", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.galleryRecycler)
        mapWebView = view.findViewById(R.id.mapWebView)
        progressBar = view.findViewById(R.id.progressBar)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleViewMode)
        val btnDateRange = view.findViewById<MaterialButton>(R.id.btnDateRange)
        val btnClearDate = view.findViewById<ImageButton>(R.id.btnClearDateFilter)
        val dateFilterLayout = view.findViewById<View>(R.id.dateFilterLayout)

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = TimelineGalleryAdapter(emptyList()) { photo ->
            showPhotoDetail(photo)
        }
        val layoutManager = GridLayoutManager(requireContext(), 3)
        layoutManager.spanSizeLookup = adapter.spanSizeLookup
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnViewGallery) {
                    recyclerView.visibility = View.VISIBLE
                    mapWebView.visibility = View.GONE
                    dateFilterLayout.visibility = View.GONE
                } else {
                    recyclerView.visibility = View.GONE
                    mapWebView.visibility = View.VISIBLE
                    dateFilterLayout.visibility = View.VISIBLE
                    if (isMapLoaded) startMapUpdateStream()
                }
            }
        }

        btnDateRange.setOnClickListener {
            val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Zeitraum wählen").build()
            picker.addOnPositiveButtonClickListener { selection ->
                filterDateStart = selection.first
                filterDateEnd = selection.second
                val sdf = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                btnDateRange.text = "${sdf.format(Date(filterDateStart!!))} - ${sdf.format(Date(filterDateEnd!!))}"
                startMapUpdateStream()
            }
            picker.show(parentFragmentManager, "date_picker")
        }

        btnClearDate.setOnClickListener {
            filterDateStart = null
            filterDateEnd = null
            btnDateRange.text = "Zeitraum wählen"
            startMapUpdateStream()
        }

        setupMap()
        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            loadPhotos()
        } else {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadPhotos() {
        loadingJob?.cancel()
        progressBar.visibility = View.VISIBLE
        
        loadingJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val context = context ?: return@launch
            val photos = mutableListOf<GalleryItem.Photo>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                @Suppress("DEPRECATION") MediaStore.Images.Media.LATITUDE,
                @Suppress("DEPRECATION") MediaStore.Images.Media.LONGITUDE
            )
            
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            val query = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val latColumn = cursor.getColumnIndexOrThrow(@Suppress("DEPRECATION") MediaStore.Images.Media.LATITUDE)
                val lonColumn = cursor.getColumnIndexOrThrow(@Suppress("DEPRECATION") MediaStore.Images.Media.LONGITUDE)

                var count = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val date = cursor.getLong(dateColumn)
                    
                    var lat = if (cursor.isNull(latColumn)) null else cursor.getDouble(latColumn)
                    var lon = if (cursor.isNull(lonColumn)) null else cursor.getDouble(lonColumn)
                    
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    photos.add(GalleryItem.Photo(id, contentUri.toString(), date, lat, lon))
                    count++

                    // 🌟 FIX: Jedes Bild wird jetzt SOFORT angezeigt, sobald es geladen wurde
                    // (In 10er Schritten am Anfang für Speed, dann einzeln)
                    if (count <= 10 || count % 5 == 0) {
                        val currentSnapshot = ArrayList(photos)
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                allPhotos = currentSnapshot
                                adapter.updateItems(groupPhotosByDate(currentSnapshot))
                                progressBar.visibility = View.GONE
                                if (mapWebView.visibility == View.VISIBLE) startMapUpdateStream()
                            }
                        }
                    }
                }
            }
            
            val finalPhotos = ArrayList(photos)
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    allPhotos = finalPhotos
                    adapter.updateItems(groupPhotosByDate(finalPhotos))
                    progressBar.visibility = View.GONE
                    loadMissingExifLocations()
                }
            }
        }
    }

    private fun loadMissingExifLocations() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val context = context ?: return@launch
            
            allPhotos.forEachIndexed { index, photo ->
                if (photo.latitude == null || photo.latitude == 0.0) {
                    try {
                        val photoUri = Uri.parse(photo.uri)
                        val inputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try { MediaStore.setRequireOriginal(photoUri) } catch (e: Exception) { photoUri }
                        } else photoUri

                        context.contentResolver.openInputStream(inputUri)?.use { stream ->
                            val exif = androidx.exifinterface.media.ExifInterface(stream)
                            val latLong = FloatArray(2)
                            if (exif.getLatLong(latLong)) {
                                val updatedPhoto = photo.copy(latitude = latLong[0].toDouble(), longitude = latLong[1].toDouble())
                                allPhotos[index] = updatedPhoto

                                // 🌟 FIX: Jedes gefundene EXIF-Datum wird jetzt SOFORT in die UI gepusht
                                withContext(Dispatchers.Main) {
                                    if (isAdded) {
                                        // Wir updaten den Adapter (nur das eine Item für Performance)
                                        adapter.notifyItemChanged(index) 
                                        
                                        // Wenn wir in der Kartenansicht sind, Marker direkt hinzufügen
                                        if (mapWebView.visibility == View.VISIBLE && isMapLoaded) {
                                            sendSinglePhotoToMap(context, updatedPhoto)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) { /* Ignorieren */ }
                }
            }
        }
    }

    private fun groupPhotosByDate(photos: List<GalleryItem.Photo>): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()
        val sdf = SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.getDefault())
        var lastDateString = ""

        photos.forEach { photo ->
            val dateString = sdf.format(Date(photo.date))
            if (dateString != lastDateString) {
                items.add(GalleryItem.Header(dateString))
                lastDateString = dateString
            }
            items.add(photo)
        }
        return items
    }

    private fun setupMap() {
        mapWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        mapWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                isMapLoaded = true
                if (mapWebView.visibility == View.VISIBLE) startMapUpdateStream()
            }
        }
        mapWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onMarkerClicked(id: String) {
                activity?.runOnUiThread {
                    val photoId = id.substringAfter("photo_").toLongOrNull()
                    val photo = allPhotos.find { it.id == photoId }
                    if (photo != null) {
                        view?.findViewById<MaterialButtonToggleGroup>(R.id.toggleViewMode)?.check(R.id.btnViewGallery)
                        scrollToPhoto(photo)
                    }
                }
            }
            @JavascriptInterface
            fun checkAndMarkSpun(): Boolean = GlobeUtils.checkAndMarkSpun()
        }, "Android")

        val html = try { requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
        mapWebView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun startMapUpdateStream() {
        mapUpdateJob?.cancel()
        mapUpdateJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val context = context ?: return@launch
            if (!isMapLoaded) return@launch

            withContext(Dispatchers.Main) {
                mapWebView.evaluateJavascript("javascript:if(window.clearMapMarkers) window.clearMapMarkers();", null)
            }

            val filtered = if (filterDateStart != null && filterDateEnd != null) {
                allPhotos.filter { it.date in filterDateStart!!..filterDateEnd!! }
            } else {
                allPhotos
            }.filter { it.latitude != null && it.longitude != null && (it.latitude != 0.0 || it.longitude != 0.0) }

            // In winzigen Chunks senden für flüssige UI
            filtered.chunked(10).forEach { chunk ->
                sendToMapLive(context, chunk)
                delay(20) 
            }
        }
    }

    private suspend fun sendToMapLive(context: Context, photos: List<GalleryItem.Photo>) {
        val jsonArray = JSONArray()
        photos.forEach { photo ->
            val obj = JSONObject()
            obj.put("id", photo.id)
            obj.put("lat", photo.latitude)
            obj.put("lon", photo.longitude)
            val thumb = getBase64ThumbnailFast(context, photo.id, Uri.parse(photo.uri))
            if (thumb != null) obj.put("image", thumb)
            jsonArray.put(obj)
        }

        withContext(Dispatchers.Main) {
            if (isAdded) {
                mapWebView.evaluateJavascript("javascript:if(window.appendPhotosToMap) window.appendPhotosToMap(${jsonArray.toString()});", null)
            }
        }
    }

    private suspend fun sendSinglePhotoToMap(context: Context, photo: GalleryItem.Photo) {
        val obj = JSONObject().apply {
            put("id", photo.id)
            put("lat", photo.latitude)
            put("lon", photo.longitude)
            val thumb = withContext(Dispatchers.IO) { getBase64ThumbnailFast(context, photo.id, Uri.parse(photo.uri)) }
            if (thumb != null) put("image", thumb)
        }
        val array = JSONArray().put(obj)
        withContext(Dispatchers.Main) {
            mapWebView.evaluateJavascript("javascript:if(window.appendPhotosToMap) window.appendPhotosToMap(${array.toString()});", null)
        }
    }

    private fun getBase64ThumbnailFast(context: Context, id: Long, uri: Uri): String? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, android.util.Size(120, 120), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Images.Thumbnails.MICRO_KIND, null)
            }

            if (bitmap == null) return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            "data:image/jpeg;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun scrollToPhoto(photo: GalleryItem.Photo) {
        val index = adapter.items.indexOf(photo)
        if (index != -1) {
            recyclerView.scrollToPosition(index)
        }
    }

    private fun showPhotoDetail(photo: GalleryItem.Photo) {
        val action = TimelineGalleryFragmentDirections.actionTimelineGalleryFragmentToPhotoDetailFragment(
            photoUri = photo.uri,
            photoDate = photo.date,
            latitude = photo.latitude?.toFloat() ?: 0f,
            longitude = photo.longitude?.toFloat() ?: 0f
        )
        findNavController().navigate(action)
    }
}
