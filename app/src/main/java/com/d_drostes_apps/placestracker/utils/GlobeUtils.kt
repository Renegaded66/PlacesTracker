package com.d_drostes_apps.placestracker.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

object GlobeUtils {
    var hasSpunThisSession = false

    /**
     * Checks if the globe has already performed its intro animation in this session.
     * Returns true if it has already spun, false otherwise.
     * Marks it as spun immediately.
     */
    fun checkAndMarkSpun(): Boolean {
        val alreadySpun = hasSpunThisSession
        hasSpunThisSession = true
        return alreadySpun
    }

    /**
     * Creates a small Base64 JPEG thumbnail for the Cesium globe markers.
     * Small enough to be passed via evaluateJavascript safely.
     */
    // =================================================================
    // 🌟 FIX: Speicher-schonendes, blitzschnelles Base64-Thumbnailing
    // =================================================================
    fun getBase64Thumbnail(path: String?): String? {
        try {
            val file = File(path)
            if (!file.exists()) return null

            // 1. Nur die Bildmaße auslesen, OHNE das riesige Bild in den RAM zu laden!
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)

            // 2. Berechnen, wie stark das Bild verkleinert werden muss (Zielgröße: winzige 150px)
            val reqSize = 150
            var inSampleSize = 1
            if (options.outHeight > reqSize || options.outWidth > reqSize) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqSize && halfWidth / inSampleSize >= reqSize) {
                    inSampleSize *= 2
                }
            }

            // 3. Jetzt laden wir nur die bereits verkleinerte Version in den RAM
            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null

            // 4. Exakt auf 150x150 skalieren (verhindert krumme Werte)
            val resized = Bitmap.createScaledBitmap(bitmap, reqSize, reqSize, true)
            val outputStream = ByteArrayOutputStream()

            // 🌟 DER WICHTIGSTE TRICK: Wir nutzen JPEG mit 70% Qualität.
            // Das sieht auf der Karte exakt gleich aus wie PNG, ist aber 10x kleiner als String!
            resized.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)

            return "data:image/jpeg;base64," + android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            return null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
