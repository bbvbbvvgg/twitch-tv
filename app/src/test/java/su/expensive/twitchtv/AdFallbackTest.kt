package su.expensive.twitchtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import su.expensive.twitchtv.player.AdFallback
import su.expensive.twitchtv.player.AdFallback.Action
import su.expensive.twitchtv.player.DiagLog

class AdFallbackTest {
    /** Regression: ads in the very first playlist + every backup has ads → must start playback. */
    @Test fun adsBeforeAnythingPlayedStartsPrimaryWithAds() {
        assertEquals(Action.START_PRIMARY_WITH_ADS, AdFallback.decide(onBackup = false, hasActiveSource = false))
        // Re-resolve path: an old URL exists but the player was not (re)started for this attempt.
        assertEquals(Action.START_PRIMARY_WITH_ADS, AdFallback.decide(onBackup = true, hasActiveSource = false))
    }

    @Test fun playingSourcesAreKept() {
        assertEquals(Action.KEEP_PRIMARY_WAIT_AD_END, AdFallback.decide(onBackup = false, hasActiveSource = true))
        assertEquals(Action.KEEP_BACKUP_WAIT_PRIMARY, AdFallback.decide(onBackup = true, hasActiveSource = true))
    }

    @Test fun logHidesUserIp() {
        val s = DiagLog.sanitize("#EXT-X-TWITCH-INFO:USER-IP=\"203.0.113.7\",USER-COUNTRY=\"IE\" from 198.51.100.23")
        assertFalse(s.contains("203.0.113.7")); assertFalse(s.contains("198.51.100.23"))
        assertTrue(s.contains("USER-COUNTRY=\"IE\""))
    }
}
