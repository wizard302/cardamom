package io.github.wizard302.cardamom.playback

/**
 * Custom session command carrying a "play next" / "add to queue" request.
 *
 * With shuffle on, ExoPlayer plays the timeline through a separate shuffled
 * order and drops inserted items at a random place in it, so a timeline
 * insertion alone has no audible effect on what plays next. That order is only
 * reachable from the service, and a MediaController's own insertion reaches the
 * player asynchronously — a follow-up command would race it and act on the
 * previous insertion. So the whole operation is handed to the service, which
 * inserts and fixes the shuffle order in one go.
 */
const val COMMAND_ENQUEUE = "io.github.wizard302.cardamom.ENQUEUE"

/** ArrayList of bundled MediaItems to enqueue. */
const val EXTRA_ITEMS = "items"

/** True to play them right after the current track, false to append them. */
const val EXTRA_PLAY_NEXT = "play_next"

/**
 * Moves the timeline indices `insertAt until insertAt + count` inside [order]
 * so that they follow [current] (when [next]) or end up last.
 *
 * [order] is the shuffled play order as timeline indices, already containing the
 * inserted items at wherever ExoPlayer put them. Returns null when the input is
 * inconsistent, in which case the caller should leave the order alone.
 */
fun reorderShuffle(
    order: List<Int>,
    insertAt: Int,
    count: Int,
    current: Int,
    next: Boolean,
): IntArray? {
    if (count <= 0 || order.size < count) return null
    val inserted = insertAt until (insertAt + count)
    if (!order.containsAll(inserted.toList())) return null
    val rest = order.filterNot { it in inserted }
    if (!next) return (rest + inserted).toIntArray()
    val anchor = rest.indexOf(current)
    if (anchor < 0) return null
    return buildList {
        addAll(rest.subList(0, anchor + 1))
        addAll(inserted)
        addAll(rest.subList(anchor + 1, rest.size))
    }.toIntArray()
}
