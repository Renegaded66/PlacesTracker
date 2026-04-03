package com.d_drostes_apps.placestracker.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.d_drostes_apps.placestracker.data.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SharingManager(private val context: Context, private val database: AppDatabase) {

    private val gson = Gson()

    suspend fun shareEntry(entry: Entry, userProfile: UserProfile) = withContext(Dispatchers.IO) {
        val sharedData = SharedData(
            senderId = userProfile.supabaseUserId,
            senderUsername = userProfile.username,
            senderProfilePicture = if (userProfile.profilePicturePath != null) "sender_profile.jpg" else null,
            senderCountryCode = userProfile.countryCode,
            type = "ENTRY",
            entry = entry
        )
        val filesToPack = mutableMapOf<String, File>()
        entry.media.forEach { path ->
            val file = File(path)
            if (file.exists()) filesToPack[file.name] = file
        }
        userProfile.profilePicturePath?.let { path ->
            val file = File(path)
            if (file.exists()) filesToPack["sender_profile.jpg"] = file
        }
        createAndShareZip(sharedData, filesToPack, "Entry_${entry.title.replace(" ", "_")}.ptshare")
    }

    suspend fun shareTrip(trip: Trip, stops: List<TripStop>, locations: List<TripLocation>, userProfile: UserProfile) = withContext(Dispatchers.IO) {
        val sharedData = SharedData(
            senderId = userProfile.supabaseUserId,
            senderUsername = userProfile.username,
            senderProfilePicture = if (userProfile.profilePicturePath != null) "sender_profile.jpg" else null,
            senderCountryCode = userProfile.countryCode,
            type = "TRIP",
            trip = trip,
            tripStops = stops,
            tripLocations = locations
        )
        val filesToPack = mutableMapOf<String, File>()
        trip.coverImage?.let { path ->
            val file = File(path)
            if (file.exists()) filesToPack[file.name] = file
        }
        stops.forEach { stop ->
            stop.media.forEach { path ->
                val file = File(path)
                if (file.exists()) filesToPack[file.name] = file
            }
            stop.coverImage?.let { path ->
                val file = File(path)
                if (file.exists()) filesToPack[file.name] = file
            }
        }
        userProfile.profilePicturePath?.let { path ->
            val file = File(path)
            if (file.exists()) filesToPack["sender_profile.jpg"] = file
        }
        createAndShareZip(sharedData, filesToPack, "Trip_${trip.title.replace(" ", "_")}.ptshare")
    }

    private fun createAndShareZip(sharedData: SharedData, files: Map<String, File>, fileName: String) {
        val cacheFile = File(context.cacheDir, fileName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(cacheFile))).use { out ->
            val json = gson.toJson(sharedData)
            out.putNextEntry(ZipEntry("metadata.json"))
            out.write(json.toByteArray())
            out.closeEntry()
            files.forEach { (name, file) ->
                FileInputStream(file).use { input ->
                    out.putNextEntry(ZipEntry(name))
                    input.copyTo(out)
                    out.closeEntry()
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Teilen mit...").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    suspend fun handleImport(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val file = File(tempDir, entry.name)
                        FileOutputStream(file).use { out -> zipIn.copyTo(out) }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            val metadataFile = File(tempDir, "metadata.json")
            if (!metadataFile.exists()) return@withContext null
            val sharedData = gson.fromJson(FileReader(metadataFile), SharedData::class.java)
            
            // WICHTIG: Die friendId ist jetzt die Supabase-UUID (falls vorhanden)
            val friendId = sharedData.senderId ?: "${sharedData.senderUsername}_${sharedData.senderCountryCode}"
            
            var senderProfilePath: String? = null
            File(tempDir, "sender_profile.jpg").let { file ->
                if (file.exists()) {
                    val dest = File(context.filesDir, "friend_${friendId}_profile.jpg")
                    file.copyTo(dest, true)
                    senderProfilePath = dest.absolutePath
                }
            }

            val friend = Friend(friendId, sharedData.senderUsername, senderProfilePath, sharedData.senderCountryCode)
            database.friendDao().insertFriend(friend)

            // Import content
            if (sharedData.type == "ENTRY" && sharedData.entry != null) {
                val entry = sharedData.entry
                val newMedia = entry.media.map { oldPath ->
                    val fileName = File(oldPath).name
                    val file = File(tempDir, fileName)
                    if (file.exists()) {
                        val dest = File(context.filesDir, "shared_${System.currentTimeMillis()}_$fileName")
                        file.copyTo(dest, true)
                        dest.absolutePath
                    } else oldPath
                }
                val newCover = entry.coverImage?.let { oldPath ->
                    val fileName = File(oldPath).name
                    val file = File(tempDir, fileName)
                    if (file.exists()) {
                        val dest = File(context.filesDir, "shared_cover_${System.currentTimeMillis()}_$fileName")
                        file.copyTo(dest, true)
                        dest.absolutePath
                    } else null
                }
                val existingEntry = database.entryDao().getEntryByFriendAndTitle(friendId, entry.title)
                if (existingEntry != null) {
                    database.entryDao().update(entry.copy(id = existingEntry.id, friendId = friendId, media = newMedia, coverImage = newCover ?: existingEntry.coverImage))
                } else {
                    database.entryDao().insert(entry.copy(id = 0, friendId = friendId, media = newMedia, coverImage = newCover))
                }
            } else if (sharedData.type == "TRIP" && sharedData.trip != null) {
                val trip = sharedData.trip
                val tripCover = trip.coverImage?.let { oldPath ->
                    val fileName = File(oldPath).name
                    val file = File(tempDir, fileName)
                    if (file.exists()) {
                        val dest = File(context.filesDir, "shared_trip_${System.currentTimeMillis()}_$fileName")
                        file.copyTo(dest, true)
                        dest.absolutePath
                    } else null
                }
                val existingTrip = database.tripDao().getTripByFriendAndTitle(friendId, trip.title)
                val newTripId = if (existingTrip != null) {
                    database.tripDao().updateTrip(trip.copy(id = existingTrip.id, friendId = friendId, coverImage = tripCover ?: existingTrip.coverImage))
                    database.tripDao().deleteStopsForTrip(existingTrip.id)
                    database.tripDao().deleteLocationsForTrip(existingTrip.id)
                    existingTrip.id
                } else {
                    database.tripDao().insertTrip(trip.copy(id = 0, friendId = friendId, coverImage = tripCover)).toInt()
                }
                sharedData.tripStops?.forEach { stop ->
                    val newMedia = stop.media.map { oldPath ->
                        val fileName = File(oldPath).name
                        val file = File(tempDir, fileName)
                        if (file.exists()) {
                            val dest = File(context.filesDir, "shared_stop_${System.currentTimeMillis()}_$fileName")
                            file.copyTo(dest, true)
                            dest.absolutePath
                        } else oldPath
                    }
                    val stopCover = stop.coverImage?.let { oldPath ->
                        val fileName = File(oldPath).name
                        val file = File(tempDir, fileName)
                        if (file.exists()) {
                            val dest = File(context.filesDir, "shared_stop_cover_${System.currentTimeMillis()}_$fileName")
                            file.copyTo(dest, true)
                            dest.absolutePath
                        } else null
                    }
                    database.tripDao().insertStop(stop.copy(id = 0, tripId = newTripId, media = newMedia, coverImage = stopCover))
                }
                sharedData.tripLocations?.forEach { loc ->
                    database.tripDao().insertLocation(loc.copy(id = 0, tripId = newTripId))
                }
            }
            tempDir.deleteRecursively()
            friendId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
