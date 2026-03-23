package com.d_drostes_apps.placestracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: Long, // Startdatum
    val notes: String? = null,
    val coverImage: String? = null,
    val isTrackingActive: Boolean = false,
    val friendId: String? = null // Verknüpfung zum Freund (Sender)
)

@Entity(tableName = "trip_stops")
data class TripStop(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tripId: Int,
    val title: String,
    val date: Long,
    val location: String?,
    val media: List<String>,
    val notes: String? = null,
    val coverImage: String? = null,
    val isDraft: Boolean = false
)
