package com.d_drostes_apps.placestracker.data

import android.content.Context
import android.util.Log
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.utils.LocationUtils
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class SupabaseManager(private val context: Context) {

    private val supabaseUrl = "https://wdvgeazkxwnllfzovlac.supabase.co"
    private val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndkdmdlYXpreHdubGxmem92bGFjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzUxNDQ4NzcsImV4cCI6MjA5MDcyMDg3N30.5OPV82TI2FDZOxMn6ZByX04m6cqhVquNJXY2Z-z1uLs"

    private val client: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl, supabaseKey) {
            install(Postgrest)
        }
    }

    private val database = (context.applicationContext as PlacesApplication).database
    private val userDao = database.userDao()
    private val tripDao = database.tripDao()
    private val entryDao = database.entryDao()
    private val bucketDao = database.bucketDao()

    suspend fun searchUsers(query: String): List<SupabaseUser> {
        return try {
            client.from("users")
                .select(columns = Columns.list("id", "name", "profile_image_url", "last_modified", "last_lat", "last_lon")) {
                    filter {
                        ilike("name", "%$query%")
                    }
                }
                .decodeList<SupabaseUser>()
        } catch (e: Exception) {
            Log.e("SupabaseSearch", "User search failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUserById(userId: String): SupabaseUser? {
        return try {
            client.from("users").select {
                filter { eq("id", userId) }
            }.decodeSingle<SupabaseUser>()
        } catch (e: Exception) { null }
    }

    suspend fun getFriendTrips(userId: String): List<SupabaseTrip> {
        return try {
            client.from("trips").select {
                filter { eq("user_id", userId); eq("is_public", true) }
            }.decodeList<SupabaseTrip>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getFriendEntries(userId: String): List<SupabaseEntry> {
        return try {
            client.from("entries").select {
                filter { eq("user_id", userId); eq("is_public", true) }
            }.decodeList<SupabaseEntry>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun syncAll() {
        /*
        val profile = userDao.getUserProfile().firstOrNull() ?: return
        if (!profile.isSyncEnabled) return

        var userId = profile.supabaseUserId
        if (userId.isNullOrBlank()) {
            userId = UUID.randomUUID().toString()
            userDao.insertOrUpdate(profile.copy(supabaseUserId = userId))
            val refreshedProfile = userDao.getUserProfile().firstOrNull()
            userId = refreshedProfile?.supabaseUserId ?: userId
        }

        // Aktuelle Position holen
        val location = LocationUtils.getLastKnownLocation(context)

        try {
            // 1. User mit aktueller Position
            client.from("users").upsert(SupabaseUser(
                id = userId!!, 
                name = profile.username, 
                last_modified = System.currentTimeMillis(),
                last_lat = location?.latitude,
                last_lon = location?.longitude
            ))

            // 2. Trips & Stopps
            val localTrips = tripDao.getAllTripsSync()
            localTrips.forEach { trip ->
                var tripSid = trip.supabaseId
                if (tripSid.isBlank()) {
                    tripSid = UUID.randomUUID().toString()
                    tripDao.updateTrip(trip.copy(supabaseId = tripSid))
                }
                client.from("trips").upsert(SupabaseTrip(
                    id = tripSid, title = trip.title, date = trip.date,
                    location = null, user_id = userId!!, last_modified = trip.lastModified,
                    is_public = trip.isPublic
                ))
                val stops = tripDao.getStopsForTripSync(trip.id)
                val locations = tripDao.getLocationsForTripSync(trip.id)
                stops.forEach { stop ->
                    var stopSid = stop.supabaseId
                    if (stopSid.isBlank()) {
                        stopSid = UUID.randomUUID().toString()
                        tripDao.insertStop(stop.copy(supabaseId = stopSid))
                    }
                    val nearestLoc = locations.minByOrNull { Math.abs(it.timestamp - stop.date) }
                    client.from("trip_stops").upsert(SupabaseTripStop(
                        id = stopSid, trip_id = tripSid, title = stop.title,
                        description = stop.notes, date = stop.date,
                        latitude = nearestLoc?.latitude, longitude = nearestLoc?.longitude,
                        last_modified = stop.lastModified
                    ))
                }
            }

            // 3. Erlebnisse (Entries)
            val localEntries = entryDao.getAllEntriesSync()
            localEntries.forEach { entry ->
                var entrySid = entry.supabaseId
                if (entrySid.isBlank()) {
                    entrySid = UUID.randomUUID().toString()
                    entryDao.update(entry.copy(supabaseId = entrySid))
                }
                client.from("entries").upsert(SupabaseEntry(
                    id = entrySid, title = entry.title, date = entry.date,
                    location = entry.location, user_id = userId!!, 
                    last_modified = entry.lastModified, is_public = entry.isPublic,
                    rating = entry.rating,
                    people = entry.people.joinToString(","),
                    entry_type = entry.entryType
                ))
            }

            // 4. Bucket Items
            val localBucketItems = bucketDao.getAllBucketItemsSync()
            localBucketItems.forEach { item ->
                var bSid = item.supabaseId
                if (bSid.isBlank()) {
                    bSid = UUID.randomUUID().toString()
                    bucketDao.updateBucketItem(item.copy(supabaseId = bSid))
                }
                client.from("bucket_items").upsert(SupabaseBucketItem(
                    id = bSid, title = item.title, description = item.description,
                    date = item.date, is_completed = item.isCompleted,
                    user_id = userId!!, last_modified = item.lastModified
                ))
            }
            userDao.insertOrUpdate(profile.copy(lastSyncTime = System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Sync failed: ${e.message}")
        }

         */
    }
}
