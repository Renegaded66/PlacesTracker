package com.d_drostes_apps.placestracker.data

import android.content.Context
import com.d_drostes_apps.placestracker.worker.SyncWorker
import kotlinx.coroutines.flow.Flow

class EntryRepository(private val entryDao: EntryDao, private val context: Context) {
    val allEntries: Flow<List<Entry>> = entryDao.getAllEntries()

    suspend fun insert(entry: Entry) {
        entryDao.insert(entry)
        SyncWorker.startImmediateSync(context)
    }

    suspend fun update(entry: Entry) {
        entryDao.update(entry)
        SyncWorker.startImmediateSync(context)
    }

    suspend fun delete(entry: Entry) {
        entryDao.delete(entry)
        SyncWorker.startImmediateSync(context)
    }
}
