package com.d_drostes_apps.placestracker.ui.statistics

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.data.TripStop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class TravelPartnerFragment : Fragment(R.layout.fragment_travel_partner) {

    private lateinit var recycler: RecyclerView
    private lateinit var photoRecycler: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var map: WebView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.rvPartnerEntries)
        photoRecycler = view.findViewById(R.id.rvPartnerPhotos)
        tvTitle = view.findViewById(R.id.tvPartnerTitle)
        map = view.findViewById(R.id.webPartnerMap)

        // back arrow should navigate one step back
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarPartner)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val person = arguments?.getString("person") ?: return
        tvTitle.text = person

        recycler.layoutManager = LinearLayoutManager(requireContext())
        photoRecycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(),3)

        val app = (requireActivity().application as PlacesApplication)

        lifecycleScope.launch {
            val entries = app.repository.allEntries.first()
            val trips = app.tripRepository.allTrips.first()
            val stops = app.tripRepository.allTripStops.first()

            val entryItems = entries.filter { it.people.contains(person) }
            val tripItems = trips.filter { it.people.contains(person) }

            val combined = mutableListOf<Triple<String, Long, String>>()
            val photos = mutableListOf<String>()
            val markerPoints = mutableListOf<String>()
            val stopPoints = mutableListOf<String>()
            val routes = mutableListOf<Pair<String,String>>()

            // entries (events) – markers only, NEVER used for routes
            entryItems.forEach {
                combined.add(Triple(it.title, it.date, "entry:${it.id}"))
                photos.addAll(it.media)
                it.location?.let { loc -> markerPoints.add(loc) }
            }

            // trips
            tripItems.forEach { trip ->
                combined.add(Triple("Trip: ${trip.title}", trip.date, "trip:${trip.id}"))

                val tripStops = stops.filter { it.tripId == trip.id }.sortedBy { it.date }

                // routes only inside same trip
                // build routes ONLY between stops of the same trip
                tripStops.zipWithNext().forEach {
                    val a = it.first.location
                    val b = it.second.location
                    if (a != null && b != null) {
                        routes.add(a to b)
                    }
                }

                tripStops.forEach { stop ->
                    photos.addAll(stop.media)
                    // stop locations are used for map markers and routes
                    stop.location?.let { loc ->
                        markerPoints.add(loc) // marker
                        stopPoints.add(loc)   // route base
                    }
                }
            }

            recycler.adapter = PartnerEntryAdapter(combined.sortedByDescending { it.second }) { target ->
                if (target.startsWith("entry:")) {
                    val id = target.removePrefix("entry:").toInt()
                    val bundle = android.os.Bundle().apply { putInt("entryId", id) }
                    val navBundle = android.os.Bundle().apply {
                        putAll(bundle)
                    }
                    findNavController().navigate(R.id.feedFragment, navBundle)
                } else if (target.startsWith("trip:")) {
                    val id = target.removePrefix("trip:").toInt()
                    val bundle = android.os.Bundle().apply { putInt("tripId", id) }
                    val navBundle = android.os.Bundle().apply {
                        putAll(bundle)
                    }
                    findNavController().navigate(R.id.feedFragment, navBundle)
                }
            }

            // nicer square gallery
            photoRecycler.adapter = object: RecyclerView.Adapter<PhotoVH>(){
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVH {
                    val iv = android.widget.ImageView(parent.context)
                    val size = parent.resources.displayMetrics.widthPixels / 3
                    val lp = ViewGroup.MarginLayoutParams(size, size)
                    lp.setMargins(6,6,6,6)
                    iv.layoutParams = lp
                    iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    iv.setBackgroundResource(android.R.color.darker_gray)
                    iv.setPadding(2,2,2,2)
                    return PhotoVH(iv)
                }
                override fun getItemCount() = photos.size
                override fun onBindViewHolder(holder: PhotoVH, position: Int) {
                    val path = photos[position]
                    com.bumptech.glide.Glide.with(this@TravelPartnerFragment).load(java.io.File(path)).into(holder.iv)
                    holder.iv.setOnClickListener {
                        val dialog = com.d_drostes_apps.placestracker.ui.feed.MediaDialogFragment.newInstance(arrayListOf(path),0)
                        dialog.show(parentFragmentManager,"photo")
                    }
                }
            }

            setupMap(markerPoints, routes)
        }
    }

    class PhotoVH(val iv: android.widget.ImageView): RecyclerView.ViewHolder(iv)

    private fun setupMap(points: List<String>, routes: List<Pair<String,String>>) {
        // routes provided from trips
        val routesInternal = routes
        map.settings.javaScriptEnabled = true

        // prevent parent scroll while interacting with globe
        map.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        // prevent page scroll when interacting with the globe
        map.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        val html = requireContext().assets.open("cesium_globe.html").bufferedReader().use{it.readText()}
        map.loadDataWithBaseURL("https://localhost/",html,"text/html","UTF-8",null)

        map.webViewClient = object: android.webkit.WebViewClient(){
            override fun onPageFinished(view: WebView?, url: String?) {
                // markers (entries + stops) but NOT used to create routes
                val jsPoints = points.mapNotNull{
                    val p=it.split(",")
                    if(p.size==2) "{lat:${p[0]},lon:${p[1]}}" else null
                }.joinToString(",")

                // routes only from stop‑to‑stop connections
                val jsRoutes = routes.mapNotNull{
                    val a=it.first.split(",")
                    val b=it.second.split(",")
                    if(a.size==2 && b.size==2)
                        "{from:{lat:${a[0]},lon:${a[1]}},to:{lat:${b[0]},lon:${b[1]}}}"
                    else null
                }.joinToString(",")

                // use dedicated friend map renderer so diaries are NOT connected to stops
                map.evaluateJavascript("javascript:if(window.setFriendData) window.setFriendData([$jsPoints],[$jsRoutes]);",null)
            }
        }
    }
}
