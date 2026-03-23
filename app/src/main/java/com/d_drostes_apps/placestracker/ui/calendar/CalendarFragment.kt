package com.d_drostes_apps.placestracker.ui.calendar

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.data.TripStop
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvMonthYear: TextView
    private var currentCalendar: Calendar = Calendar.getInstance()
    
    private var allEntries: List<Entry> = emptyList()
    private var allTripStops: List<TripStop> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = (requireActivity().application as PlacesApplication)
        val entryRepository = app.repository
        val tripDao = app.database.tripDao()

        recyclerView = view.findViewById(R.id.calendarRecyclerView)
        tvMonthYear = view.findViewById(R.id.tvMonthYear)
        val btnPrev = view.findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNext = view.findViewById<ImageButton>(R.id.btnNextMonth)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 7)

        lifecycleScope.launch {
            combine(entryRepository.allEntries, tripDao.getAllTripStops()) { entries, stops ->
                allEntries = entries
                allTripStops = stops
                updateCalendar()
            }.collectLatest { }
        }

        btnPrev.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        btnNext.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        tvMonthYear.setOnClickListener { showMonthYearPicker() }
    }

    private fun showMonthYearPicker() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_month_year_picker, null)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.monthPicker)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)

        monthPicker.minValue = 0
        monthPicker.maxValue = 11
        monthPicker.value = currentCalendar.get(Calendar.MONTH)
        monthPicker.displayedValues = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        yearPicker.minValue = 1900
        yearPicker.maxValue = 2100
        yearPicker.value = currentCalendar.get(Calendar.YEAR)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.date_hint))
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                currentCalendar.set(Calendar.MONTH, monthPicker.value)
                currentCalendar.set(Calendar.YEAR, yearPicker.value)
                updateCalendar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateCalendar() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvMonthYear.text = sdf.format(currentCalendar.time)

        val days = mutableListOf<CalendarDay>()
        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)

        var firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        if (firstDayOfWeek <= 0) firstDayOfWeek = 7 
        
        cal.add(Calendar.DAY_OF_MONTH, -(firstDayOfWeek - 1))

        for (i in 0 until 42) {
            val date = cal.time
            val isCurrentMonth = cal.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH)
            
            val dayItems = mutableListOf<CalendarItem>()
            
            allEntries.filter { isSameDay(it.date, cal) }.forEach { dayItems.add(CalendarItem.Experience(it)) }
            allTripStops.filter { isSameDay(it.date, cal) }.forEach { dayItems.add(CalendarItem.Stop(it)) }

            days.add(CalendarDay(date, isCurrentMonth, dayItems))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        recyclerView.adapter = CalendarAdapter(days) { day -> showDayEntriesDialog(day) }
    }

    private fun isSameDay(timeMillis: Long, cal: Calendar): Boolean {
        val target = Calendar.getInstance()
        target.timeInMillis = timeMillis
        return target.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
               target.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun showDayEntriesDialog(day: CalendarDay) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.dialog_day_entries)
        
        val tvDate = dialog.findViewById<TextView>(R.id.tvDialogDate)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnDialogClose)
        val tvNoEntries = dialog.findViewById<TextView>(R.id.tvNoEntries)
        val rvEntries = dialog.findViewById<RecyclerView>(R.id.rvDialogEntries)

        tvDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(day.date)
        btnClose.setOnClickListener { dialog.dismiss() }

        if (day.items.isEmpty()) {
            tvNoEntries.visibility = View.VISIBLE
            rvEntries.visibility = View.GONE
        } else {
            tvNoEntries.visibility = View.GONE
            rvEntries.visibility = View.VISIBLE
            rvEntries.layoutManager = LinearLayoutManager(requireContext())
            
            // Konvertiere CalendarItems in Entry-Objekte unter Beibehaltung des Cover-Images
            val entries = day.items.map { item ->
                when(item) {
                    is CalendarItem.Experience -> item.entry
                    is CalendarItem.Stop -> Entry(
                        id = item.stop.id, 
                        title = item.stop.title, 
                        date = item.stop.date, 
                        notes = item.stop.notes, 
                        location = item.stop.location, 
                        media = item.stop.media,
                        coverImage = item.stop.coverImage // Wichtig für das Vorschaubild im Pop-up
                    )
                }
            }

            rvEntries.adapter = DayEntriesAdapter(entries) { entry ->
                dialog.dismiss()
                val isStop = day.items.any { it is CalendarItem.Stop && it.id == entry.id && it.title == entry.title }
                
                val bundle = Bundle().apply {
                    putInt(if (isStop) "stopId" else "entryId", entry.id)
                }
                
                requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = R.id.feedFragment
                
                val destination = if (isStop) R.id.tripStopDetailFragment else R.id.entryDetailFragment
                findNavController().navigate(destination, bundle)
            }
        }
        dialog.show()
    }
}
