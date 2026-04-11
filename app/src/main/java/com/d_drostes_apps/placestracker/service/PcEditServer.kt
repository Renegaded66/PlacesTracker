package com.d_drostes_apps.placestracker.service

import android.content.Context
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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

class PcEditServer(private val context: Context, private val port: Int) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val app = context.applicationContext as PlacesApplication
    private val database = app.database
    private val entryDao = database.entryDao()
    private val tripDao = database.tripDao()

    // --- 🌟 NEU: Daten-Transfer-Objekte (DTOs) für die API ---

    @Serializable
    data class ApiEntryDto(
        val id: Int = 0,
        val title: String,
        val date: Long,
        val coverImage: String?,
        val media: List<String>,
        val notes: String? = null,
        val location: String? = null,
        // 🌟 FIX 1: Hier Float? statt Int? nutzen
        val rating: Float? = 0f,
        val type: String,
        val isDraft: Boolean = false
    )

    @Serializable
    data class ApiTripStopDto(
        val id: Int = 0,
        val tripId: Int,
        val title: String,
        val date: Long,
        val coverImage: String?,
        val media: List<String>,
        val notes: String? = null,
        val location: String? = null,
        val isMini: Boolean = false,
        val transportMode: String? = null,
        val isDraft: Boolean = false
    )

    @Serializable
    data class ApiTripDto(
        val id: Int = 0,
        val title: String,
        val date: Long,
        val coverImage: String?,
        val notes: String? = null,
        val lastModified: Long,
        val isSyncEnabled: Boolean = true,
        val isTrackingActive: Boolean = false,
        val supabaseId: String = "",
        val isPublic: Boolean = false
    )

    @Serializable
    data class ApiTripDetailDto(
        val trip: ApiTripDto,
        val stops: List<ApiTripStopDto>
    )

    @Serializable
    data class ApiFeedItemDto(
        val id: Int,
        val type: String,
        val title: String,
        val date: Long,
        val coverImage: String?,
        val notes: String? = null,
        val location: String? = null,
        val stopCount: Int? = null,
        val media: List<String> = emptyList() // Medien-Liste für den Feed
    )

    fun start() {
        if (server != null) return

        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true })
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
                    } catch (e: Exception) {
                        call.respondText("Index file not found in assets/pc_edit/", status = HttpStatusCode.InternalServerError)
                    }
                }

                // --- 🌟 Feed-Endpunkt (jetzt mit kompletten Medien-Listen) ---
                get("/api/feed") {
                    val entries = entryDao.getAllEntriesSync()
                    val trips = tripDao.getAllTripsSync()

                    val feedItems = mutableListOf<ApiFeedItemDto>()
                    entries.forEach { entry ->
                        feedItems.add(ApiFeedItemDto(
                            id = entry.id, type = "experience",
                            title = entry.title, date = entry.date,
                            coverImage = entry.coverImage, notes = entry.notes,
                            location = entry.location, media = entry.media // Komplette Medienliste übergeben
                        ))
                    }
                    trips.forEach { trip ->
                        val stops = tripDao.getStopsForTripSync(trip.id)
                        feedItems.add(ApiFeedItemDto(
                            id = trip.id, type = "trip",
                            title = trip.title, date = trip.date,
                            coverImage = trip.coverImage, notes = trip.notes,
                            stopCount = stops.size,
                            media = trip.coverImage?.let { listOf(it) } ?: emptyList() // Fallback auf Cover, falls keine Medien
                        ))
                    }
                    call.respond(feedItems.sortedByDescending { it.date })
                }

                // --- 🌟 Detail-Endpunkte (konvertieren DB-Objekte in API-DTOs) ---

                get("/api/entry/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val entry = entryDao.getEntryById(id)
                    if (entry != null) {
                        // Konvertierung in DTO
                        val dto = ApiEntryDto(
                            id = entry.id, title = entry.title, date = entry.date,
                            coverImage = entry.coverImage, media = entry.media,
                            notes = entry.notes, location = entry.location,
                            rating = entry.rating, type = entry.entryType, isDraft = entry.isDraft
                        )
                        call.respond(dto)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                get("/api/trip/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val trip = tripDao.getTripById(id)
                    if (trip != null) {
                        val stops = tripDao.getStopsForTripSync(trip.id)

                        // Konvertierung in DTOs
                        val tripDto = ApiTripDto(
                            id = trip.id, title = trip.title, date = trip.date,
                            coverImage = trip.coverImage, notes = trip.notes,
                            lastModified = trip.lastModified,
                            isTrackingActive = trip.isTrackingActive, supabaseId = trip.supabaseId,
                            isPublic = trip.isPublic
                        )
                        val stopsDto = stops.map { stop ->
                            ApiTripStopDto(
                                id = stop.id, tripId = stop.tripId, title = stop.title,
                                date = stop.date, coverImage = stop.coverImage,
                                media = stop.media, notes = stop.notes,
                                location = stop.location, isMini = stop.isDraft,
                                transportMode = stop.transportMode, isDraft = stop.isDraft
                            )
                        }
                        call.respond(ApiTripDetailDto(tripDto, stopsDto))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                // --- 🌟 Datei-Upload: Nimmt Multi-part Daten an und speichert die Datei im App-Speicher ---
                post("/api/upload") {
                    val multipart = call.receiveMultipart()
                    val filePaths = mutableListOf<String>()

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val originalFileName = part.originalFileName ?: "unknown_file.jpg"
                            // Eindeutigen Dateinamen erstellen, um Überschreiben zu verhindern
                            val uniqueFileName = "${UUID.randomUUID()}_$originalFileName"
                            val file = File(context.filesDir, uniqueFileName)

                            try {
                                part.streamProvider().use { its ->
                                    file.outputStream().buffered().use { its.copyTo(it) }
                                }
                                // Speichere den absoluten Pfad zur Verwendung in der Datenbank
                                filePaths.add(file.absolutePath)
                                Log.d("PcEditServer", "File saved: ${file.absolutePath}")
                            } catch (e: Exception) {
                                Log.e("PcEditServer", "Failed to save file: ${e.message}")
                            }
                        }
                        part.dispose()
                    }

                    if (filePaths.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, "No files uploaded")
                    } else {
                        // Sende die neuen Pfade zurück, damit das Frontend sie im Speicher-Request mitschicken kann
                        call.respond(mapOf("status" to "ok", "filePaths" to filePaths))
                    }
                }

                // --- 🌟 Speichern: Nimmt das komplette DTO an (inkl. der neuen Pfade) ---
                post("/api/entry/save") {
                    try {
                        val entryDto = call.receive<ApiEntryDto>()

                        // Konvertierung zurück in ein Datenbank-Objekt
                        // Konvertierung zurück in ein Datenbank-Objekt
                        val entry = Entry(
                            id = entryDto.id,
                            title = entryDto.title,
                            date = entryDto.date,
                            coverImage = entryDto.coverImage,
                            media = entryDto.media,
                            notes = entryDto.notes,
                            location = entryDto.location,
                            // 🌟 FIX 2: Das berühmte "Elvis-Operator" (?:). Wenn null vom PC kommt, machen wir 0f draus!
                            rating = entryDto.rating ?: 0f,
                            // 🌟 FIX 3: Das Feld heißt in Entry.kt jetzt "entryType", nicht mehr "type"
                            entryType = entryDto.type,
                            isDraft = entryDto.isDraft,
                            lastModified = System.currentTimeMillis()
                        )

                        if (entry.id == 0) {
                            entryDao.insert(entry)
                        } else {
                            entryDao.update(entry)
                        }
                        call.respond(HttpStatusCode.OK)
                    } catch (e: Exception) {
                        Log.e("PcEditServer", "Failed to save entry: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to save entry: ${e.message}")
                    }
                }

                // Für Trips und TripStops würden wir die genaue gleiche Speicher-Logik bauen...

                get("/media") {
                    val path = call.request.queryParameters["path"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file = File(path)
                    if (file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
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