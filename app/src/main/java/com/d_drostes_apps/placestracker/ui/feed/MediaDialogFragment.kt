package com.d_drostes_apps.placestracker.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.d_drostes_apps.placestracker.R

class MediaDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_fullscreen_media, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mediaPaths = arguments?.getStringArrayList("mediaPaths") ?: return
        val initialPosition = arguments?.getInt("initialPosition") ?: 0
        
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        btnClose.setOnClickListener { dismiss() }

        val adapter = FullscreenMediaAdapter(mediaPaths)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(initialPosition, false)
    }
}
