package com.d_drostes_apps.placestracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: Long,
    val notes: String? = null,
    val location: String? = null,
    val media: List<String>,
    val coverImage: String? = null,
    val isDraft: Boolean = false,
    val friendId: String? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val supabaseId: String = UUID.randomUUID().toString(),
    val isPublic: Boolean = false,
    val rating: Float = 0f,
    val people: List<String> = emptyList(),
    val entryType: String = "experience"
)