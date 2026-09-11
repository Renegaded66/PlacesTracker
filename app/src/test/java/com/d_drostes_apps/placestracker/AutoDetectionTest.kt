package com.d_drostes_apps.placestracker

import com.d_drostes_apps.placestracker.utils.AutoDetection
import com.d_drostes_apps.placestracker.utils.PhotoSample
import com.d_drostes_apps.placestracker.utils.PhotoCluster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Tests for the automatic experience detection grouping.
 *
 * Acceptance: for a given day only ONE experience suggestion is generated —
 * no duplicate suggestions for the same day, no matter how far apart the
 * photos of that day were taken.
 */
class AutoDetectionTest {

    private val berlin: TimeZone = TimeZone.getTimeZone("Europe/Berlin")

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int, tz: TimeZone = berlin): Long {
        val cal = Calendar.getInstance(tz)
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun photo(uri: String, takenAt: Long, lat: Double = 52.0, lon: Double = 13.0) =
        PhotoSample(uri, takenAt, lat, lon)

    // ── Acceptance: one experience per day ────────────────────────────────

    @Test
    fun `same day photos far apart produce a single cluster`() {
        // Morning in one part of the city, afternoon 4 km away: distance and
        // time gaps are "slightly larger" → previously produced 2 suggestions.
        val day = timestamp(2026, 9, 2, 8, 15)
        val photos = listOf(
            photo("a.jpg", day, 52.5200, 13.4050),            // 08:15 Mitte
            photo("b.jpg", day + 3600_000, 52.5400, 13.4100), // 09:15 ~2.2 km away
            photo("c.jpg", day + 5 * 3600_000, 52.5600, 13.3900) // 13:15 ~4.2 km away
        )

        val clusters = AutoDetection.groupByDay(photos)

        assertEquals(1, clusters.size)
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), clusters[0].uris)
    }

    @Test
    fun `one cluster per calendar day across multiple days`() {
        val photos = listOf(
            photo("d1a.jpg", timestamp(2026, 9, 2, 9, 0)),
            photo("d1b.jpg", timestamp(2026, 9, 2, 18, 0)),
            photo("d2a.jpg", timestamp(2026, 9, 3, 10, 0)),
            photo("d2b.jpg", timestamp(2026, 9, 3, 20, 0)),
            photo("d3a.jpg", timestamp(2026, 9, 4, 11, 0))
        )

        val clusters = AutoDetection.groupByDay(photos)

        assertEquals(3, clusters.size)
        assertEquals(listOf("d1a.jpg", "d1b.jpg"), clusters[0].uris)
        assertEquals(listOf("d2a.jpg", "d2b.jpg"), clusters[1].uris)
        assertEquals(listOf("d3a.jpg"), clusters[2].uris)
    }

    @Test
    fun `midnight boundary splits into two days`() {
        val photos = listOf(
            photo("late.jpg", timestamp(2026, 9, 2, 23, 59)),
            photo("early.jpg", timestamp(2026, 9, 3, 0, 1))
        )

        val clusters = AutoDetection.groupByDay(photos)

        assertEquals(2, clusters.size)
        assertEquals(listOf("late.jpg"), clusters[0].uris)
        assertEquals(listOf("early.jpg"), clusters[1].uris)
    }

    @Test
    fun `cluster anchor is the earliest photo of the day`() {
        val first = timestamp(2026, 9, 2, 7, 30)
        val photos = listOf(
            photo("late.jpg", first + 10 * 3600_000, 52.5100, 13.4400),
            photo("early.jpg", first, 52.5200, 13.4050)
        )

        val clusters = AutoDetection.groupByDay(photos)

        assertEquals(1, clusters.size)
        assertEquals(first, clusters[0].timestamp)
        assertEquals(52.5200, clusters[0].lat, 1e-9)
        assertEquals(13.4050, clusters[0].lon, 1e-9)
    }

    @Test
    fun `input order does not matter`() {
        val photos = listOf(
            photo("last.jpg", timestamp(2026, 9, 2, 21, 0)),
            photo("first.jpg", timestamp(2026, 9, 2, 6, 0)),
            photo("middle.jpg", timestamp(2026, 9, 2, 12, 0))
        )

        val clusters = AutoDetection.groupByDay(photos.shuffled())

        assertEquals(1, clusters.size)
        assertEquals(3, clusters[0].uris.size)
    }

    // ── Day boundary helpers ──────────────────────────────────────────────

    @Test
    fun `dayStartMillis is local midnight`() {
        val t = timestamp(2026, 9, 2, 14, 30)
        val expectedMidnight = timestamp(2026, 9, 2, 0, 0)

        assertEquals(expectedMidnight, AutoDetection.dayStartMillis(t, berlin))
    }

    @Test
    fun `day boundaries are correct across DST change`() {
        // 2026-03-29: Europe/Berlin switches to CEST (23h day).
        val t = timestamp(2026, 3, 29, 12, 0)
        assertEquals(
            23 * 3600_000L,
            AutoDetection.dayEndMillis(t, berlin) - AutoDetection.dayStartMillis(t, berlin)
        )

        // 2026-10-25: Europe/Berlin switches back to CET (25h day).
        val t2 = timestamp(2026, 10, 25, 12, 0)
        assertEquals(
            25 * 3600_000L,
            AutoDetection.dayEndMillis(t2, berlin) - AutoDetection.dayStartMillis(t2, berlin)
        )
    }

    @Test
    fun `photos around DST fall-back stay in one day`() {
        // 2026-10-25 in Berlin: clocks go back at 03:00 CEST → 02:00 CET.
        // Both 01:30 and the second 02:30 belong to the same calendar day.
        val first = timestamp(2026, 10, 25, 1, 30)
        val secondOccurrence230 = timestamp(2026, 10, 25, 2, 30)

        assertEquals(
            AutoDetection.dayStartMillis(first, berlin),
            AutoDetection.dayStartMillis(secondOccurrence230, berlin)
        )
        assertEquals(1, AutoDetection.groupByDay(listOf(photo("a.jpg", first), photo("b.jpg", secondOccurrence230)), berlin).size)
    }

    // ── Live-trip path keeps day+location grouping ────────────────────────

    @Test
    fun `live trip grouping still splits far apart same day photos`() {
        val day = timestamp(2026, 9, 2, 10, 0)
        val photos = listOf(
            photo("city1.jpg", day, 52.5200, 13.4050),
            photo("city2.jpg", day + 3600_000, 52.5210, 13.4060),    // < 500 m → same stop
            photo("outside.jpg", day + 2 * 3600_000, 52.6000, 13.7000) // ~20 km away → new stop
        )

        val clusters = AutoDetection.groupByDayAndLocation(photos, 500.0, berlin)

        assertEquals(2, clusters.size)
        assertEquals(listOf("city1.jpg", "city2.jpg"), clusters[0].uris)
        assertEquals(listOf("outside.jpg"), clusters[1].uris)
    }

    // ── Geometry sanity ──────────────────────────────────────────────────

    @Test
    fun `distanceMeters is approximately correct`() {
        // 1 degree of latitude ≈ 111.2 km.
        val d = AutoDetection.distanceMeters(52.0, 13.0, 53.0, 13.0)
        assertTrue("expected ~111 km, got $d", d in 110_000.0..113_000.0)
    }
}
