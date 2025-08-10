package com.jay.onlinetvradio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsTrackMetadataEntry
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.TrackSelectionArray
import androidx.media3.extractor.metadata.emsg.EventMessage
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.Id3Frame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.id3.UrlLinkFrame
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.ui.PlayerNotificationManager

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    // Create your player and media session in the onCreate lifecycle event

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val defaultFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
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


        player.addListener(object : Player.Listener {
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
            override fun onMetadata(eventTime: AnalyticsListener.EventTime, metadata: Metadata) {

                for (i in 0 until metadata.length()) {
                    val entry = metadata[i]
                    Log.d("Exodata","{$entry.toString()} {$i}")
                    if (entry is IcyInfo) {
                        val title = entry.title // "Artist - Song"
                        val url = entry.url // optional stream URL
                        Log.d("Exodata","Now playing: $title")
                        PlayerEvents.onMetadataUpdate?.invoke(entry.title)
                    }

                }
            }

            override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: Tracks) {
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        //val audioType = format.sampleMimeType ?: "Unknown"
                        //val codec = format.codecs ?: "Unknown"
                        val bitrate = if (format.bitrate != Format.NO_VALUE) format.bitrate / 1000 else 0
                        //val sampleRate = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 0
                        //val channels = if (format.channelCount != Format.NO_VALUE) format.channelCount else 0
                        if(bitrate>0) {
                            PlayerEvents.onBitrateUpdate?.invoke(bitrate)
                        }
                        /*Log.d(
                            "exodata",
                            "AudioType: $audioType, Codec: $codec, Bitrate: $bitrate, " +
                                    "SampleRate: $sampleRate, Channels: $channels"
                        )*/
                    }
                }
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

}