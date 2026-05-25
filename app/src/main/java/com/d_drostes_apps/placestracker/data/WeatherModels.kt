package com.d_drostes_apps.placestracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class WeatherResponse(
    val main: MainWeather,
    val weather: List<WeatherCondition>
)

data class MainWeather(
    val temp: Double
)

@Serializable
data class WeatherCondition(
    @SerialName("id") val id: Int,
    @SerialName("main") val main: String,
    @SerialName("description") val description: String,
    @SerialName("icon") val icon: String
)
