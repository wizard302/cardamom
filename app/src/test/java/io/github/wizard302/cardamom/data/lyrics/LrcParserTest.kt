package io.github.wizard302.cardamom.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses timestamps into milliseconds`() {
        val lines = LrcParser.parse(
            """
            [00:12.00]Line one
            [00:47.50]Line two
            [01:03.20]Line three
            """.trimIndent(),
        )
        assertEquals(3, lines.size)
        assertEquals(12_000L, lines[0].timeMs)
        assertEquals("Line one", lines[0].text)
        assertEquals(47_500L, lines[1].timeMs)
        assertEquals(63_200L, lines[2].timeMs)
    }

    @Test
    fun `handles millisecond precision`() {
        val lines = LrcParser.parse("[00:01.234]Hi\n")
        assertEquals(1_234L, lines.single().timeMs)
    }

    @Test
    fun `expands multiple time tags on one line`() {
        val lines = LrcParser.parse("[00:10.00][00:20.00]Repeated\n")
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].timeMs)
        assertEquals(20_000L, lines[1].timeMs)
        assertEquals("Repeated", lines[1].text)
    }

    @Test
    fun `ignores metadata and blank lines`() {
        val lines = LrcParser.parse(
            """
            [ar:Artist]
            [ti:Title]

            [00:05.00]Only line
            """.trimIndent(),
        )
        assertEquals(1, lines.size)
        assertEquals("Only line", lines.single().text)
    }

    @Test
    fun `applies offset shift`() {
        val lines = LrcParser.parse("[offset:500]\n[00:10.00]Shifted\n")
        assertEquals(9_500L, lines.single().timeMs)
    }

    @Test
    fun `sorts lines by time`() {
        val lines = LrcParser.parse("[00:20.00]Second\n[00:10.00]First\n")
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
    }

    @Test
    fun `active index tracks playback position`() {
        val lines = LrcParser.parse("[00:05.00]A\n[00:10.00]B\n[00:15.00]C\n")
        assertEquals(-1, LrcParser.activeIndex(lines, 0))
        assertEquals(0, LrcParser.activeIndex(lines, 5_000))
        assertEquals(0, LrcParser.activeIndex(lines, 9_999))
        assertEquals(1, LrcParser.activeIndex(lines, 10_000))
        assertEquals(2, LrcParser.activeIndex(lines, 60_000))
    }
}
