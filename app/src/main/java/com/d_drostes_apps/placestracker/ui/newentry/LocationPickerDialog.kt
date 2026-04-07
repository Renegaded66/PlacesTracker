package com.d_drostes_apps.placestracker.ui.newentry

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.utils.LocationUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class LocationPickerDialog(
    private val onLocationSelected: (Double, Double) -> Unit
) : DialogFragment() {

    private lateinit var webView: WebView
    private var selectedLat: Double? = null
    private var selectedLon: Double? = null
    private lateinit var coordText: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_location_picker, container, false)

        coordText = view.findViewById(R.id.tvCoordinates)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmLocation)
        val fabCurrentLocation = view.findViewById<FloatingActionButton>(R.id.fabCurrentLocation)

        webView = view.findViewById(R.id.cesiumWebView)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
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
            fun onLocationPicked(lat: Double, lon: Double) {
                activity?.runOnUiThread {
                    updatePickedLocation(lat, lon)
                }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Map ready
            }
        }

        val html = requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)

        btnClose.setOnClickListener { dismiss() }
        
        fabCurrentLocation.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val loc = LocationUtils.getLastKnownLocation(requireContext())
                loc?.let {
                    updatePickedLocation(it.latitude, it.longitude)
                    webView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint(${it.latitude}, ${it.longitude}, 0);", null)
                }
            }
        }

        btnConfirm.setOnClickListener {
            val lat = selectedLat
            val lon = selectedLon
            if (lat != null && lon != null) {
                onLocationSelected(lat, lon)
                dismiss()
            }
        }

        return view
    }

    private fun updatePickedLocation(lat: Double, lon: Double) {
        selectedLat = lat
        selectedLon = lon
        coordText.text = String.format(java.util.Locale.getDefault(), "Lat: %.4f, Lon: %.4f", lat, lon)
        webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation($lat, $lon, '');", null)
    }

    override fun getTheme(): Int = com.google.android.material.R.style.Theme_Material3_Dark_Dialog_MinWidth
}
