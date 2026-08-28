package io.github.wizard302.cardamom.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSanitizerTest {

    @Test
    fun `drops a lyrics field that is only an advert`() {
        assertNull(LyricsSanitizer.clean("Downloaded From www.MusicDuty.Com"))
        assertNull(LyricsSanitizer.clean("  \n Downloaded From MusicDuty.Com \n "))
    }

    @Test
    fun `keeps the song and drops the advert around it`() {
        val cleaned = LyricsSanitizer.clean(
            """
            Downloaded From www.MusicDuty.Com
            I just came down from Chippewa
            Had a station wagon and a hundred dollar bill

            Visit songslover.com for more
            """.trimIndent(),
        )
        assertEquals(
            "I just came down from Chippewa\nHad a station wagon and a hundred dollar bill",
            cleaned,
        )
    }

    @Test
    fun `drops an advert line from LRC without disturbing the timings`() {
        val cleaned = LyricsSanitizer.clean(
            """
            [00:00.00]Downloaded From www.MusicDuty.Com
            [00:12.00]Line one
            [00:47.50]Line two
            """.trimIndent(),
        )
        assertEquals("[00:12.00]Line one\n[00:47.50]Line two", cleaned)
        assertEquals(2, LrcParser.parse(cleaned.orEmpty()).size)
    }

    @Test
    fun `leaves clean lyrics untouched`() {
        val text = "Line one\n\nLine two, ok.Me too\nEnd."
        assertEquals(text, LyricsSanitizer.clean(text))
    }

    @Test
    fun `treats null and blank as absent`() {
        assertNull(LyricsSanitizer.clean(null))
        assertNull(LyricsSanitizer.clean("   \n\n "))
    }
}
