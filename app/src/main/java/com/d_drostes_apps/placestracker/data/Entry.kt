package com.d_drostes_apps.placestracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: Long,
    val notes: String?,
    val location: String?,
    val media: List<String>,
    val coverImage: String? = null,
    val isDraft: Boolean = false,
    val friendId: String? = null, // Verknüpfung zum Freund (Sender)
    val lastModified: Long = System.currentTimeMillis(),
    val supabaseId: String = UUID.randomUUID().toString(),
    val isPublic: Boolean = false
)
