package com.d_drostes_apps.placestracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BucketDao {
    @Query("SELECT * FROM bucket_items ORDER BY isCompleted ASC, date ASC")
    fun getAllBucketItems(): Flow<List<BucketItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBucketItem(item: BucketItem)

    @Update
    suspend fun updateBucketItem(item: BucketItem)

    @Delete
    suspend fun deleteBucketItem(item: BucketItem)

    @Query("SELECT * FROM bucket_items WHERE id = :id")
    suspend fun getBucketItemById(id: Int): BucketItem?
}
