package com.example.mediremind

import com.example.mediremind.util.VoiceAnnouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAnnouncerTest {

    @Test
    fun doseKey_isStableAndUnique() {
        assertEquals("dose_7_1000", VoiceAnnouncer.doseKey(7, 1000))
        assertTrue(VoiceAnnouncer.doseKey(7, 1000) != VoiceAnnouncer.doseKey(7, 1001))
        assertTrue(VoiceAnnouncer.doseKey(7, 1000) != VoiceAnnouncer.doseKey(8, 1000))
    }

    @Test
    fun sameKey_isAnnouncedOnlyOnce() {
        val key = "test_${System.nanoTime()}"
        assertFalse(VoiceAnnouncer.isDuplicate(key, now = 1_000_000L))
        assertTrue(VoiceAnnouncer.isDuplicate(key, now = 1_000_001L))
        assertTrue(VoiceAnnouncer.isDuplicate(key, now = 1_000_002L))
    }

    @Test
    fun differentKeys_areAllAnnounced() {
        val a = "test_a_${System.nanoTime()}"
        val b = "test_b_${System.nanoTime()}"
        assertFalse(VoiceAnnouncer.isDuplicate(a, now = 5_000L))
        assertFalse(VoiceAnnouncer.isDuplicate(b, now = 5_000L))
    }

    @Test
    fun staleKeys_expireAfterAnHour() {
        val key = "test_${System.nanoTime()}"
        val start = 1_000_000L
        assertFalse(VoiceAnnouncer.isDuplicate(key, now = start))
        // Still inside the 1-hour window → blocked.
        assertTrue(VoiceAnnouncer.isDuplicate(key, now = start + 59 * 60_000L))
        // Past the window → spoken again (e.g. next day's same-time dose).
        assertFalse(VoiceAnnouncer.isDuplicate(key, now = start + 61 * 60_000L))
    }

    @Test
    fun oldestKeys_areEvictedWhenMapGrows() {
        // 30 keys max — pushing past the limit must not throw and must keep
        // recent keys de-duplicated.
        val t = 9_000_000L
        for (i in 0 until 40) {
            assertFalse(VoiceAnnouncer.isDuplicate("bulk_${i}_$t", now = t))
        }
        // Newest key still remembered.
        assertTrue(VoiceAnnouncer.isDuplicate("bulk_39_$t", now = t))
        // Very oldest key was evicted → it can be announced again.
        assertFalse(VoiceAnnouncer.isDuplicate("bulk_0_$t", now = t))
    }
}
