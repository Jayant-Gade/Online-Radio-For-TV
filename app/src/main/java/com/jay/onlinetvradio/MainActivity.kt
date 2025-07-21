package com.jay.onlinetvradio

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread
import android.provider.MediaStore
import android.content.ContentValues
import android.content.ContentUris
import android.net.Uri
import android.view.View
import androidx.media3.session.MediaSession
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.ui.PlayerNotificationManager
import android.app.NotificationChannel
import android.app.NotificationManager





class MainActivity : AppCompatActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var radioName: TextView
    private lateinit var logText: TextView
    private lateinit var radioIcon: ImageView
    private lateinit var mediaSession: MediaSession



    private lateinit var playerNotificationManager: PlayerNotificationManager
    private val okHttpClient = OkHttpClient()

    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout

    private lateinit var row_s: LinearLayout
    private val dynamicRows = mutableListOf<LinearLayout>()

    private var currentStreamName: String? = null



    private lateinit var playbackStatusGif: ImageView

    var apiServer: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playbackStatusGif = findViewById(R.id.playbackStatusGif)

        radioName = findViewById(R.id.radioName)
        logText = findViewById(R.id.logText)
        radioIcon = findViewById(R.id.radioIcon)

        row1 = findViewById(R.id.row1)
        row2 = findViewById(R.id.row2)
        row_s = findViewById(R.id.row_s)
        dynamicRows.add(findViewById(R.id.row3))
        dynamicRows.add(findViewById(R.id.row4))
        dynamicRows.add(findViewById(R.id.row5))
        dynamicRows.add(findViewById(R.id.row6))
        loadDynamicStations()

        exoPlayer = ExoPlayer.Builder(this).build()

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
        initApiServer()


        val channelId = "radio_playback_channel"

        val channel = NotificationChannel(
            channelId,
            "Radio Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            1234,
            channelId
        ).build().apply {
            setPlayer(exoPlayer)
            setMediaSessionToken(mediaSession.sessionCompatToken) // ✅ <-- use this!
            setSmallIcon(R.mipmap.ic_launcher)
            setUseRewindAction(false)
            setUseFastForwardAction(false)
            setUsePreviousAction(false)
            setUseNextAction(false)
            setUsePlayPauseActions(true)
        }

        // Add fixed stations directly:
        addStationButton("Radio City","https://www.radiocity.in/rc-new/images/RC-logonew.png", "https://stream-60.zeno.fm/pxc55r5uyc9uv?zs=xkD7f1ttQe20opARKqWXuA", row1)
        addStationButton("Mirchi Love","https://liveradios.in/wp-content/uploads/mirchilove-1.jpg", "https://2.mystreaming.net/uber/lrbollywood/icecast.audio", row1)
        addStationButton("Big FM", "https://upload.wikimedia.org/wikipedia/commons/7/74/BIGFM_NEW_LOGO_2019.png","https://listen.openstream.co/4434/audio", row1)
        addStationButton("Red FM", "https://api.redfmindia.in/filesvc/v1/file/01939efd-c535-444b-a928-88b0a0cabcd3/content","https://stream.zeno.fm/9phrkb1e3v8uv", row1)
        addStationButton("Fever 104 FM","https://onlineradiohub.com/wp-content/uploads/2023/08/fever-fm-107_3.jpg","https://radio.canstream.co.uk:8115/live.mp3",row1)
        addStationButton("Radio Mirchi", "https://upload.wikimedia.org/wikipedia/en/a/a7/Radiomirchi.jpg","https://eu8.fastcast4u.com/proxy/clyedupq/stream", row2)
        addStationButton("Vividh Bharati-s1","https://indiaradio.in/wp-content/uploads/2024/01/vividh-bharati.jpg", "https://air.pc.cdn.bitgravity.com/air/live/pbaudio001/playlist.m3u8", row2)
        addStationButton("Vividh Bharati-s2", "https://indiaradio.in/wp-content/uploads/2024/01/vividh-bharati.jpg","https://air.pc.cdn.bitgravity.com/air/live/pbaudio070/playlist.m3u8", row2)
        addStationButton("AIR FM Rainbow","https://onlineradiofm.in/assets/image/radio/180/all-india-air.webp","https://airhlspush.pc.cdn.bitgravity.com/httppush/hlspbaudio004/hlspbaudio00464kbps.m3u8",row2)
        addStationButton("AIR FM Gold","https://onlineradiofm.in/assets/image/radio/180/fmgold.webp","https://airhlspush.pc.cdn.bitgravity.com/httppush/hlspbaudio005/hlspbaudio005_Auto.m3u8",row2)

        // Add search button in row_s
        val searchButtonView = layoutInflater.inflate(R.layout.item_station_button, row_s, false)
        val searchText = searchButtonView.findViewById<TextView>(R.id.stationName)
        val searchIcon = searchButtonView.findViewById<ImageView>(R.id.stationIcon)

        searchText.text = "Search"
        searchIcon.setImageResource(android.R.drawable.ic_menu_search) // or your custom icon

        searchButtonView.setOnClickListener { openSearchDialog() }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }

        row_s.addView(searchButtonView, params)

        // Example: dynamically get BigFM
        getStationByQuery("bigfm") { station ->
            station?.let {
                val name = it.getString("name")
                val link = it.getString("url_resolved")
                val iconUrl = it.optString("favicon", null)
                addStationButton(name, iconUrl, link, row1, it)
            }
        }
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackGif(isPlaying)
            }
        })

    }

    private fun initApiServer() {
        val servers = listOf("de1.api.radio-browser.info", "fi1.api.radio-browser.info",
            "fr1.api.radio-browser.info", "nl1.api.radio-browser.info")
        thread {
            for (server in servers) {
                try {
                    val url = "https://$server/json/stats"
                    val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                    if (response.isSuccessful) {
                        apiServer = server
                        log("Using API: $server")
                        break
                    }
                } catch (_: Exception) { }
            }
            if (apiServer == null) log("All servers failed")
        }
    }
    private fun pendingIntentToOpenApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun addStationButton(name: String, iconUrl: String?, link: String, parent: LinearLayout, meta: JSONObject? = null) {
        val view = layoutInflater.inflate(R.layout.item_station_button, parent, false)
        val nameText = view.findViewById<TextView>(R.id.stationName)
        val iconView = view.findViewById<ImageView>(R.id.stationIcon)

        nameText.text = name
        if (!iconUrl.isNullOrEmpty()) {
            Glide.with(this).load(iconUrl).into(iconView)
        } else {
            iconView.setImageResource(R.mipmap.ic_launcher)
        }

        view.setOnClickListener { playStationDirect(name, iconUrl, link) }
        view.setOnLongClickListener {
            meta?.let { showContextMenu(view, name, link, meta) }
            true
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 4; marginEnd = 4
        }
        runOnUiThread { parent.addView(view, params) }
    }




    private fun addDynamicStation(name: String, iconUrl: String?, link: String, meta: JSONObject, skipDuplicateCheck: Boolean = false) {
        if (!skipDuplicateCheck) {
            val prefs = getSharedPreferences("dynamic_stations", MODE_PRIVATE)
            val arr = JSONArray(prefs.getString("stations", "[]"))

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("link") == link) {
                    log("Station already added: $name")
                    return
                }
            }
        }

        val view = layoutInflater.inflate(R.layout.item_station_button, null)
        val nameText = view.findViewById<TextView>(R.id.stationName)
        val iconView = view.findViewById<ImageView>(R.id.stationIcon)

        nameText.text = name
        if (!iconUrl.isNullOrEmpty()) {
            Glide.with(this).load(iconUrl).into(iconView)
        } else {
            iconView.setImageResource(R.mipmap.ic_launcher)
        }

        view.setOnClickListener { playStationDirect(name, iconUrl, link) }
        view.setOnLongClickListener {
            showContextMenu(view, name, link, meta)
            true
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 4; marginEnd = 4
        }

        for (row in dynamicRows) {
            if (row.childCount < 3) {
                runOnUiThread { row.addView(view, params) }
                break
            }
        }

        if (!skipDuplicateCheck) {
            saveDynamicStation(name, iconUrl, link, meta)
        }
    }




    private fun saveDynamicStation(name: String, iconUrl: String?, link: String, meta: JSONObject) {
        val prefs = getSharedPreferences("dynamic_stations", MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("stations", "[]"))
        val obj = JSONObject().apply {
            put("name", name)
            put("iconUrl", iconUrl ?: "")
            put("link", link)
            put("meta", meta)
        }
        arr.put(obj)
        prefs.edit().putString("stations", arr.toString()).apply()
    }


    private fun loadDynamicStations() {
        val prefs = getSharedPreferences("dynamic_stations", MODE_PRIVATE)
        val arrStr = prefs.getString("stations", null) ?: return
        val arr = JSONArray(arrStr)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.getString("name")
            val iconUrl = obj.optString("iconUrl", null)
            val link = obj.getString("link")
            val meta = obj.getJSONObject("meta")
            addDynamicStation(name, iconUrl, link, meta, skipDuplicateCheck = true)
        }
    }


    private fun removeDynamicStation(link: String) {
        val prefs = getSharedPreferences("dynamic_stations", MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("stations", "[]"))
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("link") != link) {
                newArr.put(obj)
            }
        }
        prefs.edit().putString("stations", newArr.toString()).apply()
        log("Removed station with link: $link")
    }


    private fun playStationDirect(name: String, iconUrl: String?, streamUrl: String) {
        if (currentStreamName == name && exoPlayer.isPlaying) {
            // Same station clicked & already playing → pause
            exoPlayer.pause()

            log("Paused: $name")
            return
        }
        else {
            log("Playing: $name")
            try {
                exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                exoPlayer.setMediaItem(
                    MediaItem.Builder()
                        .setUri(streamUrl)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(name)
                                .setArtworkUri(iconUrl?.let { Uri.parse(it) })
                                .build()
                        )
                        .build()
                )
                currentStreamName = name
                exoPlayer.prepare()
                exoPlayer.play()

                radioName.text = name
                if (!iconUrl.isNullOrEmpty()) {
                    Glide.with(this).load(iconUrl).into(radioIcon)
                } else {
                    radioIcon.setImageResource(R.mipmap.ic_launcher)
                }

                exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        log("Error: ${error.message}")
                        radioName.text = "Error"
                        radioIcon.setImageResource(android.R.drawable.ic_delete)
                    }
                })
            } catch (e: Exception) {
                log("Error: ${e.message}")
                radioName.text = "Error"
                radioIcon.setImageResource(android.R.drawable.ic_delete)
            }
        }
    }


    private fun getStationByQuery(query: String, callback: (station: JSONObject?) -> Unit) {
        thread {
            try {
                val server = apiServer ?: return@thread
                val url = "https://$server/json/stations/search?limit=30&name=$query&hidebroken=true&order=clickcount&reverse=true"
                val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    callback(null); return@thread
                }
                val arr = JSONArray(body)
                if (arr.length() == 0) { callback(null); return@thread }
                callback(arr.getJSONObject(0))
            } catch (e: Exception) { log("Error: ${e.message}"); callback(null) }
        }
    }

    private fun openSearchDialog() {
        supportFragmentManager.commit {
            add(SearchDialogFragment.newInstance { station ->
                val name = station.getString("name")
                val link = station.getString("url_resolved")
                val iconUrl = station.optString("favicon", null)
                addDynamicStation(name, iconUrl, link, station)
            }, "SearchDialog")
        }
    }


    private fun showContextMenu(anchor: View, name: String, link: String, meta: JSONObject) {
        val iconUrl = meta.optString("favicon", null)
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Play").setOnMenuItemClickListener {
            playStationDirect(name, iconUrl, link)
            true
        }
        popup.menu.add("Delete").setOnMenuItemClickListener {
            (anchor.parent as? LinearLayout)?.removeView(anchor)
            removeDynamicStation(link)
            true
        }
        popup.menu.add("Info").setOnMenuItemClickListener {
            showInfoDialog(meta)
            true
        }
        popup.show()
    }



    private fun showInfoDialog(meta: JSONObject) {
        supportFragmentManager.commit {
            add(InfoDialogFragment.newInstance(meta), "InfoDialog")
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            logText.text = msg
        }
    }

    private fun updatePlaybackGif(isPlaying: Boolean) {
        val gifRes = if (isPlaying) R.drawable.playing else R.drawable.not_playing
        Glide.with(this).asGif().load(gifRes).into(playbackStatusGif)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerNotificationManager.setPlayer(null)
        mediaSession.release()
        exoPlayer.release()
    }
}
