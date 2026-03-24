package com.d_drostes_apps.placestracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE friendId IS NULL ORDER BY date DESC")
    fun getAllEntries(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE friendId = :friendId ORDER BY date DESC")
    fun getEntriesByFriend(friendId: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntryById(id: Int): Entry?

    @Query("SELECT * FROM entries WHERE friendId = :friendId AND title = :title LIMIT 1")
    suspend fun getEntryByFriendAndTitle(friendId: String, title: String): Entry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Entry)

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)
}
