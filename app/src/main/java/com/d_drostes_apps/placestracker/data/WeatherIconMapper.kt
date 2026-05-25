package com.d_drostes_apps.placestracker.data

import android.content.Context
import androidx.annotation.DrawableRes
import com.d_drostes_apps.placestracker.R

object WeatherIconMapper {
    /**
     * Maps OpenWeather icon code to a local drawable resource.
     * This uses a simple mapping. Ensure the corresponding drawables exist in res/drawable.
     */
    @DrawableRes
    fun getIconResId(iconCode: String): Int {
        return when (iconCode) {
            // Clear sky
            "01d", "01n" -> R.drawable.ic_weather_sunny
            // Few clouds
            "02d", "02n" -> R.drawable.ic_weather_partly_cloudy
            // Scattered clouds
            "03d", "03n" -> R.drawable.ic_weather_sunny //ic_weather_cloudy
            // Broken clouds
            "04d", "04n" -> R.drawable.ic_weather_sunny //ic_weather_overcast
            // Shower rain
            "09d", "09n" -> R.drawable.ic_weather_sunny //ic_weather_rain
            // Rain
            "10d", "10n" -> R.drawable.ic_weather_sunny //ic_weather_rain
            // Thunderstorm
            "11d", "11n" -> R.drawable.ic_weather_sunny //ic_weather_thunder
            // Snow
            "13d", "13n" -> R.drawable.ic_weather_sunny //ic_weather_snow
            // Mist
            "50d", "50n" -> R.drawable.ic_weather_sunny //ic_weather_fog
            else -> R.drawable.ic_weather_sunny //ic_weather_unknown
        }
    }
}
