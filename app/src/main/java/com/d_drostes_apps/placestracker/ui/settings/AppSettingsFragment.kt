package com.d_drostes_apps.placestracker.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.UserProfile
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    private lateinit var languageSelector: AutoCompleteTextView
    private lateinit var viewCurrentColor: View
    private lateinit var switchTimelineGallery: SwitchMaterial
    private lateinit var switchSync: SwitchMaterial
    private lateinit var layoutSyncUsername: TextInputLayout
    private lateinit var etSyncUsername: TextInputEditText
    
    private var selectedLanguageCode: String = "de"
    private var selectedColor: Int = -10044455 // Default

    private val languages by lazy {
        listOf(
            LanguageItem("de", getString(R.string.german)),
            LanguageItem("en", getString(R.string.english))
        )
    }

    private val presetColors = listOf(
        Color.parseColor("#6750A4"), // Purple (Default)
        Color.parseColor("#B3261E"), // Red
        Color.parseColor("#2196F3"), // Blue
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#FF9800"), // Orange
        Color.parseColor("#E91E63"), // Pink
        Color.parseColor("#00BCD4"), // Cyan
        Color.parseColor("#607D8B")  // Blue Grey
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = (requireActivity().application as PlacesApplication)
        val userDao = app.userDao

        languageSelector = view.findViewById(R.id.languageSelector)
        viewCurrentColor = view.findViewById(R.id.viewCurrentColor)
        switchTimelineGallery = view.findViewById(R.id.switchTimelineGallery)
        switchSync = view.findViewById(R.id.switchSync)
        layoutSyncUsername = view.findViewById(R.id.layoutSyncUsername)
        etSyncUsername = view.findViewById(R.id.etSyncUsername)
        
        val btnPickColor = view.findViewById<LinearLayout>(R.id.btnPickColor)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveAppSettings)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, languages.map { it.name })
        languageSelector.setAdapter(adapter)

        lifecycleScope.launch {
            val profile = userDao.getUserProfile().firstOrNull()
            profile?.let {
                selectedLanguageCode = it.language
                selectedColor = it.themeColor
                switchTimelineGallery.isChecked = it.isTimelineGalleryEnabled
                switchSync.isChecked = false // Force off
                etSyncUsername.setText(it.username)
                layoutSyncUsername.visibility = View.GONE // Force hidden
                
                val lang = languages.find { l -> l.code == it.language }
                languageSelector.setText(lang?.name ?: getString(R.string.german), false)
                viewCurrentColor.backgroundTintList = ColorStateList.valueOf(selectedColor)
                ThemeHelper.applyThemeColor(view, selectedColor)
            }
        }

        switchSync.setOnCheckedChangeListener { _, isChecked ->
            layoutSyncUsername.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        languageSelector.setOnItemClickListener { _, _, position, _ ->
            selectedLanguageCode = languages[position].code
        }

        btnPickColor.setOnClickListener {
            showColorPickerDialog()
        }

        btnSave.setOnClickListener {
            val newUsername = etSyncUsername.text.toString().trim()
            // Sync logic disabled

            lifecycleScope.launch {
                val currentProfile = userDao.getUserProfile().firstOrNull()
                val updatedProfile = if (currentProfile != null) {
                    currentProfile.copy(
                        username = if (newUsername.isNotEmpty()) newUsername else currentProfile.username,
                        language = selectedLanguageCode, 
                        themeColor = selectedColor,
                        isTimelineGalleryEnabled = switchTimelineGallery.isChecked,
                        isSyncEnabled = false // Always false
                    )
                } else {
                    UserProfile(
                        username = if (newUsername.isNotEmpty()) newUsername else getString(R.string.default_username), 
                        profilePicturePath = null, 
                        language = selectedLanguageCode, 
                        themeColor = selectedColor,
                        isTimelineGalleryEnabled = switchTimelineGallery.isChecked,
                        isSyncEnabled = false // Always false
                    )
                }
                
                userDao.insertOrUpdate(updatedProfile)
                
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(selectedLanguageCode)
                AppCompatDelegate.setApplicationLocales(appLocale)
                
                Toast.makeText(requireContext(), R.string.save, Toast.LENGTH_SHORT).show()
                ThemeHelper.applyThemeColor(requireActivity().window.decorView, selectedColor) 
                findNavController().navigateUp()
            }
        }
    }

    private fun showColorPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_color_picker, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.colorContainer)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.pick_color_title)
            .setView(dialogView)
            .create()

        presetColors.forEach { color ->
            val colorView = LayoutInflater.from(requireContext()).inflate(R.layout.item_color_pick, container, false)
            colorView.backgroundTintList = ColorStateList.valueOf(color)
            colorView.setOnClickListener {
                selectedColor = color
                viewCurrentColor.backgroundTintList = ColorStateList.valueOf(color)
                ThemeHelper.applyThemeColor(requireActivity().window.decorView, selectedColor) // Apply immediately
                dialog.dismiss()
            }
            container.addView(colorView)
        }

        dialog.show()
    }

    data class LanguageItem(val code: String, val name: String)
}
