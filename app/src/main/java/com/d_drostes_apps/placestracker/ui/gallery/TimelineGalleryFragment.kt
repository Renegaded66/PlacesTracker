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

                    if (count % 50 == 0 || count == 10) {
                        val currentList = ArrayList(photos)
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                allPhotos = currentList
                                adapter.updateItems(groupPhotosByDate(currentList))
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
            var changed = false
            
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
                                allPhotos[index] = photo.copy(latitude = latLong[0].toDouble(), longitude = latLong[1].toDouble())
                                changed = true
                            }
                        }
                    } catch (e: Exception) { /* Ignorieren */ }
                }
                
                if (index % 100 == 0 && changed && isAdded) {
                    withContext(Dispatchers.Main) {
                        if (mapWebView.visibility == View.VISIBLE) startMapUpdateStream()
                    }
                    changed = false
                }
            }
            
            if (changed && isAdded) {
                withContext(Dispatchers.Main) {
                    if (mapWebView.visibility == View.VISIBLE) startMapUpdateStream()
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

            val filtered = if (filterDateStart != null && filterDateEnd != null) {
                allPhotos.filter { it.date in filterDateStart!!..filterDateEnd!! }
            } else {
                allPhotos
            }.filter { it.latitude != null && it.longitude != null && it.latitude != 0.0 }

            // Streamweise senden: erst 50, dann immer mehr
            val chunkSizes = listOf(50, 150, 300, 500, 1000, 2000)
            for (size in chunkSizes) {
                val currentChunk = filtered.take(size)
                sendToMap(context, currentChunk)
                if (size >= filtered.size) break
                delay(300) // Kurze Pause für UI
            }
        }
    }

    private suspend fun sendToMap(context: Context, photos: List<GalleryItem.Photo>) {
        val jsonArray = JSONArray()
        photos.forEach { photo ->
            val obj = JSONObject()
            obj.put("type", "experience") 
            obj.put("id", "photo_${photo.id}")
            obj.put("lat", photo.latitude)
            obj.put("lon", photo.longitude)
            val thumb = getBase64ThumbnailForGallery(context, Uri.parse(photo.uri))
            if (thumb != null) obj.put("image", thumb)
            jsonArray.put(obj)
        }
        
        withContext(Dispatchers.Main) {
            if (isAdded) mapWebView.evaluateJavascript("javascript:if(window.setGlobalData) window.setGlobalData(${jsonArray.toString()});", null)
        }
    }

    private fun getBase64ThumbnailForGallery(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            if (bitmap == null) return null
            
            val resized = Bitmap.createScaledBitmap(bitmap, 100, 100, true)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
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
