package com.d_drostes_apps.placestracker.data

import com.google.gson.annotations.SerializedName

data class SharedData(
    @SerializedName("sender_username") val senderUsername: String,
    @SerializedName("sender_profile_picture") val senderProfilePicture: String?,
    @SerializedName("sender_country_code") val senderCountryCode: String?,
    @SerializedName("type") val type: String, // "ENTRY" or "TRIP"
    @SerializedName("entry") val entry: Entry? = null,
    @SerializedName("trip") val trip: Trip? = null,
    @SerializedName("trip_stops") val tripStops: List<TripStop>? = null,
    @SerializedName("trip_locations") val tripLocations: List<TripLocation>? = null
)
