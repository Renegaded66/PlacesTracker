package com.d_drostes_apps.placestracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 🌟 FIX 1: Version von 46 auf 47 erhöht
@Database(entities = [Entry::class, UserProfile::class, Trip::class, TripStop::class, TripLocation::class, Friend::class, BucketItem::class], version = 47, exportSchema = false)
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

        // 🌟 FIX 2: Die neue Migrations-Funktion für Version 47
        private val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Neues Feld in entries
                try {
                    db.execSQL("ALTER TABLE entries ADD COLUMN friendId TEXT DEFAULT NULL")
                } catch (e: Exception) {}

                // Neue Felder in trips
                try {
                    db.execSQL("ALTER TABLE trips ADD COLUMN friendId TEXT DEFAULT NULL")
                } catch (e: Exception) {}

                try {
                    // Booleans werden in SQLite als INTEGER (0 oder 1) gespeichert
                    db.execSQL("ALTER TABLE trips ADD COLUMN isAutoTrip INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {}
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
                    .addMigrations(MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}