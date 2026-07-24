package io.github.wizard302.cardamom.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun `keeps ordinary names untouched`() {
        assertEquals("Road trip 2024", sanitizeFileName("Road trip 2024"))
    }

    @Test
    fun `replaces path and reserved characters`() {
        assertEquals("AC_DC _ Best of_", sanitizeFileName("AC/DC : Best of?"))
        assertEquals("a_b_c_d_e", sanitizeFileName("a<b>c|d\"e"))
    }

    @Test
    fun `trims trailing dots and whitespace`() {
        assertEquals("mix", sanitizeFileName("  mix.  "))
    }

    @Test
    fun `falls back when nothing usable is left`() {
        assertEquals("playlist", sanitizeFileName("   "))
        assertEquals("playlist", sanitizeFileName("..."))
    }
}
