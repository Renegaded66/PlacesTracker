package com.d_drostes_apps.placestracker.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.UserProfile
import com.d_drostes_apps.placestracker.ui.feed.FeedFragment
import com.d_drostes_apps.placestracker.worker.GalleryScanWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*

class AutoDetectionSettingsFragment : Fragment(R.layout.fragment_auto_detection_settings) {

    private lateinit var webView: WebView
    private lateinit var tvHomeCoords: TextView
    private lateinit var switchAutoGallery: MaterialSwitch
    private lateinit var spinnerDistance: AutoCompleteTextView
    private var selectedLat: Double? = null
    private var selectedLon: Double? = null
    private var currentProfile: UserProfile? = null

    // 🌟 NEU: Location Client für GPS
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Launcher für die Galerie-Berechtigungen
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            switchAutoGallery.isChecked = false
            Toast.makeText(requireContext(), "Berechtigungen für Galerie-Zugriff erforderlich", Toast.LENGTH_LONG).show()
        }
    }

    // 🌟 NEU: Eigener Launcher NUR für den GPS-Button
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Standortberechtigung wird benötigt.", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as PlacesApplication
        val userDao = app.database.userDao()

        // 🌟 NEU: Client initialisieren
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        webView = view.findViewById(R.id.homeWebView)
        tvHomeCoords = view.findViewById(R.id.tvHomeCoords)
        switchAutoGallery = view.findViewById(R.id.switchAutoGallery)
        spinnerDistance = view.findViewById(R.id.spinnerDistance)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveAutoSettings)
        val btnCurrentLocation = view.findViewById<FloatingActionButton>(R.id.btnCurrentLocation) // 🌟 NEU

        setupWebView()

        // Distanz-Spinner füllen
        val distances = (0..200 step 5).map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, distances)
        spinnerDistance.setAdapter(adapter)

        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        lifecycleScope.launch {
            currentProfile = userDao.getUserProfile().firstOrNull()
            currentProfile?.let {
                selectedLat = it.homeLatitude
                selectedLon = it.homeLongitude
                switchAutoGallery.isChecked = it.isAutoGalleryScanEnabled
                spinnerDistance.setText(it.autoGalleryScanDistance.toString(), false)
                updateCoordsText()
                if (selectedLat != null && selectedLon != null) {
                    webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation(${selectedLat}, ${selectedLon});", null)
                }
            }
        }

        switchAutoGallery.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkAndRequestPermissions()
        }

        // 🌟 NEU: Klick-Event für den GPS Button
        btnCurrentLocation.setOnClickListener {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }

        btnSave.setOnClickListener {
            if (selectedLat == null || selectedLon == null) {
                Toast.makeText(requireContext(), "Bitte markiere erst dein Zuhause auf der Karte!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val isEnabled = switchAutoGallery.isChecked
                val distance = spinnerDistance.text.toString().toIntOrNull() ?: 20

                val updatedProfile = currentProfile?.copy(
                    homeLatitude = selectedLat,
                    homeLongitude = selectedLon,
                    isAutoGalleryScanEnabled = isEnabled,
                    autoGalleryScanDistance = distance
                ) ?: UserProfile(
                    username = "User",
                    profilePicturePath = null,
                    homeLatitude = selectedLat,
                    homeLongitude = selectedLon,
                    isAutoGalleryScanEnabled = isEnabled,
                    autoGalleryScanDistance = distance
                )
                userDao.insertOrUpdate(updatedProfile)

                if (isEnabled) GalleryScanWorker.enqueue(requireContext())
                else GalleryScanWorker.stop(requireContext())

                Toast.makeText(requireContext(), "Einstellungen gespeichert", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    // 🌟 NEU: Zieht die Koordinaten vom Gerät und markiert sie auf der Karte
    @SuppressLint("MissingPermission") // Wir prüfen die Rechte ja im Launcher
    private fun fetchCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                selectedLat = location.latitude
                selectedLon = location.longitude

                // Koordinaten-Text aktualisieren
                updateCoordsText()

                // Globus anweisen, dorthin zu fliegen und den Punkt zu setzen!
                webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation(${location.latitude}, ${location.longitude});", null)
            } else {
                Toast.makeText(requireContext(), "Standort konnte nicht ermittelt werden. Ist GPS aktiviert?", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onLocationPicked(lat: Double, lon: Double) {
                activity?.runOnUiThread {
                    selectedLat = lat
                    selectedLon = lon
                    updateCoordsText()
                }
            }
            @JavascriptInterface
            fun onMarkerClicked(id: String) {
                // Not needed here but interface expects it
            }
            @JavascriptInterface
            fun checkAndMarkSpun(): Boolean {
                val alreadySpun = FeedFragment.GlobeAnimationState.hasSpunThisSession
                FeedFragment.GlobeAnimationState.hasSpunThisSession = true
                return alreadySpun
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript("javascript:if(window.setPickerMode) window.setPickerMode(true);", null)
                if (selectedLat != null && selectedLon != null) {
                    webView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation(${selectedLat}, ${selectedLon});", null)
                }
            }
        }

        val html = try {
            requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun updateCoordsText() {
        if (selectedLat != null && selectedLon != null) {
            tvHomeCoords.text = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", selectedLat, selectedLon)
        } else tvHomeCoords.text = "Kein Standort festgelegt"
    }
}