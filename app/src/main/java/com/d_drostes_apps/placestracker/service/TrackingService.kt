package com.d_drostes_apps.placestracker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.d_drostes_apps.placestracker.MainActivity
import com.d_drostes_apps.placestracker.data.AppDatabase
import com.d_drostes_apps.placestracker.data.TripLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentTripId: Int = -1

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            locationResult.lastLocation?.let { location ->
                checkAndSaveLocation(location)
            }
        }
    }

    /** Stoppt das Tracking automatisch, sobald das optionale Enddatum erreicht ist. Liefert true, wenn gestoppt wurde. */
    private suspend fun checkTripEndDate(): Boolean {
        if (currentTripId == -1) return false
        val database = AppDatabase.getDatabase(applicationContext)
        val trip = database.tripDao().getTripById(currentTripId) ?: return false
        val now = System.currentTimeMillis()
        if (trip.endDate != null && trip.endDate < now) {
            database.tripDao().updateTrackingStatus(currentTripId, false)
            stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return true
        }
        return false
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_TRACKING -> {
                    currentTripId = it.getIntExtra(EXTRA_TRIP_ID, -1)
                    if (currentTripId != -1) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                        } else {
                            startForeground(NOTIFICATION_ID, createNotification())
                        }
                        startLocationUpdates()
                    }
                }
                ACTION_STOP_TRACKING -> {
                    stopLocationUpdates()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5 * 60 * 1000L)
            .setMinUpdateIntervalMillis(2 * 60 * 1000L)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun checkAndSaveLocation(location: Location) {
        if (currentTripId == -1) return
        
        serviceScope.launch {
            // Automatischer Stopp am Enddatum (Übergangs-Automation)
            if (checkTripEndDate()) return@launch

            val database = AppDatabase.getDatabase(applicationContext)
            val tripDao = database.tripDao()
            val lastLocation = tripDao.getLastLocationForTrip(currentTripId)
            val lastStop = tripDao.getStopsForTripSync(currentTripId).lastOrNull()
            
            var lastLat: Double? = null
            var lastLon: Double? = null
            
            // We need to compare against the ABSOLUTE LATEST point (either a real stop or a mini-stop)
            val locTime = lastLocation?.timestamp ?: 0L
            val stopTime = lastStop?.date ?: 0L
            
            if (locTime > stopTime && lastLocation != null) {
                lastLat = lastLocation.latitude
                lastLon = lastLocation.longitude
            } else if (lastStop != null) {
                val coords = lastStop.location?.split(",")
                if (coords?.size == 2) {
                    lastLat = coords[0].toDoubleOrNull()
                    lastLon = coords[1].toDoubleOrNull()
                }
            }
            
            var shouldSave = true
            if (lastLat != null && lastLon != null) {
                val results = FloatArray(1)
                Location.distanceBetween(lastLat, lastLon, location.latitude, location.longitude, results)
                if (results[0] < 500) {
                    shouldSave = false
                }
            }

            if (shouldSave) {
                val tripLocation = TripLocation(
                    tripId = currentTripId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
                tripDao.insertLocation(tripLocation)
            }
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Trip Tracking Active")
            .setContentText("Recording your journey...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"
        private const val NOTIFICATION_ID = 123
        private const val CHANNEL_ID = "tracking_channel"
    }
}
