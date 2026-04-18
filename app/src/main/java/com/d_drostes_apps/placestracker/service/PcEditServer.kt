package com.d_drostes_apps.placestracker.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.data.Entry
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import io.ktor.utils.io.toByteArray

class PcEditServer(private val context: Context, private val port: Int) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val app = context.applicationContext as PlacesApplication
    private val database = app.database
    private val entryDao = database.entryDao()
    private val tripDao = database.tripDao()
    private val thumbnailCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()


    @Serializable
    data class ApiEntryDto(
        val id: Int = 0, val title: String, val date: Long, val coverImage: String?,
        val media: List<String>, val notes: String? = null, val location: String? = null,
        val rating: Float? = 0f, val type: String, val isDraft: Boolean = false
    )

    @Serializable
    data class ApiTripStopDto(
        val id: Int = 0, val tripId: Int, val title: String, val date: Long,
        val coverImage: String?, val media: List<String>, val notes: String? = null,
        val location: String? = null, val isMini: Boolean = false,
        val transportMode: String? = null, val isDraft: Boolean = false
    )

    @Serializable
    data class ApiTripDto(
        val id: Int = 0, val title: String, val date: Long, val coverImage: String?,
        val notes: String? = null, val lastModified: Long, val isSyncEnabled: Boolean = true,
        val isTrackingActive: Boolean = false, val supabaseId: String = "", val isPublic: Boolean = false
    )

    @Serializable
    data class ApiTripDetailDto(val trip: ApiTripDto, val stops: List<ApiTripStopDto>)

    @Serializable
    data class ApiFeedItemDto(
        val id: Int, val type: String, val title: String, val date: Long,
        val coverImage: String?, val notes: String? = null, val location: String? = null,
        val stopCount: Int? = null, val media: List<String> = emptyList()
    )

    @Serializable
    data class ApiUploadResponse(
        val status: String,
        val filePaths: List<String> = emptyList(),
        val error: String? = null,
        val teileGefunden: Int? = null,
        val details: List<String>? = null
    )

    // 🌟 NEU: Generiert blitzschnell kleine Thumbnails, ohne den RAM zu sprengen!
    // 🌟 FIX: Die stark verbesserte Thumbnail-Funktion
    private suspend fun getThumbnailBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        // Wenn das Bild schonmal komprimiert wurde, sofort aus dem RAM laden (0 Ladezeit!)
        thumbnailCache[path]?.let { return@withContext it }

        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)

            val reqWidth = 400
            val reqHeight = 400
            var inSampleSize = 1

            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
            val outputStream = ByteArrayOutputStream()
            // 60% Qualität reicht für kleine Vorschauen dicke
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)

            val bytes = outputStream.toByteArray()

            // RAM-Schutz: Wenn mehr als 200 Thumbnails im Speicher sind, leeren wir ihn
            if (thumbnailCache.size > 200) thumbnailCache.clear()
            thumbnailCache[path] = bytes // Im Cache ablegen

            bytes
        } catch (e: Exception) { null }
    }

    fun start() {
        if (server != null) return

        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            // 🌟 NEU: Fehlermeldungen immer als JSON senden, damit der Browser nicht crasht
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    Log.e("PcEditServer", "Global Error: ${cause.message}")
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Unknown Error")))
                }
            }

            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true // 🌟 WICHTIG: Ignoriert Felder vom Browser, die wir nicht kennen
                })
            }
        install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
            }

            routing {
                get("/") {
                    try {
                        val html = context.assets.open("pc_edit/index.html").bufferedReader().use { it.readText() }
                        call.respondText(html, ContentType.Text.Html)
                    } catch (e: Exception) { call.respondText("Index file not found", status = HttpStatusCode.InternalServerError) }
                }

                get("/api/feed") {
                    val entries = entryDao.getAllEntriesSync()
                    val trips = tripDao.getAllTripsSync()
                    val feedItems = mutableListOf<ApiFeedItemDto>()
                    entries.forEach { entry ->
                        feedItems.add(ApiFeedItemDto(
                            id = entry.id,
                            type = entry.entryType, // 🌟 FIX: Hier stand vorher "experience"!
                            title = entry.title, date = entry.date,
                            coverImage = entry.coverImage, notes = entry.notes,
                            location = entry.location, media = entry.media
                        ))
                    }
                    trips.forEach { trip ->
                        val stops = tripDao.getStopsForTripSync(trip.id)
                        feedItems.add(ApiFeedItemDto(
                            id = trip.id, type = "trip", title = trip.title, date = trip.date,
                            coverImage = trip.coverImage, notes = trip.notes, stopCount = stops.size,
                            media = trip.coverImage?.let { listOf(it) } ?: emptyList()
                        ))
                    }
                    call.respond(feedItems.sortedByDescending { it.date })
                }

                get("/api/entry/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val entry = entryDao.getEntryById(id)
                    if (entry != null) {
                        call.respond(ApiEntryDto(
                            id = entry.id, title = entry.title, date = entry.date, coverImage = entry.coverImage, media = entry.media,
                            notes = entry.notes, location = entry.location, rating = entry.rating, type = entry.entryType, isDraft = entry.isDraft
                        ))
                    } else call.respond(HttpStatusCode.NotFound)
                }

                get("/api/trip/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val trip = tripDao.getTripById(id)
                    if (trip != null) {
                        val stops = tripDao.getStopsForTripSync(trip.id)
                        val tripDto = ApiTripDto(
                            id = trip.id, title = trip.title, date = trip.date, coverImage = trip.coverImage,
                            notes = trip.notes, lastModified = trip.lastModified, isTrackingActive = trip.isTrackingActive,
                            supabaseId = trip.supabaseId, isPublic = trip.isPublic
                        )
                        val stopsDto = stops.map { stop ->
                            ApiTripStopDto(
                                id = stop.id, tripId = stop.tripId, title = stop.title, date = stop.date,
                                coverImage = stop.coverImage, media = stop.media, notes = stop.notes,
                                location = stop.location, isMini = stop.isDraft, transportMode = stop.transportMode, isDraft = stop.isDraft
                            )
                        }
                        call.respond(ApiTripDetailDto(tripDto, stopsDto))
                    } else call.respond(HttpStatusCode.NotFound)
                }

                // --- 🌟 Datei-Upload: Kugelsicher und mit Fehler-Ausgabe ---
                // --- 🌟 Datei-Upload: Jetzt mit sauberer Serialisierung! ---
                post("/api/upload") {
                    try {
                        val multipart = call.receiveMultipart()
                        val filePaths = mutableListOf<String>()
                        var partCount = 0
                        val debugInfo = mutableListOf<String>()

                        multipart.forEachPart { part ->
                            partCount++
                            debugInfo.add("Gefunden: name=${part.name}, type=${part::class.simpleName}")

                            if (part is PartData.FileItem) {
                                val cleanName = File(part.originalFileName ?: "img.jpg").name.replace(" ", "_")
                                val uniqueFileName = "${UUID.randomUUID()}_$cleanName"
                                val file = File(context.filesDir, uniqueFileName)

                                try {
                                    // 🌟 FIX: Die moderne Ktor-Methode nutzen!
                                    val fileBytes = part.provider().toByteArray()
                                    file.writeBytes(fileBytes)

                                    filePaths.add(file.absolutePath)
                                    debugInfo.add("Gespeichert: $uniqueFileName (${fileBytes.size} bytes)")
                                } catch (e: Exception) {
                                    debugInfo.add("Speicher-Fehler: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                            part.dispose()
                        }

                        if (filePaths.isEmpty()) {
                            // 🌟 FIX: Wir nutzen unser sauberes ApiUploadResponse-Objekt
                            call.respond(HttpStatusCode.BadRequest, ApiUploadResponse(
                                status = "error",
                                error = "Keine Dateien gespeichert.",
                                teileGefunden = partCount,
                                details = debugInfo
                            ))
                        } else {
                            // 🌟 FIX: Auch im Erfolgsfall das saubere Objekt
                            call.respond(ApiUploadResponse(status = "ok", filePaths = filePaths))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, ApiUploadResponse(
                            status = "error",
                            error = "Upload gecrasht: ${e.message}"
                        ))
                    }
                }

                post("/api/entry/save") {
                    try {
                        val dto = call.receive<ApiEntryDto>()
                        val entry = Entry(
                            id = dto.id, title = dto.title, date = dto.date, coverImage = dto.coverImage, media = dto.media,
                            notes = dto.notes, location = dto.location, rating = dto.rating ?: 0f, entryType = dto.type,
                            isDraft = dto.isDraft, lastModified = System.currentTimeMillis()
                        )
                        if (entry.id == 0) entryDao.insert(entry) else entryDao.update(entry)
                        call.respond(HttpStatusCode.OK)
                    } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, "${e.message}") }
                }

                post("/api/trip/save") {
                    try {
                        val dto = call.receive<ApiTripDto>()
                        val existingTrip = tripDao.getTripById(dto.id)
                        if (existingTrip != null) {
                            tripDao.updateTrip(existingTrip.copy(title = dto.title, notes = dto.notes, lastModified = System.currentTimeMillis()))
                            call.respond(HttpStatusCode.OK)
                        } else call.respond(HttpStatusCode.NotFound)
                    } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError) }
                }

                post("/api/stop/save") {
                    try {
                        val dto = call.receive<ApiTripStopDto>()
                        // Holen des existierenden Stopps
                        val existingStop = tripDao.getStopsForTripSync(dto.tripId).find { it.id == dto.id }

                        if (existingStop != null) {
                            val updated = existingStop.copy(
                                title = dto.title,
                                notes = dto.notes,
                                media = dto.media,
                                coverImage = dto.coverImage,
                                lastModified = System.currentTimeMillis()
                            )
                            tripDao.updateStop(updated)
                            // 🌟 FIX: Immer ein kleines JSON-Objekt zurückgeben!
                            call.respond(mapOf("status" to "ok"))
                        } else {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Stop not found"))
                        }
                    } catch (e: Exception) {
                        Log.e("PcEditServer", "Save failed", e)
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    }
                }

                // 🌟 NEU: Thumbnail-Endpunkt (Extrem schnell, blockiert das Netzwerk nicht!)
                get("/api/thumbnail") {
                    val path = call.request.queryParameters["path"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file = File(path)
                    if (file.exists()) {
                        val bytes = getThumbnailBytes(path)
                        if (bytes != null) call.respondBytes(bytes, ContentType.Image.JPEG)
                        else call.respondFile(file) // Fallback auf Original, falls das Thumbnailing fehlschlägt
                    } else call.respond(HttpStatusCode.NotFound)
                }

                // Das alte Media-Endpunkt für das Laden der Originale, wenn man drauf klickt
                get("/media") {
                    val path = call.request.queryParameters["path"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file = File(path)
                    if (file.exists()) call.respondFile(file) else call.respond(HttpStatusCode.NotFound)
                }
            }
        }.start(wait = false)
        Log.d("PcEditServer", "Server started on port $port")
    }

    fun stop() {
        CoroutineScope(Dispatchers.IO).launch {
            server?.stop(1000, 2000)
            server = null
            Log.d("PcEditServer", "Server stopped")
        }
    }
}