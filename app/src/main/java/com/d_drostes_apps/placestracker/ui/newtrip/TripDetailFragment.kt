package com.d_drostes_apps.placestracker.ui.newtrip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.FeedItem
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.data.TripLocation
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.service.TrackingService
import com.d_drostes_apps.placestracker.ui.feed.FeedFragment
import com.d_drostes_apps.placestracker.utils.GlobeUtils
import com.d_drostes_apps.placestracker.utils.SharingManager
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.d_drostes_apps.placestracker.ui.feed.MediaDialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class TripDetailFragment : Fragment(R.layout.fragment_trip_detail) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_trip_detail, container, false)
    }

    private var mapboxWebView: WebView? = null
    private lateinit var tvNoLocation: View
    private lateinit var llFlags: LinearLayout
    private lateinit var cvCountryName: MaterialCardView
    private lateinit var tvCountryNamePopup: TextView
    private lateinit var rvStops: RecyclerView
    private lateinit var tvTripNotes: TextView
    private lateinit var cvTripNotes: MaterialCardView

    private lateinit var cvDayIndicator: MaterialCardView
    private lateinit var tvDayNumber: TextView
    private lateinit var nestedScroll: NestedScrollView
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    private var currentTrip: Trip? = null
    private var allStops: List<TripStop> = emptyList()
    private var allLocations: List<TripLocation> = emptyList()
    private var tripId: Int = -1
    private var isMapFullscreen = false
    private var tripAdapter: TripStopsAdapter? = null
    private var lastFocusedStopId: Int? = null

    private val expandedSections = MutableStateFlow<Set<String>>(emptySet())

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (locationGranted) {
            startTrackingService()
            startUserLocationUpdates()
        } else {
            context?.let {
                Toast.makeText(it, "Location permission required", Toast.LENGTH_SHORT).show()
            }
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
        tvTripNotes = view.findViewById(R.id.tvTripDetailNotes)
        cvTripNotes = view.findViewById(R.id.cvTripNotes)
        rvStops = view.findViewById(R.id.rvTripDetailStops)
        val btnFullscreen = view.findViewById<ImageButton>(R.id.btnFullscreenMap)
        nestedScroll = view.findViewById(R.id.tripNestedScroll)

        cvDayIndicator = view.findViewById(R.id.cvDayIndicator)
        tvDayNumber = view.findViewById(R.id.tvDayNumber)

        mapboxWebView = view.findViewById(R.id.tripCesiumWebView)
        tvNoLocation = view.findViewById(R.id.tvNoLocation)
        llFlags = view.findViewById(R.id.llTripFlags)
        cvCountryName = view.findViewById(R.id.cvTripCountryName)
        tvCountryNamePopup = view.findViewById(R.id.tvTripCountryNamePopup)

        val isInline = parentFragment is FeedFragment
        val bottomSheet = view.findViewById<View>(R.id.bottomSheetTrip)

        if (isInline) {
            view.findViewById<View>(R.id.mapContainer)?.visibility = View.GONE
            view.findViewById<View>(R.id.dragHandle)?.visibility = View.GONE

            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener {
                (parentFragment as? FeedFragment)?.handleBack()
            }

            try {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.isDraggable = false
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            } catch (e: Exception) {}

            bottomSheet?.elevation = 0f
            nestedScroll.setPadding(0, 0, 0, (100 * resources.displayMetrics.density).toInt())
        } else {
            mapboxWebView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            if (mapboxWebView != null) setupCesiumWebView()

            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

            if (bottomSheet != null) {
                try {
                    bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                    bottomSheetBehavior?.isFitToContents = false
                    bottomSheetBehavior?.halfExpandedRatio = 0.6f
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                } catch (e: IllegalArgumentException) {}
            }

            nestedScroll.setOnTouchListener { _, event ->
                if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    bottomSheetBehavior?.isDraggable = false
                    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                        bottomSheetBehavior?.isDraggable = true
                    }
                } else if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior?.isDraggable = !nestedScroll.canScrollVertically(-1)
                }
                false
            }
        }

        view.findViewById<View>(R.id.llTripContent)?.apply {
            alpha = 0f
            translationY = 50f
            animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(150).start()
        }

        mapboxWebView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        btnFullscreen?.setOnClickListener {
            toggleFullscreen()
        }

        view.findViewById<View>(R.id.tripDetailRootLayout)?.setOnClickListener {
            cvCountryName.visibility = View.GONE
        }

        toolbar.inflateMenu(R.menu.menu_entry_detail)

        tripAdapter = TripStopsAdapter(emptyList(),
            onStopClick = { stop ->
                if (isInline) {
                    (parentFragment as? FeedFragment)?.navigateToDetail(FeedItem.TripItem(currentTrip!!, allStops), stop.id)
                } else {
                    val bundle = Bundle().apply { putInt("stopId", stop.id) }
                    findNavController().navigate(R.id.action_tripDetailFragment_to_tripStopDetailFragment, bundle)
                }
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
            onTransportClick = { stopId, currentMode ->
                showTransportSelection(stopId, currentMode)
            },
            onConfirmDraft = { stop ->
                val bundle = Bundle().apply {
                    putInt("tripId", tripId)
                    putBoolean("directAddStop", true)
                    putInt("stopId", stop.id)
                }
                val destination = if (isInline) R.id.action_feedFragment_to_newTripFragment else R.id.action_tripDetailFragment_to_newTripFragment
                findNavController().navigate(destination, bundle)
            },
            onAddStopClick = {
                val currentContext = context ?: return@TripStopsAdapter
                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(currentContext)
                if (androidx.core.content.ContextCompat.checkSelfPermission(currentContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location: android.location.Location? ->
                            val bundle = Bundle().apply {
                                putInt("tripId", tripId)
                                putBoolean("directAddStop", true)
                                location?.let {
                                    putFloat("preLat", it.latitude.toFloat())
                                    putFloat("preLon", it.longitude.toFloat())
                                    putLong("preTime", System.currentTimeMillis())
                                }
                            }
                            val destination = if (isInline) R.id.action_feedFragment_to_newTripFragment else R.id.action_tripDetailFragment_to_newTripFragment
                            findNavController().navigate(destination, bundle)
                        }
                } else {
                    val bundle = Bundle().apply {
                        putInt("tripId", tripId)
                        putBoolean("directAddStop", true)
                        putLong("preTime", System.currentTimeMillis())
                    }
                    val destination = if (isInline) R.id.action_feedFragment_to_newTripFragment else R.id.action_tripDetailFragment_to_newTripFragment
                    findNavController().navigate(destination, bundle)
                }
            },
            onMediaClick = { path, view ->
                val dialog = MediaDialogFragment.newInstance(ArrayList(listOf(path)), 0)
                dialog.show(parentFragmentManager, "MediaFullscreen")
            },
            scope = viewLifecycleOwner.lifecycleScope
        )
        rvStops.layoutManager = LinearLayoutManager(requireContext())
        rvStops.adapter = tripAdapter

        nestedScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            updateDayIndicatorPosition()

            if (scrollY <= 5) {
                lastFocusedStopId = null
                val feedFragment = parentFragmentManager.fragments.find { it is FeedFragment } as? FeedFragment
                val globe = if (isInline) (parentFragment as? FeedFragment)?.getGlobe() else feedFragment?.getGlobe() ?: mapboxWebView

                val js = """
                    javascript:(function(){
                        try {
                            if (typeof viewer !== 'undefined') {
                                if (typeof polylineEntities !== 'undefined' && polylineEntities.length > 0) {
                                    viewer.zoomTo(polylineEntities, new Cesium.HeadingPitchRange(0, -Math.PI/2, 0));
                                } else if (typeof detailEntities !== 'undefined' && detailEntities.length > 0) {
                                    viewer.zoomTo(detailEntities, new Cesium.HeadingPitchRange(0, -Math.PI/2, 0));
                                }
                            }
                        } catch(e) {}
                    })();
                """.trimIndent()
                globe?.evaluateJavascript(js, null)
                return@OnScrollChangeListener
            }

            val childCount = rvStops.childCount
            for (i in 0 until childCount) {
                val child = rvStops.getChildAt(i)
                val absoluteTop = rvStops.top + child.top
                val absoluteBottom = rvStops.top + child.bottom

                if (absoluteTop <= scrollY + 150 && absoluteBottom > scrollY) {
                    val position = rvStops.getChildAdapterPosition(child)
                    if (position != RecyclerView.NO_POSITION) {
                        val visibleDate = getDateForPosition(position)
                        if (visibleDate != null) {
                            updateCurrentDay(visibleDate)
                        }

                        val item = tripAdapter?.getItemAt(position)
                        val feedFragment = parentFragmentManager.fragments.find { it is FeedFragment } as? FeedFragment
                        val globe = if (isInline) (parentFragment as? FeedFragment)?.getGlobe() else feedFragment?.getGlobe() ?: mapboxWebView

                        if (item is TripItem.Stop) {
                            if (lastFocusedStopId != item.stop.id) {
                                lastFocusedStopId = item.stop.id
                                item.stop.location?.split(",")?.let { coords ->
                                    if (coords.size == 2) {
                                        val lat = coords[0].toDoubleOrNull()
                                        val lon = coords[1].toDoubleOrNull()
                                        if (lat != null && lon != null) {
                                            globe?.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint($lat, $lon);", null)
                                        }
                                    }
                                }
                            }
                        } else if (item is TripItem.MiniStop) {
                            updateCurrentDay(item.location.timestamp)
                            val miniId = -(item.location.id.toInt())
                            if (lastFocusedStopId != miniId) {
                                lastFocusedStopId = miniId
                                globe?.evaluateJavascript("javascript:if(window.zoomToPoint) window.zoomToPoint(${item.location.latitude}, ${item.location.longitude});", null)
                            }
                        }
                    }
                    break
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

                if (!t.notes.isNullOrBlank()) {
                    tvTripNotes.text = t.notes
                    cvTripNotes.visibility = View.VISIBLE
                } else {
                    cvTripNotes.visibility = View.GONE
                }

                val isShared = t.friendId != null

                val fabEdit = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabEditTrip)
                fabEdit?.visibility = View.VISIBLE
                fabEdit?.setOnClickListener {
                    val bundle = Bundle().apply {
                        putInt("tripId", tripId)
                        putString("title", "Trip bearbeiten")
                    }
                    findNavController().navigate(R.id.newTripFragment, bundle)
                }

                toolbar.menu.findItem(R.id.action_edit)?.apply {
                    isVisible = true
                    setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                toolbar.menu.findItem(R.id.action_delete)?.apply {
                    isVisible = true
                    setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                toolbar.menu.findItem(R.id.action_add_shared)?.isVisible = isShared

                if (toolbar.menu.findItem(R.id.action_share) == null) {
                    toolbar.menu.add(0, R.id.action_share, 0, "Teilen").apply {
                        setIcon(R.drawable.ic_share)
                        setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                    }
                }
                toolbar.menu.findItem(R.id.action_share)?.isVisible = true
            }

            combine(
                tripDao.getStopsForTrip(tripId),
                tripDao.getLocationsForTrip(tripId),
                expandedSections
            ) { stops, locations, expanded ->
                allStops = stops.sortedBy { it.date }
                allLocations = locations.filter { !it.isConvertedToStop }.sortedBy { it.timestamp }

                val items = mutableListOf<TripItem>()

                if (allStops.isEmpty()) {
                    items.addAll(allLocations.map { TripItem.MiniStop(it) })
                } else {
                    val beforeFirst = allLocations.filter { it.timestamp < allStops.first().date }
                    if (beforeFirst.isNotEmpty()) {
                        items.add(TripItem.MiniStopExpand(expanded.contains("start"), beforeFirst.size, "start"))
                        if (expanded.contains("start")) items.addAll(beforeFirst.map { TripItem.MiniStop(it) })
                    }

                    allStops.forEachIndexed { index, stop ->
                        items.add(TripItem.Stop(stop))

                        if (index < allStops.size - 1) {
                            val nextStop = allStops[index + 1]
                            items.add(TripItem.Transport(stop.id, nextStop.id, nextStop.transportMode))

                            val between = allLocations.filter { it.timestamp > stop.date && it.timestamp < nextStop.date }
                            if (between.isNotEmpty()) {
                                val sectionId = "after_${stop.id}"
                                items.add(TripItem.MiniStopExpand(expanded.contains(sectionId), between.size, sectionId))
                                if (expanded.contains(sectionId)) items.addAll(between.map { TripItem.MiniStop(it) })
                            }
                        } else {
                            val afterLast = allLocations.filter { it.timestamp > stop.date }
                            if (afterLast.isNotEmpty()) {
                                val sectionId = "after_last"
                                items.add(TripItem.MiniStopExpand(expanded.contains(sectionId), afterLast.size, sectionId))
                                if (expanded.contains(sectionId)) items.addAll(afterLast.map { TripItem.MiniStop(it) })
                            }
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
                    findNavController().navigate(R.id.newTripFragment, bundle)
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
                R.id.action_add_shared -> {
                    showAddSharedTripConfirmation()
                    true
                }
                else -> false
            }
        }

        checkPermissionsAndStartUserLocation()
    }

    private fun showAddSharedTripConfirmation() {
        val trip = currentTrip ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_my_feed)
            .setMessage(R.string.add_to_my_feed_confirm_trip)
            .setPositiveButton(R.string.save) { _, _ ->
                lifecycleScope.launch {
                    val app = (requireActivity().application as PlacesApplication)
                    val tripDao = app.database.tripDao()
                    val newTrip = trip.copy(id = 0, friendId = null)
                    val newTripId = tripDao.insertTrip(newTrip).toInt()
                    val stops = tripDao.getStopsForTripSync(tripId)
                    stops.forEach { stop ->
                        val newStop = stop.copy(id = 0, tripId = newTripId)
                        tripDao.insertStop(newStop)
                    }
                    val locations = tripDao.getLocationsForTripSync(tripId)
                    locations.forEach { location ->
                        val newLocation = location.copy(id = 0, tripId = newTripId)
                        tripDao.insertLocation(newLocation)
                    }
                    Toast.makeText(requireContext(), R.string.added_to_my_feed_success, Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateDayIndicatorPosition() {
        val scrollMax = nestedScroll.getChildAt(0).height - nestedScroll.height
        if (scrollMax <= 0) return
        val percent = nestedScroll.scrollY.toFloat() / scrollMax
        val parentContainer = cvDayIndicator.parent as View
        val maxTravelDistance = parentContainer.height - cvDayIndicator.height
        var targetY = percent * maxTravelDistance
        if (targetY < 0f) targetY = 0f
        if (targetY > maxTravelDistance.toFloat()) targetY = maxTravelDistance.toFloat()
        cvDayIndicator.translationY = targetY
    }

    private fun updateCurrentDay(timestamp: Long) {
        val firstDate = getFirstItemDate()
        if (firstDate == 0L) return
        val calStart = Calendar.getInstance().apply {
            timeInMillis = firstDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val calCurrent = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diff = calCurrent.timeInMillis - calStart.timeInMillis
        val day = (diff / (1000 * 60 * 60 * 24)).toInt() + 1
        tvDayNumber.text = day.toString()
    }

    private fun showTransportSelection(stopId: Int, currentMode: String?) {
        val modes = arrayOf("Auto", "Fahrrad", "Flugzeug", "Zug", "Zu Fuß", "Keines")
        val modeKeys = arrayOf("car", "bike", "plane", "train", "walk", null)

        AlertDialog.Builder(requireContext())
            .setTitle("Transportmittel wählen")
            .setItems(modes) { _, which ->
                val selectedMode = modeKeys[which]
                val hasAnyModeSet = allStops.any { it.transportMode != null }
                if (!hasAnyModeSet) {
                    askApplyToAll(stopId, selectedMode)
                } else {
                    lifecycleScope.launch {
                        (requireActivity().application as PlacesApplication).database.tripDao()
                            .updateTransportMode(stopId, selectedMode)
                        updateTripRoute()
                    }
                }
            }
            .show()
    }

    private fun askApplyToAll(stopId: Int, mode: String?) {
        AlertDialog.Builder(requireContext())
            .setTitle("Transportmittel anwenden")
            .setMessage("Soll dieses Transportmittel für den gesamten Trip übernommen werden?")
            .setPositiveButton("Ja") { _, _ ->
                lifecycleScope.launch {
                    val tripDao = (requireActivity().application as PlacesApplication).database.tripDao()
                    tripDao.updateAllTransportModes(tripId, mode)
                    updateTripRoute()
                }
            }
            .setNegativeButton("Nein") { _, _ ->
                lifecycleScope.launch {
                    val tripDao = (requireActivity().application as PlacesApplication).database.tripDao()
                    tripDao.updateTransportMode(stopId, mode)
                    updateTripRoute()
                }
            }
            .show()
    }

    private fun handleShare() {
        val trip = currentTrip ?: return
        val activity = activity ?: return
        val app = (activity.application as PlacesApplication)
        lifecycleScope.launch {
            val profile = app.database.userDao().getUserProfile().first()
            val currentContext = context ?: return@launch
            if (profile != null) {
                val sharingManager = SharingManager(currentContext, app.database)
                val locations = app.database.tripDao().getLocationsForTripSync(tripId)
                sharingManager.shareTrip(trip, allStops, locations, profile)
            } else {
                Toast.makeText(currentContext, "Bitte erstelle zuerst ein Profil", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFullscreen() {
        isMapFullscreen = !isMapFullscreen
        val container = view?.findViewById<View>(R.id.mapContainer) ?: return
        if (isMapFullscreen) {
            view?.findViewById<View>(R.id.toolbarTrip)?.visibility = View.GONE
            view?.findViewById<View>(R.id.bottomSheetTrip)?.visibility = View.GONE
            val params = container.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = 0
            container.layoutParams = params
            view?.findViewById<ImageButton>(R.id.btnFullscreenMap)?.setImageResource(R.drawable.ic_fullscreen_exit)
        } else {
            view?.findViewById<View>(R.id.toolbarTrip)?.visibility = View.VISIBLE
            view?.findViewById<View>(R.id.bottomSheetTrip)?.visibility = View.VISIBLE
            val params = container.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = (120 * resources.displayMetrics.density).toInt()
            container.layoutParams = params
            view?.findViewById<ImageButton>(R.id.btnFullscreenMap)?.setImageResource(R.drawable.ic_fullscreen)
        }
    }

    private fun checkPermissionsAndStartUserLocation() {
        val currentContext = context ?: return
        if (ContextCompat.checkSelfPermission(currentContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startUserLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startUserLocationUpdates() {
        val currentContext = context ?: return
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(currentContext)
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (!isAdded) return@addOnSuccessListener
                location?.let {
                    val feedFragment = parentFragmentManager.fragments.find { f -> f is FeedFragment } as? FeedFragment
                    val globe = if (parentFragment is FeedFragment) (parentFragment as FeedFragment).getGlobe() else feedFragment?.getGlobe() ?: mapboxWebView
                    globe?.evaluateJavascript("javascript:if(window.setUserLocation) window.setUserLocation(${it.latitude}, ${it.longitude});", null)
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startTrackingService() {
        lifecycleScope.launch {
            val activity = activity ?: return@launch
            val app = (activity.application as PlacesApplication)
            app.database.tripDao().updateTrackingStatus(tripId, true)
            val currentContext = context ?: return@launch
            val intent = Intent(currentContext, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_TRACKING
                putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
            }
            currentContext.startForegroundService(intent)
        }
    }

    private fun setupCesiumWebView() {
        mapboxWebView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        class JsInterface {
            @JavascriptInterface
            fun checkAndMarkSpun(): Boolean = GlobeUtils.checkAndMarkSpun()
        }

        mapboxWebView?.addJavascriptInterface(JsInterface(), "Android")

        mapboxWebView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isAdded) return
                updateTripRoute()
                startUserLocationUpdates()
            }
        }
        val currentContext = context ?: return
        val html = try {
            currentContext.assets.open("cesium_globe.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }
        mapboxWebView?.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun updateTripRoute() {
        lifecycleScope.launch {
            val tripDao = (requireActivity().application as PlacesApplication).database.tripDao()
            val stopsFromDb = tripDao.getStopsForTripSync(tripId)
            val locationsFromDb = tripDao.getLocationsForTripSync(tripId).filter { !it.isConvertedToStop }

            val jsonArray = withContext(Dispatchers.Default) {
                val array = JSONArray()
                val allPoints = (stopsFromDb.map { it.date to it } + locationsFromDb.map { it.timestamp to it })
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
                                    point.put("image", GlobeUtils.getBase64Thumbnail(stopImg))
                                    point.put("isMini", false)
                                    point.put("transportMode", item.transportMode ?: "")
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

            val feedFragment = parentFragmentManager.fragments.find { it is FeedFragment } as? FeedFragment
            val globe = if (parentFragment is FeedFragment) (parentFragment as FeedFragment).getGlobe() else feedFragment?.getGlobe() ?: mapboxWebView

            if (jsonArray.length() == 0) {
                if (parentFragment !is FeedFragment) mapboxWebView?.visibility = View.GONE
                tvNoLocation.visibility = View.VISIBLE
            } else {
                if (parentFragment !is FeedFragment) mapboxWebView?.visibility = View.VISIBLE
                tvNoLocation.visibility = View.GONE
                val pathData = jsonArray.toString()
                globe?.evaluateJavascript("javascript:window.lastPathData = '$pathData'; if(window.setTripPath) window.setTripPath('$pathData');", null)
            }
        }
    }

    private fun loadCountryFlags(stops: List<TripStop>) {
        lifecycleScope.launch {
            val currentContext = context ?: return@launch
            val countryCodes = mutableSetOf<String>()
            val countryNames = mutableMapOf<String, String>()
            stops.forEach { stop ->
                stop.location?.let { loc ->
                    getCountryInfo(currentContext, loc)?.let { info ->
                        countryCodes.add(info.first)
                        countryNames[info.first] = info.second
                    }
                }
            }
            if (!isAdded) return@launch
            llFlags.removeAllViews()
            countryCodes.forEach { code ->
                val flagView = TextView(currentContext).apply {
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
            val addr: Address? = addresses?.firstOrNull()
            if (addr?.countryCode != null && addr.countryName != null) addr.countryCode!! to addr.countryName!! else null
        } catch (e: Exception) { null }
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    private fun getFirstItemDate(): Long {
        val firstStopDate = allStops.firstOrNull()?.date ?: Long.MAX_VALUE
        val firstLocDate = allLocations.firstOrNull()?.timestamp ?: Long.MAX_VALUE
        val minDate = minOf(firstStopDate, firstLocDate)
        return if (minDate != Long.MAX_VALUE) minDate else (currentTrip?.date ?: 0L)
    }

    private fun getDateForPosition(position: Int): Long? {
        for (i in position downTo 0) {
            val item = tripAdapter?.getItemAt(i)
            when (item) {
                is TripItem.Stop -> return item.stop.date
                is TripItem.MiniStop -> return item.location.timestamp
                else -> continue
            }
        }
        return null
    }

    private fun showDeleteConfirmation(app: PlacesApplication) {
        val currentContext = context ?: return
        AlertDialog.Builder(currentContext)
            .setTitle(R.string.delete_entry)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.save) { _, _ ->
                lifecycleScope.launch {
                    val trip = app.database.tripDao().getTripById(tripId)
                    trip?.let { app.database.tripDao().deleteTrip(it) }
                    if (isAdded) {
                        if (parentFragment is FeedFragment) (parentFragment as FeedFragment).closeDetail() else findNavController().navigateUp()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}