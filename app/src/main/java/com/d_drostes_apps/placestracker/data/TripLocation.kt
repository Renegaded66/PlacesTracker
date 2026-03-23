package com.d_drostes_apps.placestracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_locations",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class TripLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isConvertedToStop: Boolean = false
)
