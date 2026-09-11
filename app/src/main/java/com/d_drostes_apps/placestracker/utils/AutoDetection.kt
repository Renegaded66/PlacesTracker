package com.d_drostes_apps.placestracker.utils

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure, Android-free clustering helpers for the automatic gallery detection
 * (GalleryScanWorker). Keeping this logic free of android.* types makes it
 * unit-testable on the JVM.
 *
 * The core rule: an auto-detected experience belongs to exactly ONE calendar
 * day. Photos taken on the same day are merged into a single suggestion,
 * regardless of how far apart they were taken.
 */
data class PhotoSample(
    val uri: String,
    val takenAt: Long,
    val lat: Double,
    val lon: Double
)

data class PhotoCluster(
    val uris: MutableList<String>,
    val lat: Double,
    val lon: Double,
    val timestamp: Long
)

object AutoDetection {

    /** Radius within which same-day photos still count as the same stop during a live trip. */
    const val STOP_CLUSTER_RADIUS_METERS = 500.0

    /**
     * Start of the local calendar day (00:00:00.000) containing [timestamp].
     * Two timestamps belong to the same day iff their dayStartMillis are equal.
     */
    fun dayStartMillis(timestamp: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Start of the NEXT local calendar day (00:00:00.000). Walking over
     * Calendar.DAY_OF_YEAR keeps the result correct across DST shifts.
     */
    fun dayEndMillis(timestamp: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /**
     * Groups photos by calendar day ONLY: every photo of the same local day ends
     * up in exactly one cluster, no matter the distance between them.
     *
     * This enforces "one experience per day" for the automatic experience
     * detection. The cluster anchor (lat/lon/timestamp) is the EARLIEST photo
     * of the day.
     */
    fun groupByDay(photos: List<PhotoSample>, tz: TimeZone = TimeZone.getDefault()): List<PhotoCluster> {
        val clusters = mutableListOf<PhotoCluster>()
        photos.sortedBy { it.takenAt }.forEach { photo ->
            val photoDay = dayStartMillis(photo.takenAt, tz)
            val existing = clusters.firstOrNull { dayStartMillis(it.timestamp, tz) == photoDay }
            if (existing != null) {
                existing.uris.add(photo.uri)
            } else {
                clusters.add(PhotoCluster(mutableListOf(photo.uri), photo.lat, photo.lon, photo.takenAt))
            }
        }
        return clusters
    }

    /**
     * Groups photos by calendar day AND location: same-day photos within
     * [radiusMeters] of the cluster anchor stay together, everything farther
     * apart starts a new cluster. Used for the live-trip path only, where a
     * travel day may legitimately contain several stops.
     */
    fun groupByDayAndLocation(
        photos: List<PhotoSample>,
        radiusMeters: Double = STOP_CLUSTER_RADIUS_METERS,
        tz: TimeZone = TimeZone.getDefault()
    ): List<PhotoCluster> {
        val clusters = mutableListOf<PhotoCluster>()
        photos.sortedBy { it.takenAt }.forEach { photo ->
            val photoDay = dayStartMillis(photo.takenAt, tz)
            val existing = clusters.firstOrNull {
                dayStartMillis(it.timestamp, tz) == photoDay &&
                    distanceMeters(it.lat, it.lon, photo.lat, photo.lon) < radiusMeters
            }
            if (existing != null) {
                existing.uris.add(photo.uri)
            } else {
                clusters.add(PhotoCluster(mutableListOf(photo.uri), photo.lat, photo.lon, photo.takenAt))
            }
        }
        return clusters
    }

    /** Great-circle distance in meters (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * asin(sqrt(a))
    }
}
