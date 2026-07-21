package io.github.wizard302.cardamom.data.lyrics

/** A single synced lyric line: its start time and text. */
data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {

    // [mm:ss.xx] or [mm:ss.xxx] or [mm:ss]
    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    /**
     * Parses LRC [content] into time-ordered lines. Supports multiple time tags
     * per line (each yields its own entry) and an `[offset:ms]` shift. Metadata
     * tags like `[ar:]` and blank lines are ignored.
     */
    fun parse(content: String): List<LrcLine> {
        val offset = offsetTag.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LrcLine>()

        for (raw in content.lineSequence()) {
            val matches = timeTag.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fraction = m.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                val timeMs = (minutes * 60 + seconds) * 1000 + fractionMs - offset
                lines += LrcLine(timeMs.coerceAtLeast(0), text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** Index of the line active at [positionMs], or -1 before the first line. */
    fun activeIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}
