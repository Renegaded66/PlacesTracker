package com.d_drostes_apps.placestracker.ui.gallery

import android.annotation.SuppressLint
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class PhotoDetailFragment : Fragment(R.layout.fragment_photo_detail) {

    private val args: PhotoDetailFragmentArgs by navArgs()

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val tvDate = view.findViewById<TextView>(R.id.tvPhotoDate)
        val tvLocation = view.findViewById<TextView>(R.id.tvPhotoLocation)
        val cvLocation = view.findViewById<MaterialCardView>(R.id.cvPhotoLocation)
        val mapWebView = view.findViewById<WebView>(R.id.detailMapWebView)
        val mapCard = view.findViewById<MaterialCardView>(R.id.mapCard)

        val bottomSheet = view.findViewById<View>(R.id.detailsBottomSheet)
        val behavior = BottomSheetBehavior.from(bottomSheet)

        // Animation für das BottomSheet-Inhalt
        view.findViewById<View>(R.id.llPhotoDetailContent)?.apply {
            alpha = 0f
            translationY = 30f
        }

        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    view.findViewById<View>(R.id.llPhotoDetailContent)?.animate()
                        ?.alpha(1f)
                        ?.translationY(0f)
                        ?.setDuration(400)
                        ?.start()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        // Klick auf die 3 Punkte -> Sheet einblenden
        val btnInfo = view.findViewById<ImageButton>(R.id.btnPhotoInfo)
        btnInfo?.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Bild laden
        Glide.with(this)
            .load(args.photoUri)
            .into(photoView)

        // Datum formatieren
        val sdf = SimpleDateFormat("dd. MMMM yyyy, HH:mm", Locale.getDefault())
        tvDate.text = sdf.format(Date(args.photoDate))

        // Standort-Logik
        // Wir prüfen auf != 0.0, da 0.0 oft der Default-Wert bei fehlenden Koordinaten ist.
        // Aber Vorsicht: 0,0 ist ein echter Ort. In dieser App nutzen wir 0f als "nicht vorhanden".
        if (args.latitude != 0f || args.longitude != 0f) {
            cvLocation.visibility = View.VISIBLE
            mapCard.visibility = View.VISIBLE
            
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(args.latitude.toDouble(), args.longitude.toDouble(), 1)
                val addressStr = addresses?.firstOrNull()?.getAddressLine(0) 
                    ?: "${args.latitude}, ${args.longitude}"
                tvLocation.text = addressStr
            } catch (e: Exception) {
                tvLocation.text = "${args.latitude}, ${args.longitude}"
            }

            setupMiniMap(mapWebView, args.latitude, args.longitude)
        } else {
            cvLocation.visibility = View.GONE
            mapCard.visibility = View.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMiniMap(webView: WebView, lat: Float, lon: Float) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun checkAndMarkSpun(): Boolean = true
        }, "Android")

        val html = try { 
            requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() } 
        } catch (e: Exception) { "" }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation($lat, $lon, null);", null)
                webView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($lat, $lon);", null)
            }
        }

        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }
}