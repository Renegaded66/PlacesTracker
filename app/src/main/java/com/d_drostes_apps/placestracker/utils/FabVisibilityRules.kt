package com.d_drostes_apps.placestracker.utils

/**
 * Entscheidungsregeln für die Sichtbarkeit des Dashboard-FAB.
 *
 * Der FAB ("+") gehört ausschließlich zur Haupt-Dashboard-Ansicht.
 * Er ist NUR sichtbar, wenn:
 *  - kein Detail offen ist (Trip-Detail, Erlebnis-Detail, Stop-Detail), UND
 *  - der Globus nicht als Vollbild freigelegt ist (Feed-Sheet auf HIDDEN).
 *
 * Ausgelagert als pure Funktion, damit die Regel per JVM-Unit-Test
 * verifizierbar ist (kein Emulator nötig).
 */
object FabVisibilityRules {

    fun shouldShow(detailOpen: Boolean, feedSheetHidden: Boolean): Boolean =
        !detailOpen && !feedSheetHidden
}
