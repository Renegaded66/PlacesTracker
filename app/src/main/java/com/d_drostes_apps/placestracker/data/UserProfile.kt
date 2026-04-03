package com.d_drostes_apps.placestracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    var username: String,
    val profilePicturePath: String?,
    val countryCode: String? = null,
    val language: String = "de",
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val isAutoGalleryScanEnabled: Boolean = false,
    val autoGalleryScanDistance: Int = 20, // Distanz in km
    val isTimelineGalleryEnabled: Boolean = false,
    val themeColor: Int = -10044455, // Standard-Lila (Material 3 standard)
    val isSyncEnabled: Boolean = false,
    val supabaseUserId: String? = null,
    val lastSyncTime: Long = 0
)
