package com.d_drostes_apps.placestracker.data

import android.content.Context
import com.d_drostes_apps.placestracker.worker.SyncWorker
import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripDao: TripDao, private val context: Context) {
    val allTrips: Flow<List<Trip>> = tripDao.getAllTrips()
    val allTripStops: Flow<List<TripStop>> = tripDao.getAllTripStops()

    suspend fun insertTrip(trip: Trip): Long {
        val id = tripDao.insertTrip(trip)
        SyncWorker.startImmediateSync(context)
        return id
    }

    suspend fun updateTrip(trip: Trip) {
        tripDao.updateTrip(trip)
        SyncWorker.startImmediateSync(context)
    }

    suspend fun insertStop(stop: TripStop) {
        tripDao.insertStop(stop)
        SyncWorker.startImmediateSync(context)
    }

    suspend fun deleteTrip(trip: Trip) {
        tripDao.deleteStopsForTrip(trip.id)
        tripDao.deleteTrip(trip)
        SyncWorker.startImmediateSync(context)
    }

    suspend fun getTripById(id: Int): Trip? {
        return tripDao.getTripById(id)
    }

    fun getStopsForTrip(tripId: Int): Flow<List<TripStop>> {
        return tripDao.getStopsForTrip(tripId)
    }
}
