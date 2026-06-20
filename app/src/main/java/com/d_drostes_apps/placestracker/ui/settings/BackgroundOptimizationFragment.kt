package com.d_drostes_apps.placestracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.d_drostes_apps.placestracker.R

class BackgroundOptimizationFragment : Fragment(R.layout.fragment_background_optimization) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val statusText = view.findViewById<TextView>(R.id.tvBatteryStatus)
        val statusBox = view.findViewById<View>(R.id.boxBatteryStatus)
        val btnOpenSettings = view.findViewById<Button>(R.id.btnOpenBatterySettings)

        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val pm = requireContext().getSystemService(PowerManager::class.java)
        val ignoring = pm.isIgnoringBatteryOptimizations(requireContext().packageName)

        if (ignoring) {
            statusText.text = getString(R.string.battery_optimization_disabled)
            statusBox.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
        } else {
            statusText.text = getString(R.string.battery_optimization_enabled)
            statusBox.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
        }

        btnOpenSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
        }
    }
}
