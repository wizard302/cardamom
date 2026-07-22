package io.github.wizard302.cardamom.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.wizard302.cardamom.MainActivity
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.playback.PlaybackService

private const val ACTION_TOGGLE = "io.github.wizard302.cardamom.widget.TOGGLE"
private const val ACTION_NEXT = "io.github.wizard302.cardamom.widget.NEXT"
private const val ARTWORK_TARGET_PX = 256

/** Last known playback state, so the widget can be redrawn without a player. */
data class WidgetState(
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
)

/**
 * Home-screen widget: artwork, title/artist, play/pause and next.
 *
 * The provider runs in the app process, so [PlayerWidget.state] is enough to
 * redraw between updates pushed by PlaybackService. When the process has been
 * killed the widget falls back to a neutral "nothing playing" look until the
 * service comes back.
 */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = buildViews(context, state)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != ACTION_TOGGLE && action != ACTION_NEXT) return

        // Binder calls on one controller stay ordered, so the command is
        // delivered before the release that follows it.
        val pendingResult = goAsync()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    when (action) {
                        ACTION_TOGGLE -> if (controller.isPlaying) {
                            controller.pause()
                        } else {
                            controller.play()
                        }
                        ACTION_NEXT -> controller.seekToNextMediaItem()
                    }
                    controller.release()
                }
                pendingResult.finish()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    companion object {
        @Volatile
        var state: WidgetState = WidgetState()
            private set

        /** Called by PlaybackService whenever the visible state changes. */
        fun update(context: Context, player: Player) {
            val metadata = player.mediaMetadata
            state = WidgetState(
                title = metadata.title?.toString(),
                artist = metadata.artist?.toString(),
                artworkUri = metadata.artworkUri,
                isPlaying = player.isPlaying,
            )
            push(context)
        }

        private fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context, state)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context, state: WidgetState): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_player).apply {
                setTextViewText(
                    R.id.widget_title,
                    state.title ?: context.getString(R.string.widget_nothing_playing),
                )
                setTextViewText(R.id.widget_artist, state.artist.orEmpty())
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )

                val artwork = state.artworkUri?.let { loadArtwork(context, it) }
                if (artwork != null) {
                    setImageViewBitmap(R.id.widget_artwork, artwork)
                } else {
                    setImageViewResource(R.id.widget_artwork, R.mipmap.ic_launcher)
                }

                setOnClickPendingIntent(R.id.widget_play_pause, command(context, ACTION_TOGGLE))
                setOnClickPendingIntent(R.id.widget_next, command(context, ACTION_NEXT))
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
            }

        private fun command(context: Context, action: String): PendingIntent {
            val intent = Intent(context, PlayerWidget::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * Decodes album art in our own process: the launcher cannot read a
         * MediaStore album-art URI itself. Downsampled to roughly the widget's
         * thumbnail size, both to keep the decode cheap and to stay well under
         * the RemoteViews IPC limit.
         */
        private fun loadArtwork(context: Context, uri: Uri): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > ARTWORK_TARGET_PX) sample *= 2

            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }
        }.getOrNull()
    }
}
