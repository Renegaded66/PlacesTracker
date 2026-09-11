package com.d_drostes_apps.placestracker.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Zentrales Micro-Interaktion-Feedback (Haptik + Shake).
 * So fühlt sich die App auf jedem Screen gleich an — wie in modernen Social-Apps.
 */
object Feedback {

    /** Leichte Tast-/Bestätigungs-Haptik (z.B. Tab-Wechsel, Chip hinzugefügt). */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Deutliche Bestätigungs-Haptik (z.B. Speichern erfolgreich, Draft bestätigt). */
    fun confirm(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Warn-Haptik für destruktive oder fehlerhafte Aktionen (z.B. Validierungsfehler). */
    fun reject(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Kurzer horizontaler Shake — Standard-Feedback für ungültige Eingaben.
     * Verändert NICHT die Layout-Position (nur translationX, endet auf 0).
     */
    fun shake(view: View, amplitude: Float = 12f, duration: Long = 380) {
        view.animate().translationX(-amplitude).setDuration(duration / 6).withEndAction {
            view.animate().translationX(amplitude).setDuration(duration / 3).withEndAction {
                view.animate().translationX(-amplitude * 0.5f).setDuration(duration / 3).withEndAction {
                    view.animate().translationX(0f).setDuration(duration / 6).start()
                }.start()
            }.start()
        }.start()
    }
}