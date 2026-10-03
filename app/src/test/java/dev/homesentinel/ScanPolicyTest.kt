package dev.homesentinel

import dev.homesentinel.data.wifi.ScanAccessPoint
import dev.homesentinel.data.wifi.ScanPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPolicyTest {
    @Test
    fun recentCachedNetworksCanPopulateTheSelectionList() {
        val points = listOf(ScanAccessPoint("Home", 50_000, -50))
        assertEquals("Home", ScanPolicy.freshNetworks(points, 60_000).single().ssid)
    }

    @Test
    fun selectionListRejectsOldAndFutureResultsIndividually() {
        val points =
            listOf(
                ScanAccessPoint("Old", 9_999, -30),
                ScanAccessPoint("Future", 70_001, -30),
                ScanAccessPoint("Fresh", 70_000, -60),
            )
        assertEquals(listOf("Fresh"), ScanPolicy.freshNetworks(points, 70_000).map { it.ssid })
    }

    @Test
    fun expiredCachedListIsEmptyWithoutCreatingAbsenceEvidence() {
        val points = listOf(ScanAccessPoint("Home", 1_000, -50))
        assertTrue(ScanPolicy.freshNetworks(points, 61_001).isEmpty())
        assertNull(ScanPolicy.evidence(points, "Home", 61_001))
    }

    @Test
    fun signalTimestampBelongsToTheSelectedSsidAndNeverToAnotherAccessPoint() {
        val evidence =
            ScanPolicy.evidence(
                listOf(
                    ScanAccessPoint("Home", 9_000, -50),
                    ScanAccessPoint("Other", 10_000, -40),
                ),
                "Home",
                10_000,
            )!!
        assertEquals(10_000L, evidence.observedAt)
        assertEquals(9_000L, evidence.signalAt)
        assertNull(ScanPolicy.evidence(listOf(ScanAccessPoint("Other", 10_000, -40)), "Home", 10_000)!!.signalAt)
    }

    @Test
    fun homeIsVisibleWithoutAnyConnectionInformation() {
        val e =
            ScanPolicy.evidence(listOf(ScanAccessPoint("Home", 10_000, -50)), "Home", 10_000)
        assertTrue(e!!.present)
    }

    @Test
    fun absentHomeInFreshResultsIsValidEvidence() {
        assertFalse(
            ScanPolicy
                .evidence(listOf(ScanAccessPoint("Other", 10_000, -50)), "Home", 10_000)!!
                .present,
        )
    }

    @Test
    fun whollyStaleResultsAreIgnored() {
        assertNull(
            ScanPolicy.evidence(listOf(ScanAccessPoint("Other", 1_000, -50)), "Home", 61_001),
        )
    }

    @Test
    fun cachedHomeAmongNewResultsDoesNotCountAsPresence() {
        assertFalse(
            ScanPolicy
                .evidence(
                    listOf(
                        ScanAccessPoint("Home", 0, -50),
                        ScanAccessPoint("Other", 70_000, -60),
                    ),
                    "Home",
                    70_000,
                )!!
                .present,
        )
    }

    @Test
    fun ssidComparisonIsCaseSensitive() {
        assertFalse(
            ScanPolicy.evidence(listOf(ScanAccessPoint("HOME", 0, -40)), "Home", 0)!!.present,
        )
    }

    @Test
    fun successfulEmptyScanCanBeUsedAsAbsence() {
        assertFalse(ScanPolicy.evidence(emptyList(), "Home", 123)!!.present)
    }

    @Test
    fun multipleAccessPointsAreGroupedWithoutLosingTheSsid() {
        val list =
            ScanPolicy.networks(
                listOf(
                    ScanAccessPoint("Home", 0, -70),
                    ScanAccessPoint("Home", 0, -40),
                    ScanAccessPoint("", 0, -30),
                ),
            )
        assertEquals(1, list.size)
        assertEquals(2, list.single().accessPoints)
        assertEquals(-40, list.single().signalDbm)
    }
}
