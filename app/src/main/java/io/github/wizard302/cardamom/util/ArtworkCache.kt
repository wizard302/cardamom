package io.github.wizard302.cardamom.util

import android.content.Context
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache

/**
 * Drops [artworkUri] from Coil's memory and disk caches, so a cover changed
 * by a tag edit shows up instead of the cached old one.
 */
fun Context.invalidateArtworkCache(artworkUri: Uri) {
    runCatching {
        val loader = SingletonImageLoader.get(this)
        val key = artworkUri.toString()
        loader.memoryCache?.remove(MemoryCache.Key(key))
        loader.diskCache?.remove(key)
    }
}
