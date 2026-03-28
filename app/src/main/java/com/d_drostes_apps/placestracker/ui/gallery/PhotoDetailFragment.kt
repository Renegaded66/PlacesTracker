package com.d_drostes_apps.placestracker.ui.gallery

import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.text.SimpleDateFormat
import java.util.*

class PhotoDetailFragment : Fragment(R.layout.fragment_photo_detail) {

    private val args: PhotoDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val tvDate = view.findViewById<TextView>(R.id.tvPhotoDate)
        val tvLocation = view.findViewById<TextView>(R.id.tvPhotoLocation)
        val mapWebView = view.findViewById<WebView>(R.id.detailMapWebView)
        val bottomSheet = view.findViewById<View>(R.id.detailsBottomSheet)
        val behavior = BottomSheetBehavior.from(bottomSheet)

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        toolbar.inflateMenu(R.menu.menu_photo_detail)
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_details) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                true
            } else false
        }

        Glide.with(this).load(args.photoUri).into(photoView)

        val sdf = SimpleDateFormat("dd. MMMM yyyy, HH:mm", Locale.getDefault())
        tvDate.text = "Datum: ${sdf.format(Date(args.photoDate))}"

        if (args.latitude != 0f && args.longitude != 0f) {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(args.latitude.toDouble(), args.longitude.toDouble(), 1)
                tvLocation.text = "Standort: ${addresses?.firstOrNull()?.getAddressLine(0) ?: "${args.latitude}, ${args.longitude}"}"
            } catch (e: Exception) {
                tvLocation.text = "Standort: ${args.latitude}, ${args.longitude}"
            }
            setupMiniMap(mapWebView, args.latitude, args.longitude)
        } else {
            tvLocation.text = "Standort: Unbekannt"
            view.findViewById<View>(R.id.detailMapWebView).visibility = View.GONE
        }
    }

    private fun setupMiniMap(webView: WebView, lat: Float, lon: Float) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        val html = try { requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($lat, $lon, 0.0);", null)
            }
        }
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }
}
