package io.github.wizard302.cardamom.playback

/**
 * Custom session command used to fix up the shuffle order after a queue
 * insertion.
 *
 * With shuffle on, ExoPlayer plays the timeline through a separate shuffled
 * order, and inserted items land at a random spot in it. "Play next" and "add
 * to queue" therefore have no audible effect on the play order. The shuffle
 * order lives inside ExoPlayer and is not reachable through MediaController, so
 * the controller inserts the items and then asks the service to move them.
 */
const val COMMAND_REORDER_SHUFFLE = "io.github.wizard302.cardamom.REORDER_SHUFFLE"

/** Timeline index the items were inserted at. */
const val EXTRA_INSERT_AT = "insert_at"

/** How many items were inserted. */
const val EXTRA_INSERT_COUNT = "insert_count"

/** True to place them right after the current item, false to place them last. */
const val EXTRA_INSERT_NEXT = "insert_next"

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
