package io.github.wizard302.cardamom.playback

import io.github.wizard302.cardamom.data.settings.ReplayGainMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainParseTest {

    @Test
    fun `parses the common decibel forms`() {
        assertEquals(-6.50f, parseGainDb("-6.50 dB")!!, 0.001f)
        assertEquals(2f, parseGainDb("+2 dB")!!, 0.001f)
        assertEquals(2f, parseGainDb("+2dB")!!, 0.001f)
        assertEquals(3.1f, parseGainDb("3,1 dB")!!, 0.001f)
        assertEquals(-1.25f, parseGainDb("-1.25")!!, 0.001f)
        assertEquals(-4f, parseGainDb("−4 dB")!!, 0.001f) // Unicode minus
        assertEquals(-8.3f, parseGainDb("  -8.30 DB ")!!, 0.001f)
    }

    @Test
    fun `rejects anything that is not a number`() {
        assertNull(parseGainDb(null))
        assertNull(parseGainDb(""))
        assertNull(parseGainDb("   "))
        assertNull(parseGainDb("dB"))
        assertNull(parseGainDb("loud"))
        assertNull(parseGainDb("-- 3 dB"))
    }

    @Test
    fun `volume is unity when off or untagged`() {
        assertEquals(1f, replayGainVolume(ReplayGain(-6f, -6f), ReplayGainMode.OFF, 0f), 0.0001f)
        assertEquals(1f, replayGainVolume(null, ReplayGainMode.TRACK, 0f), 0.0001f)
        assertEquals(
            1f,
            replayGainVolume(ReplayGain(null, null), ReplayGainMode.TRACK, 0f),
            0.0001f,
        )
    }

    @Test
    fun `attenuates by the selected gain and never amplifies`() {
        // -6 dB ≈ half the amplitude.
        assertEquals(
            0.5012f,
            replayGainVolume(ReplayGain(-6f, -12f), ReplayGainMode.TRACK, 0f),
            0.001f,
        )
        assertEquals(
            0.2512f,
            replayGainVolume(ReplayGain(-6f, -12f), ReplayGainMode.ALBUM, 0f),
            0.001f,
        )
        // Positive net gain clamps at unity: there is no headroom above 1.0.
        assertEquals(1f, replayGainVolume(ReplayGain(3f, null), ReplayGainMode.TRACK, 0f), 0.0001f)
        // The preamp shifts the result.
        assertEquals(
            0.2512f,
            replayGainVolume(ReplayGain(-6f, null), ReplayGainMode.TRACK, -6f),
            0.001f,
        )
    }

    @Test
    fun `falls back to the other gain when the requested one is missing`() {
        assertEquals(
            0.5012f,
            replayGainVolume(ReplayGain(null, -6f), ReplayGainMode.TRACK, 0f),
            0.001f,
        )
        assertEquals(
            0.5012f,
            replayGainVolume(ReplayGain(-6f, null), ReplayGainMode.ALBUM, 0f),
            0.001f,
        )
    }
}
