package io.github.wizard302.cardamom.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parses EXTINF duration artist and title`() {
        val entries = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:210,Pink Floyd - Time
            /music/time.flac
            """.trimIndent(),
        )
        assertEquals(1, entries.size)
        val e = entries.first()
        assertEquals("/music/time.flac", e.path)
        assertEquals(210L, e.durationSec)
        assertEquals("Pink Floyd", e.artist)
        assertEquals("Time", e.title)
    }

    @Test
    fun `parses entry without EXTINF`() {
        val entries = M3uParser.parse("/music/plain.mp3\n")
        assertEquals(1, entries.size)
        assertEquals("/music/plain.mp3", entries.first().path)
        assertNull(entries.first().durationSec)
        assertNull(entries.first().artist)
    }

    @Test
    fun `ignores blank lines and unknown directives`() {
        val entries = M3uParser.parse(
            """
            #EXTM3U
            #PLAYLIST:My Mix

            #EXTINF:100,A - B
            /a.mp3

            /b.mp3
            """.trimIndent(),
        )
        assertEquals(2, entries.size)
        assertEquals("/a.mp3", entries[0].path)
        assertEquals("/b.mp3", entries[1].path)
        // EXTINF is consumed by the first entry only.
        assertNull(entries[1].title)
    }

    @Test
    fun `handles title without artist separator`() {
        val entries = M3uParser.parse(
            "#EXTINF:-1,Just A Title\n/x.mp3\n",
        )
        assertNull(entries.first().artist)
        assertEquals("Just A Title", entries.first().title)
        assertEquals(-1L, entries.first().durationSec)
    }

    @Test
    fun `keeps relative paths verbatim`() {
        val entries = M3uParser.parse("rock/a.mp3\n../b.mp3\n")
        assertEquals("rock/a.mp3", entries[0].path)
        assertEquals("../b.mp3", entries[1].path)
    }
}
