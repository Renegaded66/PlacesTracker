package com.d_drostes_apps.placestracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE friendId IS NULL ORDER BY date DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE friendId = :friendId ORDER BY date DESC")
    fun getTripsByFriend(friendId: String): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: Int): Trip?

    @Query("SELECT * FROM trips WHERE friendId = :friendId AND title = :title LIMIT 1")
    suspend fun getTripByFriendAndTitle(friendId: String, title: String): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)

    @Query("UPDATE trips SET isTrackingActive = :isActive WHERE id = :tripId")
    suspend fun updateTrackingStatus(tripId: Int, isActive: Boolean)

    @Query("SELECT * FROM trips WHERE isTrackingActive = 1 LIMIT 1")
    suspend fun getActiveTrackingTrip(): Trip?

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY date ASC")
    fun getStopsForTrip(tripId: Int): Flow<List<TripStop>>
    
    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY date ASC")
    suspend fun getStopsForTripSync(tripId: Int): List<TripStop>

    @Query("SELECT * FROM trip_stops ORDER BY date ASC")
    fun getAllTripStops(): Flow<List<TripStop>>

    @Query("SELECT * FROM trip_stops WHERE id = :id")
    suspend fun getStopById(id: Int): TripStop?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStop(stop: TripStop)

    @Delete
    suspend fun deleteStop(stop: TripStop)
    
    @Query("DELETE FROM trip_stops WHERE tripId = :tripId")
    suspend fun deleteStopsForTrip(tripId: Int)

    // TripLocation methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: TripLocation)

    @Delete
    suspend fun deleteLocation(location: TripLocation)

    @Query("DELETE FROM trip_locations WHERE id = :id")
    suspend fun deleteLocationById(id: Long)

    @Query("SELECT * FROM trip_locations WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getLocationsForTrip(tripId: Int): Flow<List<TripLocation>>

    @Query("SELECT * FROM trip_locations WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getLocationsForTripSync(tripId: Int): List<TripLocation>

    @Query("SELECT * FROM trip_locations")
    fun getAllTripLocations(): Flow<List<TripLocation>>

    @Query("SELECT * FROM trip_locations WHERE tripId = :tripId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLocationForTrip(tripId: Int): TripLocation?

    @Query("DELETE FROM trip_locations WHERE tripId = :tripId")
    suspend fun deleteLocationsForTrip(tripId: Int)
}
