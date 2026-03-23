package com.d_drostes_apps.placestracker.ui.feed

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
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
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.utils.SharingManager
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EntryDetailFragment : Fragment(R.layout.fragment_entry_detail) {

    private var entry: Entry? = null
    private lateinit var mapboxWebView: WebView
    private lateinit var llFlags: LinearLayout
    private lateinit var cvCountryName: MaterialCardView
    private lateinit var tvCountryNamePopup: TextView
    private var isMapFullscreen = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val entryId = arguments?.getInt("entryId") ?: return
        val app = (requireActivity().application as PlacesApplication)
        val repository = app.repository
        val userDao = app.userDao

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val tvTitle = view.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvDetailDate)
        val tvNotes = view.findViewById<TextView>(R.id.tvDetailNotes)
        val rvMedia = view.findViewById<RecyclerView>(R.id.rvDetailMedia)
        val btnFullscreen = view.findViewById<ImageButton>(R.id.btnFullscreenMap)
        val cardMap = view.findViewById<MaterialCardView>(R.id.cardDetailMap)
        val appBar = view.findViewById<AppBarLayout>(R.id.appBar)
        
        llFlags = view.findViewById(R.id.llDetailFlags)
        cvCountryName = view.findViewById(R.id.cvCountryName)
        tvCountryNamePopup = view.findViewById(R.id.tvCountryNamePopup)
        
        mapboxWebView = view.findViewById(R.id.detailCesiumWebView)
        mapboxWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setupMapboxWebView()

        // Fix scrolling for WebView inside NestedScrollView
        mapboxWebView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        view.findViewById<View>(R.id.detailRootLayout).setOnClickListener {
            cvCountryName.visibility = View.GONE
        }

        btnFullscreen.setOnClickListener {
            toggleFullscreen(cardMap, appBar, btnFullscreen)
        }

        toolbar.inflateMenu(R.menu.menu_entry_detail)
        
        lifecycleScope.launch {
            userDao.getUserProfile().collectLatest { profile ->
                profile?.themeColor?.let { color ->
                    ThemeHelper.applyThemeColor(view, color)
                }
            }
        }

        lifecycleScope.launch {
            val dbEntry = app.database.entryDao().getEntryById(entryId)
            dbEntry?.let { e ->
                entry = e
                tvTitle.text = e.title
                tvNotes.text = e.notes ?: ""
                
                // Hide edit/delete if it's a shared entry
                val isShared = e.friendId != null
                toolbar.menu.findItem(R.id.action_edit)?.isVisible = !isShared
                // toolbar.menu.findItem(R.id.action_delete)?.isVisible = !isShared // Commented out to allow deletion of shared entries
                
                // Add share button to menu
                toolbar.menu.add(0, R.id.action_share, 0, "Teilen").apply {
                    setIcon(R.drawable.ic_share)
                    setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                    isVisible = !isShared // Only share own entries
                }

                val sdf = SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault())
                tvDate.text = sdf.format(Date(e.date))

                rvMedia.layoutManager = GridLayoutManager(requireContext(), 3)
                rvMedia.adapter = DetailMediaAdapter(e.media) { path ->
                    val dialog = MediaDialogFragment().apply {
                        arguments = Bundle().apply {
                            putStringArrayList("mediaPaths", ArrayList(e.media))
                            putInt("initialPosition", e.media.indexOf(path))
                        }
                    }
                    dialog.show(parentFragmentManager, "MediaFullscreen")
                }
                updateGlobePosition()
                loadCountryFlag(e.location)
            }
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmation(entry, repository)
                    true
                }
                R.id.action_edit -> {
                    entry?.let {
                        val bundle = Bundle().apply {
                            putInt("entryId", it.id)
                            putString("title", getString(R.string.edit_entry))
                        }
                        findNavController().navigate(R.id.action_entryDetailFragment_to_newEntryFragment, bundle)
                    }
                    true
                }
                R.id.action_share -> {
                    handleShare()
                    true
                }
                else -> false
            }
        }
    }

    private fun handleShare() {
        val currentEntry = entry ?: return
        val app = (requireActivity().application as PlacesApplication)
        lifecycleScope.launch {
            val profile = app.userDao.getUserProfile().first()
            if (profile != null) {
                val sharingManager = SharingManager(requireContext(), app.database)
                sharingManager.shareEntry(currentEntry, profile)
            } else {
                Toast.makeText(requireContext(), "Bitte erstelle zuerst ein Profil", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFullscreen(cardMap: MaterialCardView, appBar: AppBarLayout, btn: ImageButton) {
        isMapFullscreen = !isMapFullscreen
        val nestedScrollView = view?.findViewById<NestedScrollView>(R.id.nestedScrollView)
        val linearLayout = cardMap.parent as LinearLayout

        if (isMapFullscreen) {
            appBar.visibility = View.GONE
            for (i in 0 until linearLayout.childCount) {
                val child = linearLayout.getChildAt(i)
                if (child != cardMap) child.visibility = View.GONE
            }
            linearLayout.setPadding(0, 0, 0, 0)
            
            nestedScrollView?.isFillViewport = true
            val nvParams = nestedScrollView?.layoutParams as? CoordinatorLayout.LayoutParams
            nvParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            nvParams?.behavior = null 
            nestedScrollView?.layoutParams = nvParams
            
            val llParams = linearLayout.layoutParams
            llParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            linearLayout.layoutParams = llParams
            
            val params = cardMap.layoutParams as LinearLayout.LayoutParams
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.setMargins(12, 12, 12, 12)
            cardMap.layoutParams = params
            btn.setImageResource(R.drawable.ic_fullscreen_exit)
        } else {
            appBar.visibility = View.VISIBLE
            for (i in 0 until linearLayout.childCount) {
                linearLayout.getChildAt(i).visibility = View.VISIBLE
            }
            view?.findViewById<View>(R.id.cvCountryName)?.visibility = View.GONE
            
            val padding = (16 * resources.displayMetrics.density).toInt()
            linearLayout.setPadding(padding, 0, padding, padding)
            
            nestedScrollView?.isFillViewport = false
            val nvParams = nestedScrollView?.layoutParams as? CoordinatorLayout.LayoutParams
            nvParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            nvParams?.behavior = AppBarLayout.ScrollingViewBehavior()
            nestedScrollView?.layoutParams = nvParams
            
            val llParams = linearLayout.layoutParams
            llParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            linearLayout.layoutParams = llParams
            
            val params = cardMap.layoutParams as LinearLayout.LayoutParams
            params.height = (250 * resources.displayMetrics.density).toInt()
            params.setMargins(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            cardMap.layoutParams = params
            btn.setImageResource(R.drawable.ic_fullscreen)
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
                addr.countryCode to addr.countryName
            } else null
        } catch (e: Exception) { null }
    }

    private fun getFlagEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
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
                updateGlobePosition()
            }
        }
        val html = try {
            requireContext().assets.open("mapbox_globe.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }
        mapboxWebView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    private fun updateGlobePosition() {
        lifecycleScope.launch {
            val currentEntry = entry ?: return@launch
            val coords = currentEntry.location?.split(",") ?: return@launch
            if (coords.size == 2) {
                val lat = coords[0].toDouble()
                val lon = coords[1].toDouble()
                val imgPath = currentEntry.coverImage ?: currentEntry.media.firstOrNull()
                val base64 = withContext(Dispatchers.Default) {
                    imgPath?.let { getBase64Thumbnail(it) }
                }
                mapboxWebView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation($lat, $lon, '${base64 ?: ""}');", null)
            }
        }
    }

    private fun getBase64Thumbnail(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
            val resized = Bitmap.createScaledBitmap(bitmap, 120, 120, true)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    private fun showDeleteConfirmation(entry: Entry?, repository: com.d_drostes_apps.placestracker.data.EntryRepository) {
        entry?.let { e ->
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_entry)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.save) { _, _ ->
                    lifecycleScope.launch {
                        repository.delete(e)
                        findNavController().navigateUp()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
