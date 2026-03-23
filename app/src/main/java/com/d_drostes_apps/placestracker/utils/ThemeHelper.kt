package com.d_drostes_apps.placestracker.utils

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial

object ThemeHelper {
    
    fun applyThemeColor(view: View, color: Int) {
        when (view) {
            is MaterialButton -> {
                // Nur die Umrandungsfarbe anpassen, nicht den Hintergrund
                view.strokeColor = ColorStateList.valueOf(color)
                view.setRippleColor(ColorStateList.valueOf(color))
            }
            is FloatingActionButton -> {
                // Hintergrund und Ripple-Effekt anpassen
                view.backgroundTintList = ColorStateList.valueOf(color)
                view.rippleColor = color
            }
            is SwitchMaterial -> {
                // Thumb und Track anpassen
                view.thumbTintList = ColorStateList.valueOf(color)
                view.trackTintList = ColorStateList.valueOf(color).withAlpha(128)
            }
        }

        // Rekursiver Aufruf für Child-Views
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeColor(view.getChildAt(i), color)
            }
        }
    }

    fun isDarkColor(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }
}
