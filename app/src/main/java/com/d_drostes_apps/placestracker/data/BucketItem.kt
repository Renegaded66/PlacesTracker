package com.d_drostes_apps.placestracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bucket_items")
data class BucketItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String?,
    val date: Long?,
    val isCompleted: Boolean = false,
    val media: List<String> = emptyList(),
    val isTrip: Boolean = false // false = Experience, true = Trip
)
