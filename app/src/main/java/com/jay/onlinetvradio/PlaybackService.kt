package com.jay.onlinetvradio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.ui.PlayerNotificationManager

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var playerNotificationManager: PlayerNotificationManager

    // Create your player and media session in the onCreate lifecycle event

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val defaultFactory = DefaultHttpDataSource.Factory()
        val dataSourceFactory = SafeHttpDataSourceFactory(
            context = this,
            defaultFactory,
            onHttpBlocked = {
                //runOnUiThread {                }
            })
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlayerEvents.onPlayingUpdate?.invoke(isPlaying)
                //updatePlaybackGif(isPlaying)
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                val codec = format.sampleMimeType?.substringBefore("-")?.substringAfter("/")
                val bitrate = format.bitrate / 1000
                val channel = format.channelCount

                PlayerEvents.onQualityUpdate?.invoke(codec,bitrate,channel)
            }
        })

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                PlayerEvents.onPlayerError?.invoke(error)
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        player.prepare()

        val notification = NotificationCompat.Builder(this, "radio_playback_channel")
            .setContentTitle("Track Title")
            .setContentText("Artist Name") // optional
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                mediaSession?.let { MediaStyleNotificationHelper.MediaStyle(it) }
                    ?.setShowActionsInCompactView(1) // play/pause
            )
            //.addAction(R.drawable.ic_pause, "Pause", pauseIntent)
            .build()

        startForeground(1234, notification)

    }


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession


    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            //mediaSession = null
        }
        super.onDestroy()
    }
    private fun isMediaPlaying(){
        player.isPlaying
    }
    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }

    }

    @UnstableApi
    private inner class DescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            return player.mediaMetadata.title ?: "No Title"
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? = null

        override fun getCurrentContentText(player: Player): CharSequence? {
            return player.mediaMetadata.artist
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? = null
    }
}