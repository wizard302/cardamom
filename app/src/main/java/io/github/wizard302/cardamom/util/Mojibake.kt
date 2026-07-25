package io.github.wizard302.cardamom.util

import java.nio.charset.Charset

/**
 * Undoes "Youâ€™ve"-style mojibake in MediaStore metadata.
 *
 * Android's tag extractor decodes ID3 frames that declare the ISO-8859-1
 * encoding byte with windows-1252, so a file whose frames actually hold UTF-8
 * bytes surfaces as `Youâ€™ve` instead of `You’ve` (TagLib reads the same file
 * correctly, which is why the tag editor shows the right text).
 *
 * Re-encoding the string as windows-1252 and decoding those bytes as UTF-8
 * recovers the original. Anything that does not round-trip — plain ASCII,
 * genuine Latin-1 text, Cyrillic — is returned untouched.
 */
fun String.repairMojibake(): String {
    if (all { it.code < 0x80 }) return this
    val cp1252 = CP1252 ?: return this

    val bytes = toByteArray(cp1252)
    // Characters windows-1252 cannot encode silently become '?', so check the
    // round trip: what that decoder never produced is not mojibake.
    if (String(bytes, cp1252) != this) return this

    // Decoding is lenient, marking every malformed byte with U+FFFD; its
    // absence means the bytes really were a valid UTF-8 sequence.
    val decoded = String(bytes, Charsets.UTF_8)
    return if (decoded.contains('�')) this else decoded
}

// Not one of the charsets the JVM spec guarantees; on the off chance a device
// lacks it, metadata is left as-is rather than mangled further.
private val CP1252: Charset? = runCatching { Charset.forName("windows-1252") }.getOrNull()
