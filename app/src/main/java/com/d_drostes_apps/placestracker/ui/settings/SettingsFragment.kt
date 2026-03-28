package com.d_drostes_apps.placestracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.net.toUri

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userDao = (requireActivity().application as PlacesApplication).database.userDao()
        val ivProfilePic = view.findViewById<ImageView>(R.id.ivSettingsProfilePic)
        val tvUsername = view.findViewById<TextView>(R.id.tvSettingsUsername)
        val cardProfile = view.findViewById<View>(R.id.cardProfile)
        val cardAppSettings = view.findViewById<View>(R.id.cardAppSettings)
        val cardAutoDetection = view.findViewById<View>(R.id.cardAutoDetection)
        val cardBackup = view.findViewById<View>(R.id.cardBackup)

        lifecycleScope.launch {
            userDao.getUserProfile().collectLatest { profile ->
                profile?.let {
                    tvUsername.text = it.username
                    if (it.profilePicturePath != null) {
                        Glide.with(this@SettingsFragment)
                            .load(File(it.profilePicturePath))
                            .centerCrop()
                            .into(ivProfilePic)
                    } else {
                        ivProfilePic.setImageResource(R.drawable.placeholder)
                    }
                }
            }
        }

        cardProfile.setOnClickListener {
            findNavController().navigate(R.id.profileSettingsFragment)
        }

        cardAppSettings.setOnClickListener {
            findNavController().navigate(R.id.appSettingsFragment)
        }

        cardAutoDetection.setOnClickListener {
            findNavController().navigate(R.id.autoDetectionSettingsFragment)
        }

        cardBackup.setOnClickListener {
            findNavController().navigate(R.id.backupFragment)
        }

        val btnBuyMeACoffee = view.findViewById<ImageView>(R.id.btnBuyMeACoffee)

        btnBuyMeACoffee.setOnClickListener {
            val url = "https://www.buymeacoffee.com/deinname"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }
}
