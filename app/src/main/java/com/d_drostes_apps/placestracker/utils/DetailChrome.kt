package com.d_drostes_apps.placestracker.utils

import android.graphics.Color
import android.view.View
import androidx.core.widget.NestedScrollView
import com.d_drostes_apps.placestracker.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

/**
 * Schwebende Detail-Toolbar: Der Hero läuft hinter der Toolbar durch (immersive Ansicht).
 * - Icons bleiben immer sichtbar (weiss über dem Hero-Bild)
 * - Der Hintergrund-Scrim blendet beim Scrollen ein
 * - Ab 60% Fade wechseln die Icons auf die Theme-Farbe (Lesbarkeit auf heller Fläche)
 */
object DetailChrome {

    const val HERO_HEIGHT_DP = 400f

    fun attach(rootView: View, heroHeightDp: Float = HERO_HEIGHT_DP) {
        val appBar = rootView.findViewById<AppBarLayout>(R.id.appBar) ?: return
        val toolbar = rootView.findViewById<MaterialToolbar>(R.id.toolbar) ?: return
        val scrollView = rootView.findViewById<NestedScrollView>(R.id.nestedScrollView) ?: return

        val density = rootView.resources.displayMetrics.density
        val heroHeightPx = (heroHeightDp * density).toInt()
        val scrim = appBar.background?.mutate()
        scrim?.alpha = 0
        var dark = false

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val fade = (scrollY / (heroHeightPx * 0.8f)).coerceIn(0f, 1f)
            scrim?.alpha = (fade * 255).toInt()
            val wantDark = fade > 0.6f
            if (wantDark != dark) {
                dark = wantDark
                tint(toolbar, wantDark)
            }
        }
    }

    /** Icon-Tint: weiss über dem Hero, Theme-Farbe auf eingefader Fläche. */
    fun tint(toolbar: MaterialToolbar, dark: Boolean) {
        val onSurface = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        toolbar.navigationIcon?.mutate()?.setTint(if (dark) onSurface else Color.WHITE)
        for (i in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(i).icon?.mutate()?.let { icon ->
                icon.setTint(if (dark) onSurface else Color.WHITE)
            }
        }
    }
}