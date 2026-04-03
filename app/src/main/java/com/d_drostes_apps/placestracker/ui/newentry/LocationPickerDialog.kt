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
import com.d_drostes_apps.placestracker.R
import com.google.android.material.button.MaterialButton

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
                    selectedLat = lat
                    selectedLon = lon
                    coordText.text = "Lat: ${String.format("%.4f", lat)}, Lon: ${String.format("%.4f", lon)}"
                }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Mapbox is ready
            }
        }

        val html = requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)

        btnClose.setOnClickListener { dismiss() }
        
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

    override fun getTheme(): Int = com.google.android.material.R.style.Theme_Material3_Light_Dialog
}
