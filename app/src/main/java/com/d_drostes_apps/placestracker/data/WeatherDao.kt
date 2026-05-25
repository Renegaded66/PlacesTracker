package com.d_drostes_apps.placestracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE lat = :lat AND lon = :lon AND timestamp = :timestamp LIMIT 1")
    suspend fun getWeather(lat: Double, lon: Double, timestamp: Long): WeatherCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: WeatherCache)
}
