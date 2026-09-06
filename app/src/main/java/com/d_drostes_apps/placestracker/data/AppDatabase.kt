package com.d_drostes_apps.placestracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 🌟 Schema geändert (BucketItem.folderName). Neue Migration nötig
@Database(entities = [Entry::class, UserProfile::class, Trip::class, TripStop::class, TripLocation::class, Friend::class, BucketItem::class, WeatherCache::class], version = 51, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun userDao(): UserDao
    abstract fun tripDao(): TripDao
    abstract fun friendDao(): FriendDao
abstract fun bucketDao(): BucketDao
abstract fun weatherDao(): WeatherDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE trips ADD COLUMN isPublic INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {}
                try {
                    db.execSQL("ALTER TABLE entries ADD COLUMN isPublic INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {}
            }
        }

        private val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN rating REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE entries ADD COLUMN people TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE entries ADD COLUMN entryType TEXT NOT NULL DEFAULT 'experience'")
            }
        }

        // Migration 46 → 47
        private val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE entries ADD COLUMN friendId TEXT DEFAULT NULL")
                } catch (_: Exception) {}

                try {
                    db.execSQL("ALTER TABLE trips ADD COLUMN friendId TEXT DEFAULT NULL")
                } catch (_: Exception) {}

                try {
                    db.execSQL("ALTER TABLE trips ADD COLUMN isAutoTrip INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        // 🌟 NEU: Migration 47 → 48 (BucketItem.folderName)
        private val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE bucket_items ADD COLUMN folderName TEXT DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        // Migration 48 → 49 (Trip rating + people)
        private val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE trips ADD COLUMN rating REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE trips ADD COLUMN people TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        // Migration 49 → 50 (WeatherCache table)
        private val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS weather_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        lat REAL NOT NULL,
                        lon REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        temperature REAL NOT NULL,
                        condition TEXT NOT NULL,
                        iconCode TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Migration 50 → 51 (Trip.endDate — optionales Reiseende, null = offen)
        private val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN endDate INTEGER DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "places_tracker_db"
                )
                    // 🌟 FIX 3: MIGRATION_46_47 zum Builder hinzugefügt!
                    .addMigrations(MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}