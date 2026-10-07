package io.github.wizard302.cardamom.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore by preferencesDataStore(name = "playback_state")

/**
 * Persists the playback queue, position and shuffle/repeat modes so they
 * survive app restarts.
 */
@Singleton
class QueueStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SavedQueue(
        val trackIds: List<Long>,
        val index: Int,
        val positionMs: Long,
        /** Shuffled play order as indices into [trackIds]; empty when not shuffled. */
        val shuffleOrder: List<Int>,
    )

    data class SavedModes(
        val shuffleEnabled: Boolean,
        val repeatMode: Int,
    )

    private val idsKey = stringPreferencesKey("queue_ids")
    private val indexKey = intPreferencesKey("queue_index")
    private val positionKey = longPreferencesKey("queue_position")
    private val shuffleOrderKey = stringPreferencesKey("queue_shuffle_order")
    private val shuffleKey = booleanPreferencesKey("shuffle_enabled")
    private val repeatKey = intPreferencesKey("repeat_mode")

    suspend fun save(
        trackIds: List<Long>,
        index: Int,
        positionMs: Long,
        shuffleOrder: List<Int>,
    ) {
        context.playbackDataStore.edit { prefs ->
            prefs[idsKey] = trackIds.joinToString(",")
            prefs[indexKey] = index
            prefs[positionKey] = positionMs
            prefs[shuffleOrderKey] = shuffleOrder.joinToString(",")
        }
    }

    suspend fun load(): SavedQueue? {
        val prefs = context.playbackDataStore.data.first()
        val ids = prefs[idsKey].toLongList()
        if (ids.isEmpty()) return null
        return SavedQueue(
            trackIds = ids,
            index = prefs[indexKey] ?: 0,
            positionMs = prefs[positionKey] ?: 0L,
            shuffleOrder = prefs[shuffleOrderKey].toLongList().map { it.toInt() },
        )
    }

    suspend fun saveModes(shuffleEnabled: Boolean, repeatMode: Int) {
        context.playbackDataStore.edit { prefs ->
            prefs[shuffleKey] = shuffleEnabled
            prefs[repeatKey] = repeatMode
        }
    }

    suspend fun loadModes(): SavedModes {
        val prefs = context.playbackDataStore.data.first()
        return SavedModes(
            shuffleEnabled = prefs[shuffleKey] ?: false,
            repeatMode = prefs[repeatKey] ?: Player.REPEAT_MODE_OFF,
        )
    }

    private fun String?.toLongList(): List<Long> =
        this?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
}
