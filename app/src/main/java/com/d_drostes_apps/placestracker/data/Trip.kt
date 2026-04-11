package com.d_drostes_apps.placestracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: Long,
    val notes: String? = null,
    val coverImage: String? = null,
    val isTrackingActive: Boolean = false,
    val friendId: String? = null,
    val isAutoTrip: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val supabaseId: String = UUID.randomUUID().toString(),
    val isPublic: Boolean = false
)

@Serializable
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
    val isDraft: Boolean = false,
    val transportMode: String? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val supabaseId: String = UUID.randomUUID().toString()
)