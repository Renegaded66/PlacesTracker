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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GalleryScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private data class PhotoGroup(
        val lat: Double,
        val lon: Double,
        val uris: MutableList<Uri> = mutableListOf(),
        val timestamp: Long
    )

    override suspend fun doWork(): Result {
        Log.d("GalleryScanWorker", "Starting gallery scan...")
        val app = applicationContext as PlacesApplication
        val userDao = app.database.userDao()
        val tripDao = app.database.tripDao()
        val profile = userDao.getUserProfile().first() ?: return Result.success()

        if (!profile.isAutoGalleryScanEnabled || profile.homeLatitude == null || profile.homeLongitude == null) {
            Log.d("GalleryScanWorker", "Auto scan disabled or home location missing")
            return Result.success()
        }

        // 🌟 POINT 3: Check if travel tracking is active
        val activeTrip = tripDao.getActiveTrackingTrip()
        if (activeTrip != null) {
            Log.d("GalleryScanWorker", "Travel tracking is active for trip: ${activeTrip.title}. Skipping scan.")
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences("gallery_scan_prefs", Context.MODE_PRIVATE)
        val lastScanTime = prefs.getLong("last_scan_time", System.currentTimeMillis() - 86400000)
        
        Log.d("GalleryScanWorker", "Scanning media since: ${Date(lastScanTime)}")
        val newMedia = queryNewMedia(lastScanTime)
        Log.d("GalleryScanWorker", "Found ${newMedia.size} new media items total")

        if (newMedia.isEmpty()) {
            prefs.edit().putLong("last_scan_time", System.currentTimeMillis()).apply()
            return Result.success()
        }

        val groups = mutableListOf<PhotoGroup>()
        val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        newMedia.forEach { (uri, dateTaken) ->
            try {
                applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exif = ExifInterface(inputStream)
                    val latLong = FloatArray(2)
                    
                    if (exif.getLatLong(latLong)) {
                        val photoLat = latLong[0].toDouble()
                        val photoLon = latLong[1].toDouble()
                        
                        val distFromHome = FloatArray(1)
                        Location.distanceBetween(profile.homeLatitude!!, profile.homeLongitude!!, photoLat, photoLon, distFromHome)
                        
                        val minDistanceMeters = profile.autoGalleryScanDistance * 1000
                        if (minDistanceMeters == 0 || distFromHome[0] > minDistanceMeters) {
                            var foundGroup = false
                            val photoDay = sdfDay.format(Date(dateTaken))

                            for (group in groups) {
                                val groupDay = sdfDay.format(Date(group.timestamp))
                                
                                // 🌟 POINT 3: Group by Day AND Location (500m)
                                if (photoDay == groupDay) {
                                    val distToGroup = FloatArray(1)
                                    Location.distanceBetween(group.lat, group.lon, photoLat, photoLon, distToGroup)
                                    if (distToGroup[0] < 500) { 
                                        group.uris.add(uri)
                                        foundGroup = true
                                        break
                                    }
                                }
                            }
                            if (!foundGroup) {
                                groups.add(PhotoGroup(photoLat, photoLon, mutableListOf(uri), dateTaken))
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
            createDraft(group.uris, group.lat, group.lon, group.timestamp)
        }

        prefs.edit().putLong("last_scan_time", System.currentTimeMillis()).apply()
        return Result.success()
    }

    private suspend fun createDraft(photoUris: List<Uri>, lat: Double, lon: Double, timestamp: Long) {
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
                date = timestamp,
                location = "$lat,$lon",
                media = internalFilePaths,
                isDraft = true,
                coverImage = internalFilePaths.firstOrNull()
            )
            tripDao.insertStop(draftStop)
            sendNotification("Neuer Stopp erkannt!", "Möchtest du ${internalFilePaths.size} Fotos deinem aktuellen Trip hinzufügen?", activeTrip.id, true)
        } else {
            val draftEntry = Entry(
                title = "Neues Erlebnis (Entwurf)",
                date = timestamp,
                notes = "",
                location = "$lat,$lon",
                media = internalFilePaths,
                isDraft = true,
                coverImage = internalFilePaths.firstOrNull()
            )
            entryDao.insert(draftEntry)
            sendNotification("Neues Erlebnis erkannt!", "Möchtest du ein Erlebnis mit ${internalFilePaths.size} Fotos erstellen?", -1, false)
        }
    }

    private fun queryNewMedia(since: Long): List<Pair<Uri, Long>> {
        val media = mutableListOf<Pair<Uri, Long>>()

        // Images
        val imgProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf((since / 1000).toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                var date = cursor.getLong(dateColumn)
                if (date == 0L) date = cursor.getLong(addedColumn) * 1000

                media.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()) to date)
            }
        }

        // Videos
        val vidProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_TAKEN, MediaStore.Video.Media.DATE_ADDED)

        applicationContext.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection,
            "${MediaStore.Video.Media.DATE_ADDED} >= ?",
            selectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                var date = cursor.getLong(dateColumn)
                if (date == 0L) date = cursor.getLong(addedColumn) * 1000

                media.add(Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()) to date)
            }
        }

        return media
    }

    private fun copyToInternalStorage(uri: Uri): File {
        val mime = applicationContext.contentResolver.getType(uri) ?: "image/jpeg"
        val extension = if (mime.startsWith("video")) ".mp4" else ".jpg"
        val fileName = "auto_${UUID.randomUUID()}$extension"
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
                .addTag("GalleryScan")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "GalleryScan",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d("GalleryScanWorker", "Enqueued periodic work")
        }
        
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("GalleryScan")
            Log.d("GalleryScanWorker", "Cancelled periodic work")
        }
    }
}
