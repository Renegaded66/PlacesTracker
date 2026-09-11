package com.d_drostes_apps.placestracker.utils

import android.view.animation.AlphaAnimation
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * Save-Button-Zustandsmaschine: verhindert Doppel-Saves, zeigt sichtbaren Ladezustand
 * und gibt ein kurzes Erfolgs-Feedback. Wird von allen Editoren benutzt.
 */
object SaveButton {

    /** Speichern starten: Button sperren + „Speichern…“ anzeigen. */
    fun toLoading(button: MaterialButton, loadingText: String) {
        button.isEnabled = false
        button.tag = button.text
        button.text = loadingText
        button.alpha = 0.7f
    }

    /** Fertig: Zustand zurücksetzen. Optional kurzer Erfolgs-Puls + neuer Text (z.B. „✓ Gespeichert“). */
    fun toIdle(button: MaterialButton, savedText: String? = null, resetDelayMs: Long = 900) {
        button.isEnabled = true
        button.alpha = 1f
        if (savedText != null) {
            val original = button.tag as? CharSequence ?: button.text
            button.text = savedText
            MicroInteractions.successPulse(button)
            button.postDelayed({
                if (button.isAttachedToWindow) button.text = original
            }, resetDelayMs)
        } else {
            (button.tag as? CharSequence)?.let { button.text = it }
        }
    }
}