package io.github.wizard302.cardamom.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore by preferencesDataStore(name = "playback_state")

/** Persists the playback queue and position so they survive app restarts. */
@Singleton
class QueueStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SavedQueue(
        val trackIds: List<Long>,
        val index: Int,
        val positionMs: Long,
    )

    private val idsKey = stringPreferencesKey("queue_ids")
    private val indexKey = intPreferencesKey("queue_index")
    private val positionKey = longPreferencesKey("queue_position")

    suspend fun save(trackIds: List<Long>, index: Int, positionMs: Long) {
        context.playbackDataStore.edit { prefs ->
            prefs[idsKey] = trackIds.joinToString(",")
            prefs[indexKey] = index
            prefs[positionKey] = positionMs
        }
    }

    suspend fun load(): SavedQueue? {
        val prefs = context.playbackDataStore.data.first()
        val ids = prefs[idsKey]
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        if (ids.isEmpty()) return null
        return SavedQueue(
            trackIds = ids,
            index = prefs[indexKey] ?: 0,
            positionMs = prefs[positionKey] ?: 0L,
        )
    }
}
