package com.d_drostes_apps.placestracker.ui.feed

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Bundle
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
import com.d_drostes_apps.placestracker.utils.GlobeUtils
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
        val cvNotes = view.findViewById<MaterialCardView>(R.id.cvDetailNotes)
        val rvMedia = view.findViewById<RecyclerView>(R.id.rvDetailMedia)
        val btnFullscreen = view.findViewById<ImageButton>(R.id.btnFullscreenMap)
        val cardMap = view.findViewById<MaterialCardView>(R.id.cardDetailMap)
        val appBar = view.findViewById<AppBarLayout>(R.id.appBar)
        
        llFlags = view.findViewById(R.id.llDetailFlags)
        cvCountryName = view.findViewById(R.id.cvCountryName)
        tvCountryNamePopup = view.findViewById(R.id.tvCountryNamePopup)
        
        mapboxWebView = view.findViewById(R.id.detailCesiumWebView)
        
        val isInline = parentFragment is FeedFragment
        if (isInline) {
            cardMap.visibility = View.GONE
            view.findViewById<View>(R.id.drag_handle)?.visibility = View.GONE
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener {
                (parentFragment as? FeedFragment)?.handleBack()
            }
            // Increase top padding to account for missing appBar space if needed
            view.findViewById<View>(R.id.llDetailContent)?.setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                0,
                (16 * resources.displayMetrics.density).toInt(),
                (100 * resources.displayMetrics.density).toInt()
            )
        } else {
            mapboxWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setupMapboxWebView()
            toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        }

        // Animation für den Content beim Laden
        view.findViewById<View>(R.id.llDetailContent)?.apply {
            alpha = 0f
            translationY = 50f
            animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100).start()
        }

        // Fix scrolling for WebView inside NestedScrollView
        mapboxWebView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

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
                
                if (!e.notes.isNullOrBlank()) {
                    tvNotes.text = e.notes
                    cvNotes.visibility = View.VISIBLE
                } else {
                    cvNotes.visibility = View.GONE
                }
                
                // Hide edit if it's a shared entry, show add button instead
                val isShared = e.friendId != null
                toolbar.menu.findItem(R.id.action_edit)?.isVisible = !isShared
                toolbar.menu.findItem(R.id.action_add_shared)?.isVisible = isShared
                
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
                if (!isInline) updateGlobePosition()
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
                        // 🌟 FIX: Prüfen, ob es ein Tagebuch ist, und das richtige Fragment öffnen!
                        if (it.entryType == "diary") {
                            findNavController().navigate(R.id.newDiaryEntryFragment, bundle)
                        } else {
                            findNavController().navigate(R.id.newEntryFragment, bundle)
                        }
                    }
                    true
                }
                R.id.action_share -> {
                    handleShare()
                    true
                }
                R.id.action_add_shared -> {
                    showAddSharedConfirmation()
                    true
                }
                else -> false
            }
        }
    }

    private fun showAddSharedConfirmation() {
        val currentEntry = entry ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_my_feed)
            .setMessage(R.string.add_to_my_feed_confirm_entry)
            .setPositiveButton(R.string.save) { _, _ ->
                lifecycleScope.launch {
                    val app = (requireActivity().application as PlacesApplication)
                    val newEntry = currentEntry.copy(id = 0, friendId = null)
                    app.database.entryDao().insert(newEntry)
                    Toast.makeText(requireContext(), R.string.added_to_my_feed_success, Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        mapboxWebView.addJavascriptInterface(JsInterface(), "Android")
        
        mapboxWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                updateGlobePosition()
            }
        }
        val html = try {
            requireContext().assets.open("cesium_globe.html").bufferedReader().use { it.readText() }
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
                    GlobeUtils.getBase64Thumbnail(imgPath)
                }
                mapboxWebView.evaluateJavascript("javascript:if(window.setLocation) window.setLocation($lat, $lon, '${base64 ?: ""}');", null)
            }
        }
    }

    private fun showDeleteConfirmation(entry: Entry?, repository: com.d_drostes_apps.placestracker.data.EntryRepository) {
        entry?.let { e ->
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_entry)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.save) { _, _ ->
                    lifecycleScope.launch {
                        repository.delete(e)
                        if (parentFragment is FeedFragment) {
                            (parentFragment as FeedFragment).handleBack()
                        } else {
                            findNavController().navigateUp()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}