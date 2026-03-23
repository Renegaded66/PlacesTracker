package com.d_drostes_apps.placestracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.exifinterface.media.ExifInterface
import androidx.work.*
import com.d_drostes_apps.placestracker.MainActivity
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.data.TripStop
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class GalleryScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private data class PhotoGroup(
        val lat: Double,
        val lon: Double,
        val uris: MutableList<Uri> = mutableListOf()
    )

    override suspend fun doWork(): Result {
        Log.d("GalleryScanWorker", "Starting gallery scan...")
        val app = applicationContext as PlacesApplication
        val userDao = app.database.userDao()
        val profile = userDao.getUserProfile().first() ?: return Result.success()

        if (!profile.isAutoGalleryScanEnabled || profile.homeLatitude == null || profile.homeLongitude == null) {
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences("gallery_scan_prefs", Context.MODE_PRIVATE)
        // Initial scan: last 24 hours. Subsequent scans: since last scan.
        val lastScanTime = prefs.getLong("last_scan_time", System.currentTimeMillis() - 86400000)
        
        Log.d("GalleryScanWorker", "Scanning photos since: ${Date(lastScanTime)}")
        val newPhotos = queryNewPhotos(lastScanTime)
        Log.d("GalleryScanWorker", "Found ${newPhotos.size} new photos total")

        if (newPhotos.isEmpty()) {
            prefs.edit().putLong("last_scan_time", System.currentTimeMillis()).apply()
            return Result.success()
        }

        val groups = mutableListOf<PhotoGroup>()

        newPhotos.forEach { uri ->
            try {
                applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exif = ExifInterface(inputStream)
                    val latLong = FloatArray(2)
                    
                    if (exif.getLatLong(latLong)) {
                        val photoLat = latLong[0].toDouble()
                        val photoLon = latLong[1].toDouble()
                        
                        val distFromHome = FloatArray(1)
                        Location.distanceBetween(profile.homeLatitude, profile.homeLongitude, photoLat, photoLon, distFromHome)
                        
                        if (distFromHome[0] > 20000) { // > 20km from home
                            var foundGroup = false
                            for (group in groups) {
                                val distToGroup = FloatArray(1)
                                Location.distanceBetween(group.lat, group.lon, photoLat, photoLon, distToGroup)
                                if (distToGroup[0] < 500) { // 500m clustering
                                    group.uris.add(uri)
                                    foundGroup = true
                                    break
                                }
                            }
                            if (!foundGroup) {
                                groups.add(PhotoGroup(photoLat, photoLon, mutableListOf(uri)))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryScanWorker", "Error reading EXIF for $uri", e)
            }
        }

        Log.d("GalleryScanWorker", "Created ${groups.size} location groups")

        groups.forEach { group ->
            createDraft(group.uris, group.lat, group.lon)
        }

        prefs.edit().putLong("last_scan_time", System.currentTimeMillis()).apply()
        return Result.success()
    }

    private suspend fun createDraft(photoUris: List<Uri>, lat: Double, lon: Double) {
        val app = applicationContext as PlacesApplication
        val tripDao = app.database.tripDao()
        val entryDao = app.database.entryDao()
        
        val activeTrip = tripDao.getActiveTrackingTrip()
        val internalFilePaths = photoUris.mapNotNull { uri ->
            try { copyToInternalStorage(uri).absolutePath } catch (e: Exception) { null }
        }
        
        if (internalFilePaths.isEmpty()) return

        if (activeTrip != null) {
            val draftStop = TripStop(
                tripId = activeTrip.id,
                title = "Neuer Stopp (Entwurf)",
                date = System.currentTimeMillis(),
                location = "$lat,$lon",
                media = internalFilePaths,
                isDraft = true
            )
            tripDao.insertStop(draftStop)
            sendNotification("Neuer Stopp erkannt!", "Möchtest du ${internalFilePaths.size} Fotos deinem aktuellen Trip hinzufügen?", activeTrip.id, true)
        } else {
            val draftEntry = Entry(
                title = "Neues Erlebnis (Entwurf)",
                date = System.currentTimeMillis(),
                notes = "",
                location = "$lat,$lon",
                media = internalFilePaths,
                isDraft = true
            )
            entryDao.insert(draftEntry)
            sendNotification("Neues Erlebnis erkannt!", "Möchtest du ein Erlebnis mit ${internalFilePaths.size} Fotos erstellen?", -1, false)
        }
    }

    private fun queryNewPhotos(since: Long): List<Uri> {
        val photos = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf((since / 1000).toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                photos.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()))
            }
        }
        return photos
    }

    private fun copyToInternalStorage(uri: Uri): File {
        val fileName = "${UUID.randomUUID()}.jpg"
        val destFile = File(applicationContext.filesDir, fileName)
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    private fun sendNotification(title: String, message: String, tripId: Int, isStop: Boolean) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_DRAFT", true)
            putExtra("TRIP_ID", tripId)
            putExtra("IS_STOP", isStop)
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val channelId = "auto_detection"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Automatische Erkennung", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_marker)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(Random().nextInt(), notification)
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<GalleryScanWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "GalleryScan",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
        
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("GalleryScan")
        }
    }
}
