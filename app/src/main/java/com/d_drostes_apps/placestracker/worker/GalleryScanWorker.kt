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
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.data.TripStop
import com.d_drostes_apps.placestracker.utils.AutoDetection
import com.d_drostes_apps.placestracker.utils.PhotoSample
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class GalleryScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

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

        val activeTrip = tripDao.getActiveTrackingTrip()
        if (activeTrip != null) {
            Log.d("GalleryScanWorker", "Travel tracking is active for trip: ${activeTrip.title}. Scanning for stop drafts.")
            val prefsWhileTracking = applicationContext.getSharedPreferences("gallery_scan_prefs", Context.MODE_PRIVATE)
            val lastScan = prefsWhileTracking.getLong("last_scan_time", System.currentTimeMillis() - 86400000)
            val newMediaWhileTracking = queryNewMedia(lastScan)

            if (newMediaWhileTracking.isNotEmpty()) {
                // Live-Trip: pro Tag können bewusst mehrere Orte (Stops) vorkommen,
                // deshalb Tag+Ort-Clustering beibehalten.
                val groups = AutoDetection.groupByDayAndLocation(toSamples(newMediaWhileTracking))
                groups.forEach { group ->
                    val uris = group.uris.map { Uri.parse(it) }
                    mergeOrCreateStop(uris, group.lat, group.lon, group.timestamp, activeTrip)
                }
            }
            prefsWhileTracking.edit().putLong("last_scan_time", System.currentTimeMillis()).apply()
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

        val newSamples = toSamples(newMedia).filter { sample ->
            val distFromHome = FloatArray(1)
            Location.distanceBetween(profile.homeLatitude!!, profile.homeLongitude!!, sample.lat, sample.lon, distFromHome)
            val minDistanceMeters = profile.autoGalleryScanDistance * 1000
            minDistanceMeters == 0 || distFromHome[0] > minDistanceMeters
        }

        Log.d("GalleryScanWorker", "${newSamples.size} media items with GPS after home filter")

        // 🌟 POINT 3: One experience per calendar day — every photo of a day belongs
        // to exactly one suggestion, no matter how far apart it was taken.
        val groups = AutoDetection.groupByDay(newSamples)
        Log.d("GalleryScanWorker", "Created ${groups.size} day groups")

        groups.forEach { group ->
            val uris = group.uris.map { Uri.parse(it) }
            mergeOrCreateExperience(uris, group.lat, group.lon, group.timestamp)
        }

        prefs.edit().putLong("last_scan_time", System.currentTimeMillis()).apply()
        return Result.success()
    }

    /**
     * Erzeugt einen Erlebnis-Entwurf für den Tag — außer es existiert bereits ein
     * Entwurf an diesem Tag. Dann werden die neuen Fotos dem bestehenden Entwurf
     * hinzugefügt. So entsteht auch über mehrere Scan-Läufe hinweg nur EIN
     * Vorschlag pro Tag.
     */
    private suspend fun mergeOrCreateExperience(photoUris: List<Uri>, lat: Double, lon: Double, timestamp: Long) {
        val app = applicationContext as PlacesApplication
        val entryDao = app.database.entryDao()

        val dayStart = AutoDetection.dayStartMillis(timestamp)
        val dayEnd = AutoDetection.dayEndMillis(timestamp)
        val existingDraft = entryDao.getDraftForDay(dayStart, dayEnd)
        if (existingDraft != null) {
            val mergedMedia = (existingDraft.media + photoUris.mapNotNull { uri ->
                try { copyToInternalStorage(uri).absolutePath } catch (e: Exception) { null }
            }).distinct()
            if (mergedMedia.size == existingDraft.media.size) return
            entryDao.update(
                existingDraft.copy(
                    media = mergedMedia,
                    coverImage = existingDraft.coverImage ?: mergedMedia.firstOrNull()
                )
            )
            Log.d("GalleryScanWorker", "Merged ${photoUris.size} photos into existing draft for day ${Date(dayStart)}")
            return
        }

        val internalFilePaths = photoUris.mapNotNull { uri ->
            try { copyToInternalStorage(uri).absolutePath } catch (e: Exception) { null }
        }
        if (internalFilePaths.isEmpty()) return

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

    /**
     * Live-Trip: Fotos werden als Stop-Entwürfe dem Trip zugeordnet. Existiert bereits
     * ein Stop am selben Tag (Entwurf oder bestätigt), werden die Fotos dort ergänzt
     * statt einen weiteren Stop anzulegen.
     */
    private suspend fun mergeOrCreateStop(photoUris: List<Uri>, lat: Double, lon: Double, timestamp: Long, trip: Trip) {
        val app = applicationContext as PlacesApplication
        val tripDao = app.database.tripDao()

        val dayStart = AutoDetection.dayStartMillis(timestamp)
        val dayEnd = AutoDetection.dayEndMillis(timestamp)
        val existingStop = tripDao.getLatestStopForDay(trip.id, dayStart, dayEnd)
        if (existingStop != null) {
            val mergedMedia = (existingStop.media + photoUris.mapNotNull { uri ->
                try { copyToInternalStorage(uri).absolutePath } catch (e: Exception) { null }
            }).distinct()
            if (mergedMedia.size == existingStop.media.size) return
            tripDao.updateStop(
                existingStop.copy(
                    media = mergedMedia,
                    coverImage = existingStop.coverImage ?: mergedMedia.firstOrNull()
                )
            )
            Log.d("GalleryScanWorker", "Merged ${photoUris.size} photos into existing stop for day ${Date(dayStart)}")
            return
        }

        val internalFilePaths = photoUris.mapNotNull { uri ->
            try { copyToInternalStorage(uri).absolutePath } catch (e: Exception) { null }
        }
        if (internalFilePaths.isEmpty()) return

        val draftStop = TripStop(
            tripId = trip.id,
            title = "Neuer Stopp (Entwurf)",
            date = timestamp,
            location = "$lat,$lon",
            media = internalFilePaths,
            isDraft = true,
            coverImage = internalFilePaths.firstOrNull()
        )
        tripDao.insertStop(draftStop)
        sendNotification("Neuer Stopp erkannt!", "Möchtest du ${internalFilePaths.size} Fotos deinem aktuellen Trip hinzufügen?", trip.id, true)
    }

    /** Wandelt (Uri, timestamp)-Paare in reine, testbare Samples um. */
    private fun toSamples(media: List<Pair<Uri, Long>>): List<PhotoSample> =
        media.mapNotNull { (uri, dateTaken) ->
            try {
                applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exif = ExifInterface(inputStream)
                    val latLong = FloatArray(2)
                    if (exif.getLatLong(latLong)) {
                        PhotoSample(
                            uri = uri.toString(),
                            takenAt = dateTaken,
                            lat = latLong[0].toDouble(),
                            lon = latLong[1].toDouble()
                        )
                    } else null
                }
            } catch (e: Exception) {
                Log.e("GalleryScanWorker", "Error reading EXIF for $uri", e)
                null
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
