package com.d_drostes_apps.placestracker.ui.calendar

import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.data.TripStop
import java.util.Date

sealed class CalendarItem {
    data class Experience(val entry: Entry) : CalendarItem()
    data class Stop(val stop: TripStop) : CalendarItem()
    
    val title: String get() = when(this) {
        is Experience -> entry.title
        is Stop -> stop.title
    }
    
    val media: List<String> get() = when(this) {
        is Experience -> entry.media
        is Stop -> stop.media
    }

    val coverImage: String? get() = when(this) {
        is Experience -> entry.coverImage
        is Stop -> stop.coverImage
    }

    val id: Int get() = when(this) {
        is Experience -> entry.id
        is Stop -> stop.id
    }
}

data class CalendarDay(
    val date: Date,
    val isCurrentMonth: Boolean,
    val items: List<CalendarItem> = emptyList()
)
