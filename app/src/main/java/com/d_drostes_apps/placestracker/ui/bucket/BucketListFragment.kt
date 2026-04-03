package com.d_drostes_apps.placestracker.ui.bucket

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
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
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.BucketItem
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class BucketListFragment : Fragment(R.layout.fragment_bucket_list) {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: BucketPagerAdapter
    
    private var selectedMedia: String? = null
    private var ivPreview: ImageView? = null
    private var allItems: List<BucketItem> = emptyList()

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
                    tabLayout.setSelectedTabIndicatorColor(color)
                }
            }
        }

        viewPager = view.findViewById(R.id.viewPagerBucket)
        tabLayout = view.findViewById(R.id.tabLayoutBucket)

        pagerAdapter = BucketPagerAdapter(
            onToggle = { item ->
                lifecycleScope.launch {
                    bucketDao.updateBucketItem(item.copy(isCompleted = !item.isCompleted))
                }
            },
            onEdit = { item -> showEditItemDialog(item) },
            onDelete = { item -> showDeleteConfirmation(item) }
        )
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.all)
                1 -> getString(R.string.open)
                2 -> getString(R.string.completed)
                else -> ""
            }
        }.attach()

        viewLifecycleOwner.lifecycleScope.launch {
            bucketDao.getAllBucketItems().collectLatest { items ->
                allItems = sortBucketItems(items)
                updatePager()
            }
        }

        view.findViewById<FloatingActionButton>(R.id.fabAddBucketItem).setOnClickListener {
            showAddItemDialog()
        }
    }

    private fun sortBucketItems(items: List<BucketItem>): List<BucketItem> {
        val now = System.currentTimeMillis()
        return items.sortedWith { a, b ->
            val catA = when {
                a.date == null -> 0
                a.date > now -> 1
                else -> 2
            }
            val catB = when {
                b.date == null -> 0
                b.date > now -> 1
                else -> 2
            }

            if (catA != catB) {
                catA.compareTo(catB)
            } else {
                when (catA) {
                    0 -> a.title.compareTo(b.title) // Both null, sort by title
                    1 -> a.date!!.compareTo(b.date!!) // Future: closest first (ascending)
                    2 -> b.date!!.compareTo(a.date!!) // Past: latest first (descending)
                    else -> 0
                }
            }
        }
    }

    private fun updatePager() {
        val active = allItems.filter { !it.isCompleted }
        val completed = allItems.filter { it.isCompleted }
        pagerAdapter.setData(allItems, active, completed)
    }

    private fun showDeleteConfirmation(item: BucketItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_item_title)
            .setMessage(getString(R.string.delete_item_msg, item.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val bucketDao = (requireActivity().application as PlacesApplication).database.bucketDao()
                    bucketDao.deleteBucketItem(item)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddItemDialog(existingItem: BucketItem? = null) {
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

        var selectedDate: Long? = existingItem?.date
        selectedMedia = existingItem?.media?.firstOrNull()

        existingItem?.let {
            inputTitle.setText(it.title)
            inputDesc.setText(it.description)
            cbIsTrip.isChecked = it.isTrip
            if (it.date != null) {
                tvDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it.date))
            }
            if (selectedMedia != null) {
                Glide.with(this).load(File(selectedMedia!!)).centerCrop().into(ivPreview!!)
            }
        }

        btnBack.setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.layoutBucketDate).setOnClickListener {
            val cal = Calendar.getInstance()
            if (selectedDate != null) cal.timeInMillis = selectedDate!!
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
                val item = (existingItem ?: BucketItem(title = "", description = null, date = null)).copy(
                    title = title,
                    description = inputDesc.text.toString(),
                    date = selectedDate,
                    isTrip = cbIsTrip.isChecked,
                    media = if (selectedMedia != null) listOf(selectedMedia!!) else emptyList()
                )
                val dao = (requireActivity().application as PlacesApplication).database.bucketDao()
                if (existingItem != null) dao.updateBucketItem(item) else dao.insertBucketItem(item)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showEditItemDialog(item: BucketItem) {
        showAddItemDialog(item)
    }

    inner class BucketPagerAdapter(
        private val onToggle: (BucketItem) -> Unit,
        private val onEdit: (BucketItem) -> Unit,
        private val onDelete: (BucketItem) -> Unit
    ) : RecyclerView.Adapter<BucketPagerAdapter.PageViewHolder>() {

        private var lists = listOf<List<BucketItem>>(emptyList(), emptyList(), emptyList())

        fun setData(all: List<BucketItem>, active: List<BucketItem>, completed: List<BucketItem>) {
            lists = listOf(all, active, completed)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val rv = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                layoutManager = LinearLayoutManager(context)
                setPadding(16, 16, 16, 200)
                clipToPadding = false
            }
            return PageViewHolder(rv)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(lists[position])
        }

        override fun getItemCount() = 3

        inner class PageViewHolder(private val rv: RecyclerView) : RecyclerView.ViewHolder(rv) {
            private val adapter = BucketAdapter(onToggle, onEdit, onDelete)
            init { rv.adapter = adapter }
            fun bind(data: List<BucketItem>) {
                adapter.submitList(data)
            }
        }
    }
}

class BucketAdapter(
    private val onToggle: (BucketItem) -> Unit,
    private val onEdit: (BucketItem) -> Unit,
    private val onDelete: (BucketItem) -> Unit
) : RecyclerView.Adapter<BucketAdapter.ViewHolder>() {

    private var items = emptyList<BucketItem>()

    fun submitList(newItems: List<BucketItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bucket, parent, false)
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
        private val btnEdit = view.findViewById<ImageButton>(R.id.btnEditBucketItem)

        fun bind(item: BucketItem) {
            tvTitle.text = item.title
            tvType.text = if (item.isTrip) itemView.context.getString(R.string.bucket_type_trip) else itemView.context.getString(R.string.bucket_type_experience)
            
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
            btnEdit.setOnClickListener { onEdit(item) }
            
            itemView.setOnClickListener { onEdit(item) }
        }
    }
}
