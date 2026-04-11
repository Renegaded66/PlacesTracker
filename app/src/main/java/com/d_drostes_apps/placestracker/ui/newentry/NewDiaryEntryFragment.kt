package com.d_drostes_apps.placestracker.ui.newentry

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class NewDiaryEntryFragment : Fragment(R.layout.fragment_new_diary_entry) {

    private var selectedDate: Calendar = Calendar.getInstance()
    private var isTimeSet = false
    private var selectedCoverImage: String? = null
    private val selectedPeople = mutableSetOf<String>()
    private var selectedLocation: Pair<Double, Double>? = null
    private var editingEntryId: Int = -1
    
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var ivCover: ImageView
    private lateinit var etTitle: EditText
    private lateinit var etLocation: EditText
    private lateinit var etNotes: EditText
    private lateinit var etPeople: AutoCompleteTextView
    private lateinit var chipGroupPeople: ChipGroup
    private lateinit var ratingBar: RatingBar
    private lateinit var layoutAddImage: View

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editingEntryId = arguments?.getInt("entryId") ?: -1

        tvDate = view.findViewById(R.id.tvDiaryDate)
        tvTime = view.findViewById(R.id.tvDiaryTime)
        ivCover = view.findViewById(R.id.ivDiaryCover)
        etTitle = view.findViewById(R.id.etDiaryTitle)
        etLocation = view.findViewById(R.id.etDiaryLocation)
        etNotes = view.findViewById(R.id.etDiaryNotes)
        etPeople = view.findViewById(R.id.etDiaryPeople)
        chipGroupPeople = view.findViewById(R.id.chipGroupPeople)
        ratingBar = view.findViewById(R.id.ratingDiary)
        layoutAddImage = view.findViewById(R.id.layoutAddDiaryImage)

        if (editingEntryId != -1) {
            loadEntryData()
        } else {
            updateDateDisplay()
            updateTimeDisplay()
        }
        
        view.findViewById<View>(R.id.cardDiaryDate).setOnClickListener { showDatePicker() }
        view.findViewById<View>(R.id.cardDiaryTime).setOnClickListener { showTimePicker() }
        
        view.findViewById<View>(R.id.cardDiaryImage).setOnClickListener { 
            if (selectedCoverImage == null) {
                imagePicker.launch("image/*")
            } else {
                showImageOptions()
            }
        }
        
        etLocation.setOnClickListener {
            showLocationPicker()
        }

        view.findViewById<View>(R.id.btnSaveDiary).setOnClickListener { saveEntry() }

        setupPeopleAutocomplete()
    }

    private fun loadEntryData() {
        lifecycleScope.launch {
            val app = requireActivity().application as PlacesApplication
            val entry = app.database.entryDao().getEntryById(editingEntryId)
            entry?.let {
                etTitle.setText(it.title)
                etNotes.setText(it.notes)
                ratingBar.rating = it.rating!!
                selectedDate.timeInMillis = it.date
                isTimeSet = true
                updateDateDisplay()
                updateTimeDisplay()

                it.location?.split(",")?.let { coords ->
                    if (coords.size == 2) {
                        val lat = coords[0].toDoubleOrNull()
                        val lon = coords[1].toDoubleOrNull()
                        if (lat != null && lon != null) {
                            updateLocation(lat, lon)
                        } else {
                            etLocation.setText(it.location)
                        }
                    } else {
                        etLocation.setText(it.location)
                    }
                }

                it.coverImage?.let { path ->
                    selectedCoverImage = path
                    Glide.with(this@NewDiaryEntryFragment).load(File(path)).centerCrop().into(ivCover)
                    layoutAddImage.visibility = View.GONE
                }

                it.people.forEach { person ->
                    addPersonChip(person)
                }
                
                view?.findViewById<Button>(R.id.btnSaveDiary)?.text = "Änderungen speichern"
            }
        }
    }

    private fun handleImageSelected(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = copyToInternalStorage(uri)
            selectedCoverImage = file.absolutePath
            
            withContext(Dispatchers.Main) {
                Glide.with(this@NewDiaryEntryFragment).load(file).centerCrop().into(ivCover)
                layoutAddImage.visibility = View.GONE
                tryUseLocationFromImage()
            }
        }
    }

    private fun showImageOptions() {
        val popup = PopupMenu(requireContext(), ivCover)
        popup.menu.add("Neues Bild wählen")
        
        val file = selectedCoverImage?.let { File(it) }
        val hasGps = if (file?.exists() == true) {
            val exif = try { ExifInterface(file.absolutePath) } catch (e: Exception) { null }
            val latLong = FloatArray(2)
            exif?.getLatLong(latLong) ?: false
        } else false

        if (hasGps) {
            popup.menu.add("Standort von dem Bild verwenden")
        }
        
        popup.menu.add("Bild entfernen")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Neues Bild wählen" -> imagePicker.launch("image/*")
                "Standort von dem Bild verwenden" -> tryUseLocationFromImage()
                "Bild entfernen" -> {
                    selectedCoverImage = null
                    ivCover.setImageResource(R.drawable.placeholder)
                    layoutAddImage.visibility = View.VISIBLE
                }
            }
            true
        }
        popup.show()
    }

    private fun tryUseLocationFromImage() {
        val path = selectedCoverImage ?: return
        val file = File(path)
        if (!file.exists()) return

        val exif = try { ExifInterface(file.absolutePath) } catch (e: Exception) { null }
        val latLong = FloatArray(2)
        if (exif?.getLatLong(latLong) == true) {
            updateLocation(latLong[0].toDouble(), latLong[1].toDouble())
            Toast.makeText(requireContext(), "Standort übernommen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocationPicker() {
        val dialog = LocationPickerDialog { lat, lon ->
            updateLocation(lat, lon)
        }
        dialog.show(parentFragmentManager, "LocationPicker")
    }

    private fun updateLocation(lat: Double, lon: Double) {
        selectedLocation = lat to lon
        etLocation.setText(String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon))
    }

    private fun setupPeopleAutocomplete() {
        lifecycleScope.launch {
            val app = requireActivity().application as PlacesApplication
            val allPeopleRaw = app.database.entryDao().getAllPeopleRaw()
            val allPeople = allPeopleRaw.flatMap { it.split(",") }.filter { it.isNotBlank() }.map { it.trim() }.distinct()
            
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allPeople)
            etPeople.setAdapter(adapter)
            etPeople.setOnItemClickListener { parent, _, position, _ ->
                val person = parent.getItemAtPosition(position) as String
                addPersonChip(person)
                etPeople.setText("")
            }

            etPeople.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    val person = etPeople.text.toString().trim()
                    if (person.isNotEmpty()) {
                        addPersonChip(person)
                        etPeople.setText("")
                    }
                    true
                } else false
            }
        }
    }

    private fun addPersonChip(name: String) {
        if (selectedPeople.contains(name)) return
        selectedPeople.add(name)
        
        val chip = Chip(requireContext()).apply {
            text = name
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                selectedPeople.remove(name)
                chipGroupPeople.removeView(this)
            }
        }
        chipGroupPeople.addView(chip)
    }

    private fun showDatePicker() {
        DatePickerDialog(requireContext(), { _, y, m, d ->
            selectedDate.set(Calendar.YEAR, y)
            selectedDate.set(Calendar.MONTH, m)
            selectedDate.set(Calendar.DAY_OF_MONTH, d)
            updateDateDisplay()
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(requireContext(), { _, h, min ->
            selectedDate.set(Calendar.HOUR_OF_DAY, h)
            selectedDate.set(Calendar.MINUTE, min)
            isTimeSet = true
            updateTimeDisplay()
        }, selectedDate.get(Calendar.HOUR_OF_DAY), selectedDate.get(Calendar.MINUTE), true).show()
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault())
        tvDate.text = sdf.format(selectedDate.time)
    }

    private fun updateTimeDisplay() {
        if (isTimeSet) {
            val sdf = SimpleDateFormat("HH:mm 'Uhr'", Locale.getDefault())
            tvTime.text = sdf.format(selectedDate.time)
        } else {
            tvTime.text = "Uhrzeit hinzufügen"
        }
    }

    private fun saveEntry() {
        val title = etTitle.text.toString()
        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Bitte gib einen Titel ein", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isTimeSet) {
            selectedDate.set(Calendar.HOUR_OF_DAY, 0)
            selectedDate.set(Calendar.MINUTE, 0)
        }

        val entry = Entry(
            id = if (editingEntryId != -1) editingEntryId else 0,
            title = title,
            date = selectedDate.timeInMillis,
            notes = etNotes.text.toString().ifBlank { null },
            location = selectedLocation?.let { "${it.first},${it.second}" } ?: etLocation.text.toString().ifBlank { null },
            media = selectedCoverImage?.let { listOf(it) } ?: emptyList(),
            coverImage = selectedCoverImage,
            rating = ratingBar.rating,
            people = selectedPeople.toList(),
            entryType = "diary"
        )

        lifecycleScope.launch {
            (requireActivity().application as PlacesApplication).repository.insert(entry)
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun copyToInternalStorage(uri: Uri): File {
        val fileName = "diary_${UUID.randomUUID()}.jpg"
        val input = requireContext().contentResolver.openInputStream(uri)!!
        val file = File(requireContext().filesDir, fileName)
        FileOutputStream(file).use { output -> input.copyTo(output) }
        return file
    }
}
