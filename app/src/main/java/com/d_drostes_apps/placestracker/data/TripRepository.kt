package com.d_drostes_apps.placestracker.data

import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripDao: TripDao) {
    val allTrips: Flow<List<Trip>> = tripDao.getAllTrips()
    val allTripStops: Flow<List<TripStop>> = tripDao.getAllTripStops()

    suspend fun insertTrip(trip: Trip): Long {
        return tripDao.insertTrip(trip)
    }

    suspend fun insertStop(stop: TripStop) {
        tripDao.insertStop(stop)
    }

    suspend fun deleteTrip(trip: Trip) {
        tripDao.deleteStopsForTrip(trip.id)
        tripDao.deleteTrip(trip)
    }

    suspend fun getTripById(id: Int): Trip? {
        return tripDao.getTripById(id)
    }

    fun getStopsForTrip(tripId: Int): Flow<List<TripStop>> {
        return tripDao.getStopsForTrip(tripId)
    }
}
