package com.d_drostes_apps.placestracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Entry::class, UserProfile::class, Trip::class, TripStop::class, TripLocation::class, Friend::class, BucketItem::class], version = 41, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun userDao(): UserDao
    abstract fun tripDao(): TripDao
    abstract fun friendDao(): FriendDao
    abstract fun bucketDao(): BucketDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `bucket_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `date` INTEGER, `isCompleted` INTEGER NOT NULL, `media` TEXT NOT NULL, `isTrip` INTEGER NOT NULL)")
            }
        }

        // Sicherheits-Migration: Fügt Spalten hinzu, falls sie fehlen, löscht aber niemals Tabellen.
        private val MIGRATION_SAFE = object : Migration(15, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE user_profile ADD COLUMN isTimelineGalleryEnabled INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Spalte existiert evtl. schon
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "places_tracker_db"
                )
                .addMigrations(MIGRATION_14_15, MIGRATION_SAFE)
                .fallbackToDestructiveMigration(false) // ABSOLUTES VERBOT: Nie wieder die DB löschen!
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
