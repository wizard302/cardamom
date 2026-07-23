package io.github.wizard302.cardamom.data.playlist

import io.github.wizard302.cardamom.data.media.Track

/** One line's worth of playlist entry, as written to / read from an M3U file. */
data class M3uEntry(
    val path: String,
    val durationSec: Long,
    val artist: String,
    val title: String,
)

/** A parsed M3U line: the file reference plus any `#EXTINF` metadata. */
data class ParsedM3uEntry(
    val path: String,
    val durationSec: Long?,
    val artist: String?,
    val title: String?,
)

object M3uWriter {

    /**
     * Serializes [entries] as UTF-8 M3U text. When [baseDir] is non-null, any
     * entry whose path sits under it is written relative to it (playlist-file
     * relative), otherwise the absolute path is written.
     */
    fun write(entries: List<M3uEntry>, baseDir: String? = null): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        val normalizedBase = baseDir?.trimEnd('/')
        for (entry in entries) {
            // Tags may legally contain newlines; flattened so they cannot break
            // the file structure or inject extra entries.
            sb.append("#EXTINF:").append(entry.durationSec).append(',')
                .append(entry.artist.oneLine()).append(" - ").append(entry.title.oneLine())
                .append('\n')
            sb.append(relativize(entry.path, normalizedBase)).append('\n')
        }
        return sb.toString()
    }

    private fun String.oneLine(): String = replace(NEWLINES, " ").trim()

    private val NEWLINES = Regex("[\\r\\n]+")

    private fun relativize(path: String, baseDir: String?): String =
        if (baseDir != null && path.startsWith("$baseDir/")) {
            path.removePrefix("$baseDir/")
        } else {
            path
        }
}

object M3uParser {

    /** Parses M3U/M3U8 [content]; unknown `#` directives are ignored. */
    fun parse(content: String): List<ParsedM3uEntry> {
        val entries = mutableListOf<ParsedM3uEntry>()
        var pendingDuration: Long? = null
        var pendingArtist: String? = null
        var pendingTitle: String? = null

        for (raw in content.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:") -> {
                    val payload = line.removePrefix("#EXTINF:")
                    val comma = payload.indexOf(',')
                    if (comma >= 0) {
                        pendingDuration = payload.substring(0, comma).trim()
                            .substringBefore('.').toLongOrNull()
                        val rest = payload.substring(comma + 1).trim()
                        val dash = rest.indexOf(" - ")
                        if (dash >= 0) {
                            pendingArtist = rest.substring(0, dash).trim()
                            pendingTitle = rest.substring(dash + 3).trim()
                        } else {
                            pendingTitle = rest.ifEmpty { null }
                        }
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    entries += ParsedM3uEntry(
                        path = line,
                        durationSec = pendingDuration,
                        artist = pendingArtist,
                        title = pendingTitle,
                    )
                    pendingDuration = null
                    pendingArtist = null
                    pendingTitle = null
                }
            }
        }
        return entries
    }
}

/** Result of resolving one parsed entry against the library. */
data class M3uMatch(
    val entry: ParsedM3uEntry,
    val track: Track?,
)

object M3uMatcher {

    /**
     * Resolves parsed entries to library tracks with a cascade:
     * exact path → filename+parent suffix → (title, artist). Relative entry
     * paths are first resolved against [playlistDir].
     */
    fun match(
        entries: List<ParsedM3uEntry>,
        library: List<Track>,
        playlistDir: String? = null,
    ): List<M3uMatch> {
        val byPath = library.associateBy { it.path }
        val byName = library.groupBy { it.path.substringAfterLast('/') }
        val base = playlistDir?.trimEnd('/')

        return entries.map { entry ->
            val absolute = when {
                entry.path.startsWith('/') -> entry.path
                base != null -> "$base/${entry.path}"
                else -> entry.path
            }
            val track = byPath[absolute]
                ?: suffixMatch(entry.path, byName)
                ?: metadataMatch(entry, library)
            M3uMatch(entry, track)
        }
    }

    private fun suffixMatch(entryPath: String, byName: Map<String, List<Track>>): Track? {
        val fileName = entryPath.substringAfterLast('/')
        val candidates = byName[fileName] ?: return null
        if (candidates.size == 1) return candidates.first()
        // Disambiguate by also matching the parent directory segment. When that
        // fails too, report the entry as unresolved instead of silently picking
        // an arbitrary candidate.
        val parent = entryPath.removeSuffix("/$fileName").substringAfterLast('/')
        return candidates.firstOrNull {
            it.path.removeSuffix("/$fileName").substringAfterLast('/') == parent
        }
    }

    private fun metadataMatch(entry: ParsedM3uEntry, library: List<Track>): Track? {
        val title = entry.title?.takeIf { it.isNotBlank() } ?: return null
        val artist = entry.artist
        return library.firstOrNull { track ->
            track.title.equals(title, ignoreCase = true) &&
                (artist.isNullOrBlank() || track.artist.equals(artist, ignoreCase = true))
        }
    }
}
