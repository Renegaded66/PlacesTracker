package com.d_drostes_apps.placestracker.ui.newtrip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.data.TripLocation
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.service.TrackingService
import com.d_drostes_apps.placestracker.utils.SharingManager
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class TripDetailFragment : Fragment(R.layout.fragment_trip_detail) {

    private lateinit var mapboxWebView: WebView
    private lateinit var cardMap: MaterialCardView
    private lateinit var tvNoLocation: View
    private lateinit var llFlags: LinearLayout
    private lateinit var cvCountryName: MaterialCardView
    private lateinit var tvCountryNamePopup: TextView
    private lateinit var switchTracking: SwitchMaterial
    private lateinit var cbShowMiniStops: MaterialCheckBox
    private lateinit var rvStops: RecyclerView
    
    private var currentTrip: Trip? = null
    private var allStops: List<TripStop> = emptyList()
    private var allLocations: List<TripLocation> = emptyList()
    private var tripId: Int = -1
    private var isMapFullscreen = false
    private var tripAdapter: TripStopsAdapter? = null
    private var lastFocusedStopId: Int? = null
    
    private val expandedSections = MutableStateFlow<Set<String>>(emptySet())
    private val showMiniStopsList = MutableStateFlow(true)

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (locationGranted) {
            startTrackingService()
            startUserLocationUpdates()
        } else {
            switchTracking.isChecked = false
            Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tripId = arguments?.getInt("tripId") ?: return
        val app = (requireActivity().application as PlacesApplication)
        val tripDao = app.database.tripDao()
        val userDao = app.userDao

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbarTrip)
        val tvTitle = view.findViewById<TextView>(R.id.tvTripDetailTitle)
        rvStops = view.findViewById(R.id.rvTripDetailStops)
        val btnFullscreen = view.findViewById<ImageButton>(R.id.btnFullscreenMap)
        val appBar = view.findViewById<AppBarLayout>(R.id.appBarLayout)
        val nestedScroll = view.findViewById<NestedScrollView>(R.id.tripNestedScroll)
        
        switchTracking = view.findViewById(R.id.switchTracking)
        cbShowMiniStops = view.findViewById(R.id.cbShowMiniStops)

        cardMap = view.findViewById(R.id.cardTripMap)
        mapboxWebView = view.findViewById(R.id.tripCesiumWebView)
        mapboxWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        tvNoLocation = view.findViewById(R.id.tvNoLocation)
        llFlags = view.findViewById(R.id.llTripFlags)
        cvCountryName = view.findViewById(R.id.cvTripCountryName)
        tvCountryNamePopup = view.findViewById(R.id.tvTripCountryNamePopup)

        setupMapboxWebView()

        mapboxWebView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        btnFullscreen.setOnClickListener {
            toggleFullscreen(appBar, btnFullscreen, nestedScroll)
        }

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        view.findViewById<View>(R.id.tripDetailRootLayout).setOnClickListener {
            cvCountryName.visibility = View.GONE
        }

        toolbar.inflateMenu(R.menu.menu_entry_detail)
        
        tripAdapter = TripStopsAdapter(emptyList(), 
            onStopClick = { stop ->
                val bundle = Bundle().apply { putInt("stopId", stop.id) }
                findNavController().navigate(R.id.action_tripDetailFragment_to_tripStopDetailFragment, bundle)
            }, 
            onMiniStopClick = { location ->
                val bundle = Bundle().apply {
                    putInt("tripId", tripId)
                    putFloat("preLat", location.latitude.toFloat())
                    putFloat("preLon", location.longitude.toFloat())
                    putLong("preTime", location.timestamp)
                    putLong("preLocationId", location.id)
                    putBoolean("directAddStop", true)
                }
                findNavController().navigate(R.id.action_tripDetailFragment_to_newTripFragment, bundle)
            },
            onDeleteMiniStop = { location ->
                lifecycleScope.launch {
                    tripDao.deleteLocation(location)
                }
            },
            onToggleExpand = { sectionId ->
                val current = expandedSections.value
                expandedSections.value = if (current.contains(sectionId)) current - sectionId else current + sectionId
            },
            onConfirmDraft = { stop ->
                val bundle = Bundle().apply {
                    putInt("tripId", tripId)
                    putBoolean("directAddStop", true)
                    putInt("stopId", stop.id) 
                }
                findNavController().navigate(R.id.action_tripDetailFragment_to_newTripFragment, bundle)
            }
        )
        rvStops.layoutManager = LinearLayoutManager(requireContext())
        rvStops.adapter = tripAdapter

        // Scroll-Listener for Auto-Zoom
        // Scroll-Listener for Auto-Zoom
        nestedScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            // Wenn der Nutzer ganz nach oben scrollt, zentrieren wir die gesamte Route wieder
            if (scrollY <= 10) {
                if (lastFocusedStopId != null) {
                    lastFocusedStopId = null
                    mapboxWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint(null, null);", null)
                }
                return@OnScrollChangeListener
            }

            val childCount = rvStops.childCount
            for (i in 0 until childCount) {
                val child = rvStops.getChildAt(i)

                // Absolute Y-Position des Elements relativ zur ScrollView berechnen
                val absoluteTop = rvStops.top + child.top
                val absoluteBottom = rvStops.top + child.bottom

                // Prüfen, ob das Element gerade oben an der Kante anliegt (mit 150px Puffer)
                if (absoluteTop <= scrollY + 150 && absoluteBottom > scrollY) {
                    val position = rvStops.getChildAdapterPosition(child)
                    if (position != RecyclerView.NO_POSITION) {
                        val item = tripAdapter?.getItemAt(position)

                        if (item is TripItem.Stop) {
                            if (lastFocusedStopId != item.stop.id) {
                                // ID sofort speichern, damit es nicht pro Pixel feuert
                                lastFocusedStopId = item.stop.id

                                // Nur an die Karte senden, wenn ein Standort da ist
                                item.stop.location?.split(",")?.let { coords ->
                                    if (coords.size == 2) {
                                        val lat = coords[0].toDoubleOrNull()
                                        val lon = coords[1].toDoubleOrNull()
                                        if (lat != null && lon != null) {
                                            mapboxWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($lat, $lon);", null)
                                        }
                                    }
                                }
                            }
                        } else if (item is TripItem.MiniStop) {
                            // Bonus: Wenn du Mini-Stopps eingeblendet hast, zoomt die Karte nun auch dorthin!
                            val miniId = -(item.location.id.toInt()) // Minus, um ID-Kollisionen mit normalen Stopps zu vermeiden
                            if (lastFocusedStopId != miniId) {
                                lastFocusedStopId = miniId
                                mapboxWebView.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint(${item.location.latitude}, ${item.location.longitude});", null)
                            }
                        }
                    }
                    break // Element gefunden, Schleife abbrechen
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            userDao.getUserProfile().collectLatest { profile ->
                profile?.themeColor?.let { color ->
                    ThemeHelper.applyThemeColor(view, color)
                }
            }
        }

        lifecycleScope.launch {
            val trip = tripDao.getTripById(tripId)
            trip?.let { t ->
                currentTrip = t
                tvTitle.text = t.title
                switchTracking.isChecked = t.isTrackingActive
                
                val isShared = t.friendId != null
                toolbar.menu.findItem(R.id.action_edit)?.isVisible = !isShared
                toolbar.menu.findItem(R.id.action_delete)?.isVisible = !isShared
                switchTracking.visibility = if (isShared) View.GONE else View.VISIBLE
                
                toolbar.menu.add(0, R.id.action_share, 0, "Teilen").apply {
                    setIcon(R.drawable.ic_share)
                    setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                    isVisible = !isShared
                }
            }

            switchTracking.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checkPermissionsAndStartService() else stopTrackingService()
            }

            combine(
                tripDao.getStopsForTrip(tripId),
                tripDao.getLocationsForTrip(tripId),
                expandedSections,
                showMiniStopsList
            ) { stops, locations, expanded, showMini ->
                allStops = stops.sortedBy { it.date }
                allLocations = locations.filter { !it.isConvertedToStop }.sortedBy { it.timestamp }
                
                val items = mutableListOf<TripItem>()
                if (!showMini) {
                    items.addAll(allStops.map { TripItem.Stop(it) })
                } else {
                    val beforeFirst = allLocations.filter { allStops.isEmpty() || it.timestamp < allStops.first().date }
                    if (beforeFirst.isNotEmpty()) {
                        items.add(TripItem.MiniStopExpand(expanded.contains("start"), beforeFirst.size, "start"))
                        if (expanded.contains("start")) items.addAll(beforeFirst.map { TripItem.MiniStop(it) })
                    }

                    allStops.forEachIndexed { index, stop ->
                        items.add(TripItem.Stop(stop))
                        val nextStopDate = if (index < allStops.size - 1) allStops[index + 1].date else Long.MAX_VALUE
                        val between = allLocations.filter { it.timestamp > stop.date && it.timestamp < nextStopDate }
                        if (between.isNotEmpty()) {
                            val sectionId = "after_${stop.id}"
                            items.add(TripItem.MiniStopExpand(expanded.contains(sectionId), between.size, sectionId))
                            if (expanded.contains(sectionId)) items.addAll(between.map { TripItem.MiniStop(it) })
                        }
                    }
                }
                items
            }.collect { items ->
                tripAdapter?.updateItems(items)
                updateTripRoute()
                loadCountryFlags(allStops)
            }
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    val bundle = Bundle().apply {
                        putInt("tripId", tripId)
                        putString("title", "Trip bearbeiten")
                    }
                    findNavController().navigate(R.id.action_tripDetailFragment_to_newTripFragment, bundle)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation(app)
                    true
                }
                R.id.action_share -> {
                    handleShare()
                    true
                }
                else -> false
            }
        }

        cbShowMiniStops.setOnCheckedChangeListener { _, isChecked ->
            showMiniStopsList.value = isChecked
        }
        
        checkPermissionsAndStartUserLocation()
    }

    private fun handleShare() {
        val trip = currentTrip ?: return
        val app = (requireActivity().application as PlacesApplication)
        lifecycleScope.launch {
            val profile = app.database.userDao().getUserProfile().first()
            if (profile != null) {
                val sharingManager = SharingManager(requireContext(), app.database)
                val locations = app.database.tripDao().getLocationsForTripSync(tripId)
                sharingManager.shareTrip(trip, allStops, locations, profile)
            } else {
                Toast.makeText(requireContext(), "Bitte erstelle zuerst ein Profil", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFullscreen(appBar: AppBarLayout, btn: ImageButton, nestedScroll: NestedScrollView) {
        isMapFullscreen = !isMapFullscreen
        val params = cardMap.layoutParams as AppBarLayout.LayoutParams
        if (isMapFullscreen) {
            view?.findViewById<View>(R.id.toolbarTrip)?.visibility = View.GONE
            view?.findViewById<View>(R.id.tvNoLocation)?.visibility = View.GONE
            nestedScroll.visibility = View.GONE
            
            params.height = resources.displayMetrics.heightPixels
            params.setMargins(8, 8, 8, 8)
            btn.setImageResource(R.drawable.ic_fullscreen_exit)
        } else {
            view?.findViewById<View>(R.id.toolbarTrip)?.visibility = View.VISIBLE
            nestedScroll.visibility = View.VISIBLE
            
            params.height = (250 * resources.displayMetrics.density).toInt()
            params.setMargins((16 * resources.displayMetrics.density).toInt(), 
                             (8 * resources.displayMetrics.density).toInt(), 
                             (16 * resources.displayMetrics.density).toInt(), 
                             (12 * resources.displayMetrics.density).toInt())
            btn.setImageResource(R.drawable.ic_fullscreen)
        }
        cardMap.layoutParams = params
    }

    private fun checkPermissionsAndStartUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startUserLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startUserLocationUpdates() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    mapboxWebView.evaluateJavascript("javascript:if(window.setUserLocation) window.setUserLocation(${it.latitude}, ${it.longitude});", null)
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            startTrackingService()
            startUserLocationUpdates()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startTrackingService() {
        lifecycleScope.launch {
            val app = (requireActivity().application as PlacesApplication)
            app.database.tripDao().updateTrackingStatus(tripId, true)
            val intent = Intent(requireContext(), TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_TRACKING
                putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
            }
            requireContext().startForegroundService(intent)
        }
    }

    private fun stopTrackingService() {
        lifecycleScope.launch {
            val app = (requireActivity().application as PlacesApplication)
            app.database.tripDao().updateTrackingStatus(tripId, false)
            val intent = Intent(requireContext(), TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP_TRACKING
            }
            requireContext().startService(intent)
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
        mapboxWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                updateTripRoute()
                startUserLocationUpdates()
            }
        }
        val html = try {
            requireContext().assets.open("mapbox_globe.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }
        mapboxWebView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun updateTripRoute() {
        lifecycleScope.launch {
            val jsonArray = withContext(Dispatchers.Default) {
                val array = JSONArray()
                val allPoints = (allStops.map { it.date to it } + allLocations.map { it.timestamp to it })
                    .sortedBy { it.first }

                allPoints.forEach { (_, item) ->
                    val point = JSONObject()
                    when (item) {
                        is TripStop -> {
                            item.location?.split(",")?.let { coords ->
                                if (coords.size == 2) {
                                    point.put("lat", coords[0].toDouble())
                                    point.put("lon", coords[1].toDouble())
                                    val stopImg = item.coverImage ?: item.media.firstOrNull()
                                    point.put("image", stopImg?.let { getBase64Thumbnail(it) })
                                    point.put("isMini", false)
                                    array.put(point)
                                }
                            }
                        }
                        is TripLocation -> {
                            point.put("lat", item.latitude)
                            point.put("lon", item.longitude)
                            point.put("isMini", true)
                            array.put(point)
                        }
                    }
                }
                array
            }

            if (jsonArray.length() == 0) {
                cardMap.visibility = View.GONE
                tvNoLocation.visibility = View.VISIBLE
            } else {
                cardMap.visibility = View.VISIBLE
                tvNoLocation.visibility = View.GONE
                mapboxWebView.evaluateJavascript("javascript:if(window.setTripPath) window.setTripPath(${jsonArray.toString()});", null)
            }
        }
    }

    private fun getBase64Thumbnail(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
            val resized = Bitmap.createScaledBitmap(bitmap, 80, 80, true)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val bytes = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    private fun loadCountryFlags(stops: List<TripStop>) {
        lifecycleScope.launch {
            val countryCodes = mutableSetOf<String>()
            val countryNames = mutableMapOf<String, String>()
            stops.forEach { stop ->
                stop.location?.let { loc ->
                    getCountryInfo(requireContext(), loc)?.let { info ->
                        countryCodes.add(info.first)
                        countryNames[info.first] = info.second
                    }
                }
            }
            llFlags.removeAllViews()
            countryCodes.forEach { code ->
                val flagView = TextView(requireContext()).apply {
                    text = getFlagEmoji(code)
                    textSize = 24f
                    setPadding(0, 0, 16, 0)
                    setOnClickListener {
                        tvCountryNamePopup.text = countryNames[code] ?: "Unbekannt"
                        cvCountryName.visibility = View.VISIBLE
                    }
                }
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
            if (addr?.countryCode != null && addr.countryName != null) addr.countryCode to addr.countryName else null
        } catch (e: Exception) { null }
    }

    private fun getFlagEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    private fun showDeleteConfirmation(app: PlacesApplication) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_entry)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.save) { _, _ ->
                lifecycleScope.launch {
                    val trip = app.database.tripDao().getTripById(tripId)
                    trip?.let { app.database.tripDao().deleteTrip(it) }
                    findNavController().navigateUp()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
