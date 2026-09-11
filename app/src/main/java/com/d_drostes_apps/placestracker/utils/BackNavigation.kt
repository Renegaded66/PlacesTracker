package com.d_drostes_apps.placestracker.utils

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions

/**
 * Einheitliche Rück-Navigation zum Dashboard (Start-Destination des Nav-Graphen).
 *
 * Verhindert das gemeldete "Fragment verschiebt sich zu vorherigen Einstellungen":
 * Der Zurück-Pfeil einer Detailansicht poppt NIE in den Tab-Stack (Kalender, Freunde,
 * Statistiken, Einstellungen …), sondern kehrt explizit zur Feed/Dashboard-
 * Start-Destination zurück:
 *
 * - popUpTo(startDestination, inclusive=false): alles über dem Dashboard entfernen
 * - setLaunchSingleTop(true): kein Doppel-Eintrag des Dashboards im Stack
 *
 * Der NavController ist absichtlich die einzige Abhängigkeit — dadurch in Fragmenten
 * und im FeedFragment (Inline-Details) identisch benutzbar.
 */
object BackNavigation {

    fun toDashboard(navController: NavController) {
        val current = navController.currentDestination ?: return
        val startDest = navController.graph.findStartDestination()
        if (current.id == startDest.id) return // bereits auf dem Dashboard
        try {
            navController.navigate(
                startDest.id,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(startDest.id, inclusive = false)
                    .build()
            )
        } catch (_: Exception) {
            // Start-Destination ist bereits der aktuelle Eintrag — nichts zu tun
        }
    }
}
