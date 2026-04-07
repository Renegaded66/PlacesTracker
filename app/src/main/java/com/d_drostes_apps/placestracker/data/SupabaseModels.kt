package com.d_drostes_apps.placestracker.data

import kotlinx.serialization.Serializable

@Serializable
data class SupabaseUser(
    val id: String,
    val name: String,
    val profile_image_url: String? = null,
    val last_modified: Long,
    val last_lat: Double? = null,
    val last_lon: Double? = null
)

@Serializable
data class SupabaseTrip(
    val id: String,
    val title: String,
    val date: Long,
    val location: String?,
    val user_id: String,
    val last_modified: Long,
    val is_public: Boolean = false
)

@Serializable
data class SupabaseTripStop(
    val id: String,
    val trip_id: String,
    val title: String,
    val description: String?,
    val date: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val last_modified: Long
)

@Serializable
data class SupabaseEntry(
    val id: String,
    val title: String,
    val date: Long,
    val location: String?,
    val user_id: String,
    val last_modified: Long,
    val is_public: Boolean = false,
    val rating: Float = 0f,
    val people: String = "", // Comma separated
    val entry_type: String = "experience"
)

@Serializable
data class SupabaseBucketItem(
    val id: String,
    val title: String,
    val description: String?,
    val date: Long?,
    val is_completed: Boolean,
    val user_id: String,
    val last_modified: Long
)
