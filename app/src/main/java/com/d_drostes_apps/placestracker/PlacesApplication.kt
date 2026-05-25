package com.d_drostes_apps.placestracker

import android.app.Application
import com.d_drostes_apps.placestracker.data.AppDatabase
import com.d_drostes_apps.placestracker.data.EntryRepository
import com.d_drostes_apps.placestracker.data.TripRepository
import com.d_drostes_apps.placestracker.data.UserDao
import com.d_drostes_apps.placestracker.data.WeatherRepository

class PlacesApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { EntryRepository(database.entryDao(), this) }
    val tripRepository by lazy { TripRepository(database.tripDao(), this) }
    val userDao: UserDao by lazy { database.userDao() }
    val weatherRepository by lazy { WeatherRepository(database.weatherDao()) }
}
