package com.d_drostes_apps.placestracker.data

sealed class FeedItem(val id: Int, val title: String, val date: Long, val coverImage: String?, val mediaCount: Int) {
    class Experience(val entry: Entry) : FeedItem(
        entry.id, 
        entry.title, 
        entry.date, 
        entry.coverImage ?: entry.media.firstOrNull(),
        entry.media.size
    )
    
    class TripItem(val trip: Trip, val stops: List<TripStop>) : FeedItem(
        trip.id, 
        trip.title, 
        trip.date, 
        trip.coverImage ?: stops.firstOrNull()?.media?.firstOrNull(),
        stops.sumOf { it.media.size }
    )
}
