package io.github.wizard302.cardamom.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uWriterTest {

    @Test
    fun `writes EXTM3U header and EXTINF lines`() {
        val out = M3uWriter.write(
            listOf(
                M3uEntry("/music/a.mp3", 210, "Artist A", "Title A"),
                M3uEntry("/music/b.mp3", 95, "Artist B", "Title B"),
            ),
        )
        val lines = out.trimEnd().lines()
        assertEquals("#EXTM3U", lines[0])
        assertEquals("#EXTINF:210,Artist A - Title A", lines[1])
        assertEquals("/music/a.mp3", lines[2])
        assertEquals("#EXTINF:95,Artist B - Title B", lines[3])
        assertEquals("/music/b.mp3", lines[4])
    }

    @Test
    fun `writes absolute paths when no base dir`() {
        val out = M3uWriter.write(listOf(M3uEntry("/music/rock/a.mp3", 1, "A", "T")))
        assertTrue(out.contains("\n/music/rock/a.mp3\n"))
    }

    @Test
    fun `writes paths relative to base dir when under it`() {
        val out = M3uWriter.write(
            listOf(M3uEntry("/music/rock/a.mp3", 1, "A", "T")),
            baseDir = "/music",
        )
        assertTrue(out.contains("\nrock/a.mp3\n"))
    }

    @Test
    fun `flattens newlines in artist and title`() {
        val out = M3uWriter.write(
            listOf(M3uEntry("/music/a.mp3", 10, "Bad\nArtist", "Line\r\nBreak Title")),
        )
        val lines = out.trimEnd().lines()
        assertEquals("#EXTINF:10,Bad Artist - Line Break Title", lines[1])
        assertEquals("/music/a.mp3", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun `keeps absolute path when outside base dir`() {
        val out = M3uWriter.write(
            listOf(M3uEntry("/other/a.mp3", 1, "A", "T")),
            baseDir = "/music",
        )
        assertTrue(out.contains("\n/other/a.mp3\n"))
    }
}
