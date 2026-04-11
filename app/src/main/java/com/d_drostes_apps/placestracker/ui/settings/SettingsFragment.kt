package com.d_drostes_apps.placestracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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
        val cardPcEdit = view.findViewById<View>(R.id.cardPcEdit)
        val cardAppSettings = view.findViewById<View>(R.id.cardAppSettings)
        val cardAutoDetection = view.findViewById<View>(R.id.cardAutoDetection)
        val cardBackup = view.findViewById<View>(R.id.cardBackup)
        val cardVisions = view.findViewById<View>(R.id.cardVisions)
        val btnHelp = view.findViewById<View>(R.id.btnHelp)

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

        cardPcEdit.setOnClickListener {
            findNavController().navigate(R.id.pcEditFragment)
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

        cardVisions.setOnClickListener {
            findNavController().navigate(R.id.visionsFragment)
        }

        btnHelp.setOnClickListener {
            showHelpDialog()
        }

        val btnSupport = view.findViewById<Button>(R.id.btnSupport)
        btnSupport.setOnClickListener {
            openDonateUrl()
        }
    }

    private fun showHelpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help, null)
        val viewPager = dialogView.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.helpViewPager)
        val tabLayout = dialogView.findViewById<com.google.android.material.tabs.TabLayout>(R.id.helpTabLayout)
        val btnClose = dialogView.findViewById<View>(R.id.btnHelpClose)

        val helpPages = listOf(
            HelpPage(R.string.help_local_title, R.string.help_local_desc),
            HelpPage(R.string.help_types_title, R.string.help_types_desc),
            HelpPage(R.string.help_auto_trip_title, R.string.help_auto_trip_desc),
            HelpPage(R.string.help_friends_title, R.string.help_friends_desc),
            HelpPage(R.string.help_calendar_title, R.string.help_calendar_desc),
            HelpPage(R.string.help_stats_title, R.string.help_stats_desc),
            HelpPage(R.string.help_bucket_title, R.string.help_bucket_desc),
            HelpPage(R.string.help_auto_detect_title, R.string.help_auto_detect_desc),
            HelpPage(R.string.help_backup_title, R.string.help_backup_desc),
            HelpPage(R.string.help_personalize_title, R.string.help_personalize_desc),
            HelpPage(R.string.help_donate_title, R.string.help_donate_desc, showDonateButton = true, onDonateClick = { openDonateUrl() })
        )

        viewPager.adapter = HelpAdapter(helpPages)

        // ==========================================================
        // 🌟 NEU: Klarer Abstand zwischen den Seiten im ViewPager!
        // ==========================================================
        viewPager.setPageTransformer(androidx.viewpager2.widget.MarginPageTransformer(64))

        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Damit die runden Ecken unserer CardView sichtbar werden
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openDonateUrl() {
        val url = "https://ko-fi.com/ddroste"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }
}
