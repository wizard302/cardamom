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

/** Removes the queue item at timeline index [EXTRA_INDEX]. */
const val COMMAND_QUEUE_REMOVE = "io.github.wizard302.cardamom.QUEUE_REMOVE"

/**
 * Moves a queue item from play-order position [EXTRA_FROM] to [EXTRA_TO]. With
 * shuffle on this rewrites the shuffle order; otherwise it moves the timeline item.
 */
const val COMMAND_QUEUE_MOVE = "io.github.wizard302.cardamom.QUEUE_MOVE"

/**
 * Re-inserts a removed queue item ([EXTRA_ITEM]) at timeline index [EXTRA_INDEX]
 * and, with shuffle on, at play-order position [EXTRA_TO] — the undo of
 * [COMMAND_QUEUE_REMOVE].
 */
const val COMMAND_QUEUE_RESTORE = "io.github.wizard302.cardamom.QUEUE_RESTORE"

const val EXTRA_ITEM = "item"
const val EXTRA_INDEX = "index"
const val EXTRA_FROM = "from"
const val EXTRA_TO = "to"

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

/**
 * Returns [order] (a shuffled play order as timeline indices) with the entry at
 * position [from] moved to position [to], or null if either is out of range.
 */
fun moveInShuffle(order: List<Int>, from: Int, to: Int): IntArray? {
    if (from !in order.indices || to !in order.indices) return null
    return order.toMutableList().apply { add(to, removeAt(from)) }.toIntArray()
}

/**
 * Maps a saved shuffle [order] (indices into the saved queue) onto the restored
 * queue, where only the entries flagged in [survived] made it back. Returns the
 * order as indices into the restored queue, or null when [order] does not
 * describe the saved queue.
 */
fun restoreShuffleOrder(order: List<Int>, survived: List<Boolean>): IntArray? {
    if (order.size != survived.size || order.sorted() != survived.indices.toList()) return null
    val newIndex = IntArray(survived.size)
    var next = 0
    survived.forEachIndexed { i, kept -> newIndex[i] = if (kept) next++ else -1 }
    return order.filter { survived[it] }.map { newIndex[it] }.toIntArray()
}
