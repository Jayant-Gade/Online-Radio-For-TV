package com.jay.onlinetvradio

object PlayerEvents {
    var onQualityUpdate: ((codec: String?,bitrate: Int,channel: Int) -> Unit)? = null
    var onPlayerError:((error: androidx.media3.common.PlaybackException) -> Unit)? = null
    var onPlayingUpdate:((isPlaying : Boolean) -> Unit)? = null
    var onBitrateUpdate:((bitrate: Int) -> Unit)? = null
    var onMetadataUpdate:((entry : String?) -> Unit)? = null
}