package io.github.wizard302.cardamom.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFoldTest {

    @Test
    fun `strips latin diacritics and case`() {
        assertEquals("hallas", "Halläs".foldForSearch())
        assertEquals("bjork", "Björk".foldForSearch())
        assertEquals("motley crue", "Mötley Crüe".foldForSearch())
        assertEquals("sigur ros", "Sigur Rós".foldForSearch())
    }

    @Test
    fun `spells out letters without a decomposition`() {
        assertEquals("strasse", "Straße".foldForSearch())
        assertEquals("aeon", "Æon".foldForSearch())
        assertEquals("orsted", "Ørsted".foldForSearch())
        assertEquals("lodz", "Łódź".foldForSearch())
    }

    @Test
    fun `folds yo to ye but keeps short i`() {
        assertEquals("ежик", "Ёжик".foldForSearch())
        assertEquals("мой", "Мой".foldForSearch())
        assertEquals("йога", "Йога".foldForSearch())
    }

    @Test
    fun `plain ascii is only lowercased`() {
        assertEquals("ac/dc", "AC/DC".foldForSearch())
    }
}
