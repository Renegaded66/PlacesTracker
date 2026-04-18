package com.d_drostes_apps.placestracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.d_drostes_apps.placestracker.R

class PcEditService : Service() {
    private var server: PcEditServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("PORT", 8080) ?: 8080

        // 🌟 FIX: Eine andauernde Benachrichtigung erstellen, die den Tiefschlaf verhindert
        val notification = NotificationCompat.Builder(this, "PC_EDIT_CHANNEL")
            .setContentTitle("PC Editor aktiv")
            .setContentText("Der lokale Server läuft auf Port $port")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ggf. gegen dein App-Icon tauschen
            .setOngoing(true)
            .build()

        // ID 2, damit es nicht mit deinem TrackingService (falls der ID 1 hat) kollidiert
        startForeground(2, notification)

        // Server starten
        if (server == null) {
            // "this" funktioniert, da ein Service auch ein Context ist!
            server = PcEditServer(this, port)
            server?.start()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        server = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "PC_EDIT_CHANNEL",
                "PC Editor Server",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}