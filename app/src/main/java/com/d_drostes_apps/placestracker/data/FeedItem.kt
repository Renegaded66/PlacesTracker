package com.d_drostes_apps.placestracker.data

sealed class FeedItem(val id: Int, val title: String, val date: Long, val coverImage: String?, val mediaCount: Int, val supabaseId: String = "", val isPublic: Boolean = false) {
    
    abstract val sortDate: Long
    abstract val isLive: Boolean

    class Experience(val entry: Entry) : FeedItem(
        entry.id, 
        entry.title, 
        entry.date, 
        entry.coverImage ?: entry.media.firstOrNull(),
        entry.media.size,
        entry.supabaseId,
        entry.isPublic
    ) {
        override val sortDate: Long = entry.date
        override val isLive: Boolean = false
    }
    
    class TripItem(val trip: Trip, val stops: List<TripStop>) : FeedItem(
        trip.id, 
        trip.title, 
        trip.date, 
        trip.coverImage ?: stops.firstOrNull()?.media?.firstOrNull(),
        stops.sumOf { it.media.size },
        trip.supabaseId,
        trip.isPublic
    ) {
        override val sortDate: Long = stops.maxOfOrNull { it.date } ?: trip.date
        override val isLive: Boolean = trip.isTrackingActive
    }
}
