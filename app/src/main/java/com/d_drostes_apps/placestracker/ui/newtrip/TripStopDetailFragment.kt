package com.d_drostes_apps.placestracker.ui.newtrip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.ui.feed.DetailMediaAdapter
import com.d_drostes_apps.placestracker.ui.feed.MediaDialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TripStopDetailFragment : Fragment(R.layout.fragment_entry_detail) {

    private var stop: TripStop? = null
    private lateinit var mapboxWebView: WebView
    private lateinit var llFlags: LinearLayout
    private lateinit var cvCountryName: MaterialCardView
    private lateinit var tvCountryNamePopup: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val stopId = arguments?.getInt("stopId") ?: return
        val app = (requireActivity().application as PlacesApplication)
        val tripDao = app.database.tripDao()

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val tvTitle = view.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvDetailDate)
        val tvNotes = view.findViewById<TextView>(R.id.tvDetailNotes)
        val rvMedia = view.findViewById<RecyclerView>(R.id.rvDetailMedia)
        
        llFlags = view.findViewById(R.id.llDetailFlags)
        cvCountryName = view.findViewById(R.id.cvCountryName)
        tvCountryNamePopup = view.findViewById(R.id.tvCountryNamePopup)
        
        mapboxWebView = view.findViewById(R.id.detailCesiumWebView)
        mapboxWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setupMapboxWebView()

        // Fix scrolling for WebView
        mapboxWebView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        view.findViewById<View>(R.id.detailRootLayout).setOnClickListener {
            cvCountryName.visibility = View.GONE
        }

        lifecycleScope.launch {
            val dbStop = tripDao.getStopById(stopId)
            dbStop?.let { 
                stop = it
                tvTitle.text = it.title
                tvNotes.text = it.notes ?: ""
                
                val sdf = SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault())
                tvDate.text = sdf.format(Date(it.date))

                rvMedia.layoutManager = GridLayoutManager(requireContext(), 3)
                rvMedia.adapter = DetailMediaAdapter(it.media) { path ->
                    val dialog = MediaDialogFragment().apply {
                        arguments = Bundle().apply {
                            putStringArrayList("mediaPaths", ArrayList(it.media))
                            putInt("initialPosition", it.media.indexOf(path))
                        }
                    }
                    dialog.show(parentFragmentManager, "MediaFullscreen")
                }
                updateGlobePosition()
                loadCountryFlag(it.location)
            }
        }
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

    private fun setupMapboxWebView() {
        mapboxWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        mapboxWebView.webChromeClient = WebChromeClient()
        mapboxWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                updateGlobePosition()
            }
        }
        val html = requireContext().assets
            .open("mapbox_globe.html")
            .bufferedReader()
            .use { it.readText() }

        mapboxWebView.loadDataWithBaseURL(
            "https://localhost/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun updateGlobePosition() {
        lifecycleScope.launch {
            val stopItem = stop ?: return@launch
            val coords = stopItem.location?.split(",") ?: return@launch
            if (coords.size == 2) {
                val lat = coords[0].toDouble()
                val lon = coords[1].toDouble()
                val stopImg = stopItem.coverImage ?: stopItem.media.firstOrNull()
                
                val base64 = withContext(Dispatchers.Default) {
                    stopImg?.let { getBase64Thumbnail(it) }
                }

                mapboxWebView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation(${lat}, ${lon}, '${base64 ?: ""}');", null)
            }
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
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }
}
