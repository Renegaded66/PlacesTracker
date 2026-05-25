package com.d_drostes_apps.placestracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherRepository(private val weatherDao: WeatherDao) {

    suspend fun getWeather(lat: Double, lon: Double, timestamp: Long, apiKey: String): WeatherCache {
        // Try cache first (exact timestamp match)
        val cached = weatherDao.getWeather(lat, lon, timestamp)
        if (cached != null) return cached
        return try {
            val response = WeatherRetrofitClient.apiService.getWeather(
                lat = lat,
                lon = lon,
                apiKey = apiKey
            )

            val condition = response.weather.firstOrNull()

            val weather = WeatherCache(
                lat = lat,
                lon = lon,
                timestamp = timestamp,
                temperature = response.main.temp,
                condition = condition?.main ?: "",
                iconCode = condition?.icon ?: ""
            )

            weatherDao.insertWeather(weather)
            weather
        } catch (e: Exception) {
            // Fallback so UI never crashes if API fails (401, network, etc.)
            WeatherCache(
                lat = lat,
                lon = lon,
                timestamp = timestamp,
                temperature = 0.0,
                condition = "",
                iconCode = ""
            )
        }
    }
}
