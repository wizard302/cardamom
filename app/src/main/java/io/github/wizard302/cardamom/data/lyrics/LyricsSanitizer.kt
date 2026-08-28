package io.github.wizard302.cardamom.data.lyrics

/**
 * Strips the advertising rip sites bake into lyrics tags — lines such as
 * "Downloaded From www.MusicDuty.Com". That line is often the whole lyrics
 * field, so keeping it would put an advert where the song's words belong; when
 * it sits next to real lyrics it only has to be dropped from them.
 *
 * Works on plain text and on LRC alike: a junk line carrying a timestamp is
 * removed with it, leaving the rest of the timings untouched.
 */
object LyricsSanitizer {

    private val junkPatterns = listOf(
        // "Downloaded from …", "Free download at …", "Dowloaded by …".
        Regex("""\b(?:free\s+)?down?load(?:ed)?\s+(?:from|by|at)\b""", RegexOption.IGNORE_CASE),
        // Any web address: real lyrics practically never carry one.
        Regex("""(?:https?://|www\.)\S""", RegexOption.IGNORE_CASE),
        // A bare host, e.g. "musicduty.com". Deliberately narrow: a TLD that
        // doubles as an English word would match ordinary lines that happen to
        // miss a space after a full stop.
        Regex("""\b[a-z0-9][a-z0-9-]+\.(?:com|net|org|info|biz|xyz)\b""", RegexOption.IGNORE_CASE),
    )

    /** [text] without its junk lines, or null when nothing meaningful remains. */
    fun clean(text: String?): String? {
        if (text.isNullOrBlank()) return null
        if (junkPatterns.none { it.containsMatchIn(text) }) return text
        return text.lineSequence()
            .filterNot { line -> junkPatterns.any { it.containsMatchIn(line) } }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}
