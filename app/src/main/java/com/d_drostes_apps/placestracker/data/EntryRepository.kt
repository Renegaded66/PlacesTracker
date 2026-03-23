package com.d_drostes_apps.placestracker.data

import kotlinx.coroutines.flow.Flow

class EntryRepository(private val entryDao: EntryDao) {
    val allEntries: Flow<List<Entry>> = entryDao.getAllEntries()

    suspend fun insert(entry: Entry) {
        entryDao.insert(entry)
    }

    suspend fun delete(entry: Entry) {
        entryDao.delete(entry)
    }
}
