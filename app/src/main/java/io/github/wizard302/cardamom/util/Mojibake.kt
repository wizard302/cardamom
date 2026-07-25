package io.github.wizard302.cardamom.util

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Undoes "Youâ€™ve"-style mojibake in MediaStore metadata.
 *
 * Android's tag extractor decodes ID3 frames that declare the ISO-8859-1
 * encoding byte with windows-1252, so a file whose frames actually hold UTF-8
 * bytes surfaces as `Youâ€™ve` instead of `You’ve` (TagLib reads the same file
 * correctly, which is why the tag editor shows the right text).
 *
 * Re-encoding the string as windows-1252 and decoding those bytes as strict
 * UTF-8 recovers the original. Anything that does not round-trip — plain ASCII,
 * genuine Latin-1 text, Cyrillic — is returned untouched.
 */
fun String.repairMojibake(): String {
    if (all { it.code < 0x80 }) return this

    val bytes = ByteArray(length)
    for (i in indices) {
        val c = this[i]
        val byte = when {
            // U+0080..U+00FF map to themselves in windows-1252 as well, bar the
            // punctuation block below.
            c.code <= 0xFF -> c.code
            else -> CP1252_HIGH.indexOf(c).takeIf { it >= 0 }?.plus(0x80) ?: return this
        }
        bytes[i] = byte.toByte()
    }

    val decoded = runCatching {
        UTF_8_STRICT.get()!!.reset().decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull() ?: return this

    // A repaired multi-byte sequence always collapses into fewer characters;
    // equal length means nothing was actually decoded.
    return if (decoded.length < length) decoded else this
}

/**
 * windows-1252 characters for bytes 0x80..0x9F, in byte order. The five
 * unassigned slots keep their C1 control character so indices stay aligned.
 */
private const val CP1252_HIGH =
    "\u20AC\u0081\u201A\u0192\u201E\u2026\u2020\u2021" +
        "\u02C6\u2030\u0160\u2039\u0152\u008D\u017D\u008F" +
        "\u0090\u2018\u2019\u201C\u201D\u2022\u2013\u2014" +
        "\u02DC\u2122\u0161\u203A\u0153\u009D\u017E\u0178"

// CharsetDecoder is stateful and not thread-safe; strings are repaired from the
// scanner's IO dispatcher as well as from ViewModel scopes.
private val UTF_8_STRICT = ThreadLocal.withInitial {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
}
