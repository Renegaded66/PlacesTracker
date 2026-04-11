package com.d_drostes_apps.placestracker.ui.newentry

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.NestedScrollView
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.ui.themes.newentry.MediaAdapter
import com.d_drostes_apps.placestracker.utils.GlobeUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class NewEntryFragment : Fragment(R.layout.fragment_new_entry) {

    private lateinit var recycler: RecyclerView
    private lateinit var mediaAdapter: MediaAdapter
    private val mediaFiles = mutableListOf<String>()

    private var selectedDate: Calendar = Calendar.getInstance()
    private var isTimeSet: Boolean = false
    private var selectedLocation: Pair<Double, Double>? = null
    private var selectedCoverImage: String? = null
    private var editingEntryId: Int = -1
    private var isMapFullscreen = false

    private lateinit var tvDateDisplay: TextView
    private lateinit var tvTimeDisplay: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var mapboxWebView: WebView
    private lateinit var cardMap: MaterialCardView
    //private lateinit var switchPublic: SwitchMaterial
    private lateinit var inputTitle: android.widget.EditText
    private lateinit var inputNotes: android.widget.EditText

    val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val file = copyToInternalStorage(uri)
            mediaFiles.add(file.absolutePath)
            if (selectedCoverImage == null) {
                selectedCoverImage = file.absolutePath
            }
        }
        mediaAdapter.updateCoverImage(selectedCoverImage)
        mediaAdapter.notifyDataSetChanged()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editingEntryId = arguments?.getInt("entryId") ?: -1
        val repository = (requireActivity().application as PlacesApplication).repository

        inputTitle = view.findViewById(R.id.inputTitle)
        inputNotes = view.findViewById(R.id.inputNotes)
        //switchPublic = view.findViewById(R.id.switchPublic)
        val btnLocation = view.findViewById<MaterialButton>(R.id.btnLocation)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        val btnFullscreen = view.findViewById<ImageButton>(R.id.btnFullscreenMap)
        tvCoordinates = view.findViewById(R.id.tvCoordinates)
        
        tvDateDisplay = view.findViewById(R.id.tvDateDisplay)
        tvTimeDisplay = view.findViewById(R.id.tvTimeDisplay)

        cardMap = view.findViewById(R.id.cardNewEntryMap)
        mapboxWebView = view.findViewById(R.id.newEntryCesiumWebView)
        setupMapboxWebView()

        updateDateDisplay()
        updateTimeDisplay()

        btnFullscreen.setOnClickListener { toggleFullscreen(btnFullscreen) }

        recycler = view.findViewById(R.id.mediaRecycler)
        recycler.layoutManager = GridLayoutManager(requireContext(), 4)
        
        mediaAdapter = MediaAdapter(
            mediaPaths = mediaFiles,
            coverImagePath = selectedCoverImage,
            onAddClick = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
            onMediaClick = { path -> showMediaOptions(path) },
            onRemove = { removed -> removeMedia(removed) },
            onSetCover = { path -> setAsCover(path) }
        )
        recycler.adapter = mediaAdapter

        if (editingEntryId != -1) {
            lifecycleScope.launch {
                val entry = (requireActivity().application as PlacesApplication).database.entryDao().getEntryById(editingEntryId)
                entry?.let {
                    inputTitle.setText(it.title)
                    inputNotes.setText(it.notes)
                    //switchPublic.isChecked = it.isPublic
                    selectedDate.timeInMillis = it.date
                    isTimeSet = true
                    
                    selectedCoverImage = it.coverImage
                    updateDateDisplay()
                    updateTimeDisplay()
                    
                    it.location?.split(",")?.let { coords ->
                        if (coords.size == 2) {
                            val lat = coords[0].trim().toDoubleOrNull()
                            val lon = coords[1].trim().toDoubleOrNull()
                            if (lat != null && lon != null) {
                                selectedLocation = lat to lon
                                tvCoordinates.text = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", lat, lon)
                                cardMap.visibility = View.VISIBLE
                                updateMapPreview()
                            }
                        }
                    }
                    
                    mediaFiles.clear()
                    mediaFiles.addAll(it.media)
                    mediaAdapter.updateCoverImage(selectedCoverImage)
                    mediaAdapter.notifyDataSetChanged()
                    
                    btnSave.text = "Änderungen speichern"
                }
            }
        }

        view.findViewById<View>(R.id.cardDate).setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate.set(Calendar.YEAR, y)
                selectedDate.set(Calendar.MONTH, m)
                selectedDate.set(Calendar.DAY_OF_MONTH, d)
                updateDateDisplay()
            }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
        }

        view.findViewById<View>(R.id.cardTime).setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, min ->
                selectedDate.set(Calendar.HOUR_OF_DAY, h)
                selectedDate.set(Calendar.MINUTE, min)
                isTimeSet = true
                updateTimeDisplay()
            }, selectedDate.get(Calendar.HOUR_OF_DAY), selectedDate.get(Calendar.MINUTE), true).show()
        }

        btnLocation.setOnClickListener {
            val dialog = LocationPickerDialog { lat, lon ->
                updateLocation(lat, lon)
            }
            dialog.show(parentFragmentManager, "LocationPicker")
        }

        btnSave.setOnClickListener {
            val title = inputTitle.text.toString()
            if (title.isBlank()) {
                Toast.makeText(requireContext(), "Bitte Titel eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isTimeSet) {
                selectedDate.set(Calendar.HOUR_OF_DAY, 0)
                selectedDate.set(Calendar.MINUTE, 0)
            }

            val entry = Entry(
                id = if (editingEntryId != -1) editingEntryId else 0,
                title = title,
                date = selectedDate.timeInMillis,
                notes = inputNotes.text?.toString(),
                location = selectedLocation?.let { "${it.first},${it.second}" },
                media = mediaFiles.toList(),
                coverImage = selectedCoverImage ?: mediaFiles.firstOrNull(),
                isDraft = false,
                isPublic = false, //switchPublic.isChecked,
                entryType = "experience"
            )

            lifecycleScope.launch {
                repository.insert(entry)
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun toggleFullscreen(btn: ImageButton) {
        isMapFullscreen = !isMapFullscreen
        val nestedScrollView = view?.findViewById<NestedScrollView>(R.id.newEntryScrollView)
        val linearLayout = cardMap.parent as LinearLayout
        val btnSave = view?.findViewById<View>(R.id.btnSave)

        if (isMapFullscreen) {
            for (i in 0 until linearLayout.childCount) {
                val child = linearLayout.getChildAt(i)
                if (child != cardMap) child.visibility = View.GONE
            }
            btnSave?.visibility = View.GONE
            linearLayout.setPadding(0, 0, 0, 0)
            nestedScrollView?.isFillViewport = true
            
            val params = cardMap.layoutParams as LinearLayout.LayoutParams
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.setMargins(0, 0, 0, 0)
            cardMap.layoutParams = params
            btn.setImageResource(R.drawable.ic_fullscreen_exit)
        } else {
            for (i in 0 until linearLayout.childCount) {
                linearLayout.getChildAt(i).visibility = View.VISIBLE
            }
            btnSave?.visibility = View.VISIBLE
            val padding = (24 * resources.displayMetrics.density).toInt()
            linearLayout.setPadding(padding, padding, padding, padding)
            nestedScrollView?.isFillViewport = false

            val params = cardMap.layoutParams as LinearLayout.LayoutParams
            params.height = (200 * resources.displayMetrics.density).toInt()
            params.setMargins(0, 0, 0, (32 * resources.displayMetrics.density).toInt())
            cardMap.layoutParams = params
            btn.setImageResource(R.drawable.ic_fullscreen)
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
            fun checkAndMarkSpun(): Boolean = GlobeUtils.checkAndMarkSpun()
        }, "Android")
        mapboxWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        mapboxWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                selectedLocation?.let { updateMapPreview() }
            }
        }
        val html = try { requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
        mapboxWebView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun updateLocation(lat: Double, lon: Double) {
        selectedLocation = lat to lon
        tvCoordinates.text = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", lat, lon)
        cardMap.visibility = View.VISIBLE
        updateMapPreview()
    }

    private fun updateMapPreview() {
        lifecycleScope.launch {
            val base64 = withContext(Dispatchers.Default) {
                GlobeUtils.getBase64Thumbnail(selectedCoverImage)
            }
            selectedLocation?.let {
                mapboxWebView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation(${it.first}, ${it.second}, '${base64 ?: ""}');", null)
            }
        }
    }

    private fun showMediaOptions(path: String) {
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
            setAsCover(path)
            bottomSheet.dismiss()
        }

        btnUseLocation.setOnClickListener {
            if (hasLocation) {
                updateLocation(latLong[0].toDouble(), latLong[1].toDouble())
            }
            bottomSheet.dismiss()
        }

        btnUseDateTime.setOnClickListener {
            dateTimeString?.let { dt ->
                try {
                    val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    sdf.parse(dt)?.let {
                        selectedDate.time = it
                        isTimeSet = true
                        updateDateDisplay()
                        updateTimeDisplay()
                    }
                } catch (e: Exception) { }
            }
            bottomSheet.dismiss()
        }

        btnRemove.setOnClickListener {
            removeMedia(path)
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun setAsCover(path: String) {
        selectedCoverImage = path
        mediaAdapter.updateCoverImage(path)
        updateMapPreview()
    }

    private fun removeMedia(path: String) {
        mediaFiles.remove(path)
        if (selectedCoverImage == path) {
            selectedCoverImage = mediaFiles.firstOrNull()
            mediaAdapter.updateCoverImage(selectedCoverImage)
            updateMapPreview()
        }
        mediaAdapter.notifyDataSetChanged()
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault())
        tvDateDisplay.text = sdf.format(selectedDate.time)
    }

    private fun updateTimeDisplay() {
        if (isTimeSet) {
            val sdf = SimpleDateFormat("HH:mm 'Uhr'", Locale.getDefault())
            tvTimeDisplay.text = sdf.format(selectedDate.time)
        } else {
            tvTimeDisplay.text = "Uhrzeit hinzufügen"
        }
    }

    private fun copyToInternalStorage(uri: Uri): File {
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(requireContext().contentResolver.getType(uri))
        val fileName = "${UUID.randomUUID()}.${extension ?: "jpg"}"
        val file = File(requireContext().filesDir, fileName)
        requireContext().contentResolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file
    }
}
