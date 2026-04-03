package com.d_drostes_apps.placestracker.ui.newtrip

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.ui.feed.DetailMediaAdapter
import com.d_drostes_apps.placestracker.ui.feed.MediaDialogFragment
import com.d_drostes_apps.placestracker.utils.GlobeUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class TripStopDetailFragment : BottomSheetDialogFragment() {

    private var stop: TripStop? = null
    private lateinit var mapboxWebView: WebView
    private lateinit var llFlags: LinearLayout
    private lateinit var cvCountryName: View
    private lateinit var tvCountryNamePopup: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_entry_detail, container, false)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            behavior.halfExpandedRatio = 0.65f
            behavior.skipCollapsed = true
            
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

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
        setupCesiumWebView()

        mapboxWebView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        toolbar.setNavigationOnClickListener { dismiss() }

        view.findViewById<View>(R.id.detailRootLayout).setOnClickListener {
            cvCountryName.visibility = View.GONE
        }

        lifecycleScope.launch {
            val dbStop = tripDao.getStopById(stopId)
            dbStop?.let { 
                stop = it
                tvTitle.text = it.title
                
                if (!it.notes.isNullOrBlank()) {
                    tvNotes.text = it.notes
                    tvNotes.visibility = View.VISIBLE
                } else {
                    tvNotes.visibility = View.GONE
                }
                
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault())
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
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(coords[0].toDouble(), coords[1].toDouble(), 1)
            val addr = addresses?.firstOrNull()
            if (addr?.countryCode != null && addr.countryName != null) {
                addr.countryCode!! to addr.countryName!!
            } else null
        } catch (e: Exception) { null }
    }

    private fun getFlagEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    private fun setupCesiumWebView() {
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
        mapboxWebView.webChromeClient = WebChromeClient()
        mapboxWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                updateGlobePosition()
            }
        }
        val html = try {
            requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }
        mapboxWebView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
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
                    GlobeUtils.getBase64Thumbnail(stopImg)
                }
                mapboxWebView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation($lat, $lon, '${base64 ?: ""}');", null)
            }
        }
    }
}
