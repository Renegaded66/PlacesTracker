package com.d_drostes_apps.placestracker

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Regressionschutz für die Zurück-Pfeil-Fixierung (t_fca4d8b0).
 *
 * Der gemeldete Bug: "Zurück-Pfeil in Detailansichten verschiebt das Fragment
 * zu vorherigen Einstellungen" — verursacht durch findNavController().navigateUp()
 * in den Vollbild-Detailansichten (poppt in den Tab-Stack: Kalender, Freunde,
 * Statistiken, Settings) und durch das fehlende Trip-Detail-Ziel im Inline-
 * Stop-Detail (schloss direkt zum Dashboard statt zum Trip).
 *
 * Der Test scannt die Fragment-Quellen auf die Korrekturen (gleiche Technik wie
 * StickyGuardInitRegressionTest in Aevum):
 * 1. Erlebnis-/Trip-Detail (Vollbild): Zurück-Pfeil → BackNavigation.toDashboard,
 *    KEIN nacktes navigateUp() mehr.
 * 2. Trip-Stop-Detail (inline): Zurück → Trip-Detail (navigateToTripDetail /
 *    popBackStack), nicht mehr der bloße handleBack()-Dashboard-Schließer.
 * 3. Trip-Detail-Ersetzung im Inline-Stack ohne BackStack-Eintrag
 *    (addToBackStack = false), damit der Klick danach nicht den Stop wiederherstellt.
 */
class DetailBackNavigationRegressionTest {

    private val sources: Map<String, File> by lazy {
        // Gradle-Unit-Tests laufen mit dem App-Modul als Working-Dir; zusätzlich
        // Root-Variante abdecken, falls jemand vom Projektroot testet.
        val pkg = "com/d_drostes_apps/placestracker"
        val relPaths = mapOf(
            "EntryDetailFragment.kt" to "$pkg/ui/feed/EntryDetailFragment.kt",
            "TripDetailFragment.kt" to "$pkg/ui/newtrip/TripDetailFragment.kt",
            "TripStopDetailFragment.kt" to "$pkg/ui/newtrip/TripStopDetailFragment.kt",
            "FeedFragment.kt" to "$pkg/ui/feed/FeedFragment.kt"
        )
        val moduleDirs = listOf(
            Paths.get("src/main/java"),
            Paths.get("app/src/main/java")
        )
        relPaths.mapValues { (name, rel) ->
            val file = moduleDirs.asSequence()
                .map { it.resolve(rel) }
                .firstOrNull { Files.exists(it) }
                ?: error("Quelldatei $name nicht gefunden unter src/main/java (Suchwurzel: App-Modul)")
            file.toFile()
        }
    }

    private fun source(name: String): String = sources.getValue(name).readText()

    @Test
    fun `experience detail fullscreen back arrow must navigate to dashboard`() {
        val src = source("EntryDetailFragment.kt")
        // Alt-Verhalten (Bug): einfaches navigateUp → poppt in den Tab-Stack
        assertFalse(
            "EntryDetailFragment darf nicht mehr mit nacktem navigateUp() zurückgehen",
            src.contains("setNavigationOnClickListener { findNavController().navigateUp() }")
        )
        assertTrue(
            "EntryDetailFragment-Zurück-Pfeil muss BackNavigation.toDashboard nutzen",
            src.contains("BackNavigation.toDashboard(findNavController())")
        )
    }

    @Test
    fun `trip detail fullscreen back arrow must navigate to dashboard`() {
        val src = source("TripDetailFragment.kt")
        assertFalse(
            "TripDetailFragment darf nicht mehr mit nacktem navigateUp() zurückgehen",
            src.contains("setNavigationOnClickListener { findNavController().navigateUp() }")
        )
        assertTrue(
            "TripDetailFragment-Zurück-Pfeil muss BackNavigation.toDashboard nutzen",
            src.contains("BackNavigation.toDashboard(findNavController())")
        )
    }

    @Test
    fun `stop detail inline back arrow must return to trip detail not dashboard`() {
        val src = source("TripStopDetailFragment.kt")
        // Bug-Verhalten: pures handleBack() schloss beim direkten Dashboard-Öffnen
        // das Detail statt zum Trip zu wechseln.
        assertFalse(
            "TripStopDetailFragment-Zurück-Pfeil darf nicht mehr pauschal handleBack() rufen",
            src.contains("(parentFragment as? FeedFragment)?.handleBack()")
        )
        assertTrue(
            "TripStopDetailFragment muss für den Trip-Kontext navigieren (Trip-Detail)",
            src.contains("navigateToTripDetail(tripId)") ||
                src.contains("action_tripStopDetailFragment_to_tripDetailFragment")
        )
        assertTrue(
            "TripStopDetailFragment muss tripId race-sicher per DAO-Fallback auflösen",
            src.contains("loadedTripId ?: tripDao.getStopById(stopId)?.tripId")
        )
    }

    @Test
    fun `inline trip detail replacement must not add backstack entry`() {
        val src = source("FeedFragment.kt")
        assertTrue(
            "navigateToDetail muss einen addToBackStack-Schalter für die Stop→Trip-Ersetzung haben",
            src.contains("fun navigateToDetail(item: FeedItem, stopId: Int? = null, addToBackStack: Boolean = true)")
        )
        assertTrue(
            "navigateToTripDetail muss die Ersetzung ohne BackStack-Eintrag durchführen",
            src.contains("navigateToDetail(item, addToBackStack = false)")
        )
    }
}
