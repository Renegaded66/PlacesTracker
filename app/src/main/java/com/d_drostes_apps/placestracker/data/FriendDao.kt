package com.d_drostes_apps.placestracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends")
    fun getAllFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE id = :friendId")
    suspend fun getFriendById(friendId: String): Friend?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend)

    @Delete
    suspend fun deleteFriend(friend: Friend)
}
