package com.d_drostes_apps.placestracker.ui.newentry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.d_drostes_apps.placestracker.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay


class LocationPickerBottomSheet(
    private val onLocationSelected: (Double, Double) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var mapView: MapView
    private var selectedMarker: Marker? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_location_picker_osm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))

        mapView = view.findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(5.0)
        mapView.controller.setCenter(GeoPoint(51.5, 7.5)) // default

        // Event Overlay für Klicks
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    selectedMarker?.let { mapView.overlays.remove(it) }
                    val marker = Marker(mapView)
                    marker.position = it
                    marker.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mapView.overlays.add(marker)
                    selectedMarker = marker

                    onLocationSelected(it.latitude, it.longitude)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }

        mapView.overlays.add(MapEventsOverlay(receiver))
    }
}