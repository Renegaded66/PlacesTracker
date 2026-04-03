package com.d_drostes_apps.placestracker.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.UserProfile
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ProfileSettingsFragment : Fragment(R.layout.fragment_profile_settings) {

    private var selectedProfilePicPath: String? = null
    private var selectedCountryCode: String? = null
    private lateinit var ivProfilePic: ImageView
    private lateinit var inputUsername: TextInputEditText
    private lateinit var countrySelector: AutoCompleteTextView

    private val countries = Locale.getISOCountries().map { code ->
        val locale = Locale("", code)
        CountryItem(code, locale.displayCountry, getFlagEmoji(code))
    }.sortedBy { it.name }

    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = copyToInternalStorage(it)
            selectedProfilePicPath = file.absolutePath
            Glide.with(this).load(file).centerCrop().into(ivProfilePic)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userDao = (requireActivity().application as PlacesApplication).database.userDao()
        ivProfilePic = view.findViewById(R.id.ivProfilePic)
        inputUsername = view.findViewById(R.id.inputUsername)
        countrySelector = view.findViewById(R.id.countrySelector)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveProfile)
        val btnSelectPic = view.findViewById<View>(R.id.btnSelectProfilePic)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, countries.map { "${it.flag} ${it.name}" })
        countrySelector.setAdapter(adapter)
        countrySelector.setOnItemClickListener { _, _, position, _ ->
            selectedCountryCode = countries[position].code
        }

        lifecycleScope.launch {
            userDao.getUserProfile().collect { profile ->
                profile?.let {
                    inputUsername.setText(it.username)
                    selectedProfilePicPath = it.profilePicturePath
                    selectedCountryCode = it.countryCode
                    
                    if (it.profilePicturePath != null) {
                        Glide.with(this@ProfileSettingsFragment)
                            .load(File(it.profilePicturePath))
                            .centerCrop()
                            .into(ivProfilePic)
                    }

                    selectedCountryCode?.let { code ->
                        val country = countries.find { it.code == code }
                        country?.let { c ->
                            countrySelector.setText("${c.flag} ${c.name}", false)
                        }
                    }
                }
            }
        }

        btnSelectPic.setOnClickListener {
            photoPicker.launch("image/*")
        }

        btnSave.setOnClickListener {
            val name = inputUsername.text.toString()
            if (name.isBlank()) {
                Toast.makeText(requireContext(), R.string.enter_name_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                userDao.insertOrUpdate(UserProfile(
                    username = name, 
                    profilePicturePath = selectedProfilePicPath,
                    countryCode = selectedCountryCode
                ))
                Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun getFlagEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    private fun copyToInternalStorage(uri: Uri): File {
        val input = requireContext().contentResolver.openInputStream(uri)!!
        val file = File(requireContext().filesDir, "profile_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { output -> input.copyTo(output) }
        return file
    }

    data class CountryItem(val code: String, val name: String, val flag: String)
}
