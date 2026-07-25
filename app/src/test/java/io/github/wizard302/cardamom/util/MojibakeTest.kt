package io.github.wizard302.cardamom.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MojibakeTest {

    @Test
    fun `repairs windows-1252 mangled apostrophe`() {
        assertEquals("You’ve Got Another Thing Coming", "Youâ€™ve Got Another Thing Coming".repairMojibake())
    }

    @Test
    fun `repairs mangled ellipsis`() {
        assertEquals("For the Glory of…", "For the Glory ofâ€¦".repairMojibake())
    }

    @Test
    fun `repairs mangled two-byte latin text`() {
        assertEquals("Björk", "BjÃ¶rk".repairMojibake())
        assertEquals("Café del Mar", "CafÃ© del Mar".repairMojibake())
    }

    @Test
    fun `repairs mangled cyrillic`() {
        assertEquals("Кино", "ÐšÐ¸Ð½Ð¾".repairMojibake())
    }

    @Test
    fun `leaves ascii untouched`() {
        assertEquals("Painkiller", "Painkiller".repairMojibake())
    }

    @Test
    fun `leaves correctly decoded text untouched`() {
        assertEquals("Björk", "Björk".repairMojibake())
        assertEquals("Кино", "Кино".repairMojibake())
        assertEquals("You’ve", "You’ve".repairMojibake())
        assertEquals("Sigur Rós – Ágætis byrjun", "Sigur Rós – Ágætis byrjun".repairMojibake())
    }

    @Test
    fun `leaves lone high characters untouched`() {
        assertEquals("½ Life", "½ Life".repairMojibake())
        assertEquals("AC⚡DC", "AC⚡DC".repairMojibake())
    }
}
