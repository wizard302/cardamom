package io.github.wizard302.cardamom.util

import java.text.Normalizer
import java.util.Locale

/**
 * Folds text for accent- and case-insensitive search, so "hallas" finds
 * "halläs", "bjork" finds "Björk" and "ежик" finds "Ёжик". The query and the
 * searched text must both be folded.
 *
 * Diacritics are stripped via NFD decomposition, letters that do not decompose
 * (ß, æ, ø, ł, …) are spelled out, and Cyrillic "й" is kept intact: in Russian
 * it is a distinct letter, not an accented "и".
 */
fun String.foldForSearch(): String {
    val lower = lowercase(Locale.ROOT)
    if (lower.all { it.code < 0x80 }) return lower
    val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    for ((i, c) in decomposed.withIndex()) {
        if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) {
            if (c == COMBINING_BREVE && i > 0 && decomposed[i - 1] == 'и') out.append(c)
            continue
        }
        when (c) {
            'ß' -> out.append("ss")
            'æ' -> out.append("ae")
            'œ' -> out.append("oe")
            'þ' -> out.append("th")
            'ø' -> out.append('o')
            'đ', 'ð' -> out.append('d')
            'ł' -> out.append('l')
            'ħ' -> out.append('h')
            'ı' -> out.append('i')
            else -> out.append(c)
        }
    }
    // Recompose the kept "й".
    return Normalizer.normalize(out, Normalizer.Form.NFC)
}

private const val COMBINING_BREVE = '̆'
