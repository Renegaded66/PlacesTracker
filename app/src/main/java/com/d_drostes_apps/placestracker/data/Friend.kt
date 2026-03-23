package com.d_drostes_apps.placestracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val id: String, // Eindeutige ID (z.B. Kombination aus Username und Initial-Timestamp oder UUID)
    val username: String,
    val profilePicturePath: String?,
    val countryCode: String?
)
