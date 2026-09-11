package com.d_drostes_apps.placestracker.utils

import android.view.View
import android.view.ViewPropertyAnimator

/**
 * Wiederverwendbare Micro-Interaktionen für Engagement-Momente.
 * Alle Animationen sind kurz (150–450 ms), dezent und enden immer im definierten Endzustand.
 */
object MicroInteractions {

    /** Pop-in: Element erscheint mit leichtem Überschwung (z.B. neue Karte, Chip, Badge). */
    fun popIn(view: View, startDelay: Long = 0, duration: Long = 260) {
        view.scaleX = 0.85f
        view.scaleY = 0.85f
        view.alpha = 0f
        view.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
            .start()
    }

    /** Gestaffeltes Slide-up für Listen (Feed-Karten, Bucket-Liste) — 1x beim ersten Binden. */
    fun slideUpIn(view: View, position: Int, baseDelay: Long = 40) {
        // Nur die ersten sichtbaren Elemente animieren — weiter unten scrollen ohne Delay.
        val delay = if (position < 6) position * baseDelay else 0L
        view.translationY = 60f
        view.alpha = 0f
        view.animate()
            .translationY(0f).alpha(1f)
            .setDuration(320)
            .setStartDelay(delay)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()
    }

    /** Deutlicher Erfolgs-Puls (z.B. nach dem Speichern): 1x pulsieren, dann Normalzustand. */
    fun successPulse(view: View) {
        view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }.start()
    }

    /** Weiches Atem-Pulsieren für Live-Indikatoren. Gibt den Animator zurück, damit er gestoppt werden kann. */
    fun breathe(view: View, scaleTo: Float = 1.15f, duration: Long = 750): ViewPropertyAnimator {
        view.animate()
            .scaleX(scaleTo).scaleY(scaleTo)
            .setDuration(duration)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(duration).withEndAction {
                    if (view.isAttachedToWindow) breathe(view, scaleTo, duration)
                }.start()
            }
        return view.animate()
    }

    /** Sanftes Einblenden (z.B. Banner, Empty State). */
    fun fadeIn(view: View, duration: Long = 350) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(duration).start()
    }

    /**
     * Zahlen hochzählen (Statistik-Kacheln): der Moment, der Daten "erlebbar" macht.
     * Endet exakt auf dem Zielwert — auch bei to <= 0.
     */
    fun countUp(textView: android.widget.TextView, to: Int, duration: Long = 800) {
        val animator = android.animation.ValueAnimator.ofInt(0, to)
        animator.duration = duration
        animator.addUpdateListener { anim ->
            if (textView.isAttachedToWindow) {
                textView.text = (anim.animatedValue as Int).toString()
            }
        }
        animator.start()
    }
}