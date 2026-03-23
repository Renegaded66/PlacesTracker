package com.d_drostes_apps.placestracker.ui.bucket

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.BucketItem
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class BucketListFragment : Fragment(R.layout.fragment_bucket_list) {

    private lateinit var adapter: BucketAdapter
    private var selectedMedia: String? = null
    private var ivPreview: ImageView? = null

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = File(requireContext().filesDir, "${UUID.randomUUID()}.jpg")
            requireContext().contentResolver.openInputStream(it)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            selectedMedia = file.absolutePath
            ivPreview?.let { Glide.with(this).load(file).centerCrop().into(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = (requireActivity().application as PlacesApplication)
        val bucketDao = app.database.bucketDao()
        val userDao = app.userDao

        viewLifecycleOwner.lifecycleScope.launch {
            userDao.getUserProfile().collectLatest { profile ->
                profile?.themeColor?.let { color ->
                    ThemeHelper.applyThemeColor(view, color)
                }
            }
        }

        val rvBucket = view.findViewById<RecyclerView>(R.id.rvBucketList)
        rvBucket.layoutManager = LinearLayoutManager(requireContext())
        
        adapter = BucketAdapter(
            onToggle = { item ->
                lifecycleScope.launch {
                    bucketDao.updateBucketItem(item.copy(isCompleted = !item.isCompleted))
                }
            },
            onDelete = { item ->
                showDeleteConfirmation(item)
            }
        )
        rvBucket.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            bucketDao.getAllBucketItems().collectLatest { items ->
                adapter.submitList(items)
            }
        }

        view.findViewById<FloatingActionButton>(R.id.fabAddBucketItem).setOnClickListener {
            showAddItemDialog()
        }
    }

    private fun showDeleteConfirmation(item: BucketItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Element löschen")
            .setMessage("Möchtest du '${item.title}' wirklich von der Bucket List entfernen?")
            .setPositiveButton("Löschen") { _, _ ->
                lifecycleScope.launch {
                    val bucketDao = (requireActivity().application as PlacesApplication).database.bucketDao()
                    bucketDao.deleteBucketItem(item)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showAddItemDialog() {
        val dialog = Dialog(requireContext(), R.style.Theme_PlacesTracker)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_bucket_item)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val inputTitle = dialog.findViewById<TextInputEditText>(R.id.inputBucketTitle)
        val inputDesc = dialog.findViewById<TextInputEditText>(R.id.inputBucketDesc)
        val tvDate = dialog.findViewById<TextView>(R.id.tvBucketDateDisplay)
        val btnAddMedia = dialog.findViewById<MaterialButton>(R.id.btnAddBucketMedia)
        ivPreview = dialog.findViewById<ImageView>(R.id.ivBucketMediaPreview)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnSaveBucketItem)
        val btnBack = dialog.findViewById<View>(R.id.btnBack)
        val cbIsTrip = dialog.findViewById<MaterialCheckBox>(R.id.cbIsTrip)

        var selectedDate: Long? = null
        selectedMedia = null

        btnBack.setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.layoutBucketDate).setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                selectedDate = cal.timeInMillis
                tvDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnAddMedia.setOnClickListener { mediaPicker.launch("image/*") }

        btnSave.setOnClickListener {
            val title = inputTitle.text.toString()
            if (title.isBlank()) return@setOnClickListener

            lifecycleScope.launch {
                val item = BucketItem(
                    title = title,
                    description = inputDesc.text.toString(),
                    date = selectedDate,
                    isTrip = cbIsTrip.isChecked,
                    media = if (selectedMedia != null) listOf(selectedMedia!!) else emptyList()
                )
                (requireActivity().application as PlacesApplication).database.bucketDao().insertBucketItem(item)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}

class BucketAdapter(
    private val onToggle: (BucketItem) -> Unit,
    private val onDelete: (BucketItem) -> Unit
) : RecyclerView.Adapter<BucketAdapter.ViewHolder>() {

    private var items = emptyList<BucketItem>()

    fun submitList(newItems: List<BucketItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = View.inflate(parent.context, R.layout.item_bucket, null)
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivMedia = view.findViewById<ImageView>(R.id.ivBucketMedia)
        private val tvTitle = view.findViewById<TextView>(R.id.tvBucketTitle)
        private val tvDate = view.findViewById<TextView>(R.id.tvBucketDate)
        private val tvType = view.findViewById<TextView>(R.id.tvBucketType)
        private val cbCompleted = view.findViewById<MaterialCheckBox>(R.id.cbCompleted)
        private val overlay = view.findViewById<View>(R.id.completedOverlay)
        private val checkIcon = view.findViewById<ImageView>(R.id.ivCompletedCheck)
        private val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteBucketItem)

        fun bind(item: BucketItem) {
            tvTitle.text = item.title
            tvType.text = if (item.isTrip) "TRIP" else "EXPERIENCE"
            
            if (item.date != null) {
                tvDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(item.date))
                tvDate.visibility = View.VISIBLE
            } else {
                tvDate.visibility = View.GONE
            }

            if (item.media.isNotEmpty()) {
                Glide.with(itemView.context).load(File(item.media[0])).centerCrop().into(ivMedia)
            } else {
                ivMedia.setImageResource(R.drawable.placeholder)
            }

            cbCompleted.isChecked = item.isCompleted
            overlay.visibility = if (item.isCompleted) View.VISIBLE else View.GONE
            checkIcon.visibility = if (item.isCompleted) View.VISIBLE else View.GONE

            cbCompleted.setOnClickListener { onToggle(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            
            itemView.setOnLongClickListener {
                onDelete(item)
                true
            }
        }
    }
}
