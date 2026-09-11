package com.d_drostes_apps.placestracker

import com.d_drostes_apps.placestracker.utils.FabVisibilityRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the dashboard FAB visibility rule.
 *
 * Acceptance: the floating action button ("+") is visible on the main
 * dashboard only — never while a detail view (trip detail, experience
 * detail, stop detail) is open, and not while the feed sheet is hidden
 * (globe fullscreen).
 */
class FabVisibilityTest {

    @Test
    fun `dashboard with feed sheet visible shows FAB`() {
        assertTrue(FabVisibilityRules.shouldShow(detailOpen = false, feedSheetHidden = false))
    }

    @Test
    fun `trip detail open hides FAB`() {
        assertFalse(FabVisibilityRules.shouldShow(detailOpen = true, feedSheetHidden = false))
    }

    @Test
    fun `experience detail open hides FAB`() {
        assertFalse(FabVisibilityRules.shouldShow(detailOpen = true, feedSheetHidden = false))
    }

    @Test
    fun `globe fullscreen hides FAB`() {
        assertFalse(FabVisibilityRules.shouldShow(detailOpen = false, feedSheetHidden = true))
    }

    @Test
    fun `detail open while globe fullscreen hides FAB`() {
        assertFalse(FabVisibilityRules.shouldShow(detailOpen = true, feedSheetHidden = true))
    }
}
