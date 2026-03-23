package com.d_drostes_apps.placestracker.ui.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.d_drostes_apps.placestracker.MainActivity
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

class BackupFragment : Fragment(R.layout.fragment_backup) {

    private lateinit var statusText: TextView
    private lateinit var progressBar: LinearProgressIndicator

    private val createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { performBackup(it) }
    }

    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { performRestore(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val btnCreate = view.findViewById<MaterialButton>(R.id.btnCreateBackup)
        val btnRestore = view.findViewById<MaterialButton>(R.id.btnRestoreBackup)
        statusText = view.findViewById(R.id.tvBackupStatus)
        progressBar = view.findViewById(R.id.backupProgress)

        toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        btnCreate.setOnClickListener {
            val fileName = "PlacesTracker_Backup_${System.currentTimeMillis()}.zip"
            createBackupLauncher.launch(fileName)
        }

        btnRestore.setOnClickListener {
            restoreBackupLauncher.launch("application/zip")
        }
    }

    private fun performBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                showProgress(true, "Backup wird erstellt...")
                withContext(Dispatchers.IO) {
                    val context = requireContext()
                    val dataDir = context.applicationInfo.dataDir
                    val dbPath = context.getDatabasePath("places_tracker_db")
                    val filesDir = context.filesDir
                    val prefsDir = File(dataDir, "shared_prefs")

                    // Check if DB is open before closing
                    val db = (context.applicationContext as PlacesApplication).database
                    if (db.isOpen) {
                        db.close()
                    }

                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                            // Backup Database
                            if (dbPath.exists()) {
                                addToZip(dbPath, "databases/${dbPath.name}", zipOut)
                                val dbWal = File(dbPath.path + "-wal")
                                if (dbWal.exists()) addToZip(dbWal, "databases/${dbWal.name}", zipOut)
                                val dbShm = File(dbPath.path + "-shm")
                                if (dbShm.exists()) addToZip(dbShm, "databases/${dbShm.name}", zipOut)
                            }

                            // Backup Media Files
                            filesDir.listFiles()?.forEach { file ->
                                if (file.isFile) {
                                    addToZip(file, "files/${file.name}", zipOut)
                                }
                            }

                            // Backup SharedPreferences
                            if (prefsDir.exists() && prefsDir.isDirectory) {
                                prefsDir.listFiles()?.forEach { file ->
                                    if (file.isFile) {
                                        addToZip(file, "shared_prefs/${file.name}", zipOut)
                                    }
                                }
                            }
                        }
                    }
                }
                Toast.makeText(requireContext(), "Backup erfolgreich erstellt", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Fehler beim Backup: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                showProgress(false, "")
                // We don't need to restart the app after a simple backup.
                // Room will automatically re-open the database on the next access.
            }
        }
    }

    private fun performRestore(uri: Uri) {
        lifecycleScope.launch {
            try {
                showProgress(true, "Daten werden wiederhergestellt...")
                withContext(Dispatchers.IO) {
                    val context = requireContext()
                    val dataDir = context.applicationInfo.dataDir
                    val dbFolder = context.getDatabasePath("places_tracker_db").parentFile
                    val filesDir = context.filesDir
                    val prefsDir = File(dataDir, "shared_prefs")

                    // Close DB
                    (context.applicationContext as PlacesApplication).database.close()

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                            var entry: ZipEntry? = zipIn.nextEntry
                            while (entry != null) {
                                val destination = when {
                                    entry.name.startsWith("databases/") -> 
                                        File(dbFolder, entry.name.substringAfter("databases/"))
                                    entry.name.startsWith("files/") -> 
                                        File(filesDir, entry.name.substringAfter("files/"))
                                    entry.name.startsWith("shared_prefs/") -> 
                                        File(prefsDir, entry.name.substringAfter("shared_prefs/"))
                                    else -> null
                                }

                                destination?.let {
                                    it.parentFile?.mkdirs()
                                    FileOutputStream(it).use { out -> zipIn.copyTo(out) }
                                }
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                            }
                        }
                    }
                }
                
                Toast.makeText(requireContext(), "Wiederherstellung erfolgreich. App wird neu gestartet...", Toast.LENGTH_LONG).show()
                restartApp()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Fehler bei Wiederherstellung: ${e.message}", Toast.LENGTH_LONG).show()
                showProgress(false, "")
            }
        }
    }

    private fun addToZip(file: File, zipPath: String, zipOut: ZipOutputStream) {
        val buffer = ByteArray(1024)
        FileInputStream(file).use { input ->
            zipOut.putNextEntry(ZipEntry(zipPath))
            var length: Int
            while (input.read(buffer).also { length = it } > 0) {
                zipOut.write(buffer, 0, length)
            }
            zipOut.closeEntry()
        }
    }

    private fun showProgress(show: Boolean, text: String) {
        statusText.text = text
        progressBar.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        val mPendingIntentId = 123456
        val mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val mgr = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent)
        exitProcess(0)
    }
}
