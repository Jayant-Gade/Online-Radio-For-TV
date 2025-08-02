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
import android.net.Uri
import android.view.View
import android.view.animation.AnimationUtils
import androidx.media3.session.MediaSession
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.ui.PlayerNotificationManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.core.content.edit
import kotlin.text.substringBefore
import androidx.core.content.ContextCompat
import androidx.media3.common.Format
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.common.MimeTypes
import android.content.Context
import android.media.AudioManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.preference.PreferenceManager
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var radioName: TextView
    private lateinit var qualityInfo: TextView
    private lateinit var logText: TextView
    private lateinit var radioIcon: ImageView
    private lateinit var mediaSession: MediaSession

    @androidx.media3.common.util.UnstableApi

    private lateinit var playerNotificationManager: PlayerNotificationManager
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val enableHttp = prefs.getBoolean("enable_http_other", false)
            val url = chain.request().url
            if (!enableHttp && url.scheme.equals("http", ignoreCase = true)) {
                throw IOException("HTTP traffic disabled by user")
            }
            chain.proceed(chain.request())
        }
        .build()
    private var animatingView: View? = null

    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout

    private lateinit var parentContainer: LinearLayout
    private lateinit var row_s: LinearLayout
    private val dynamicRows = mutableListOf<LinearLayout>()

    private var currentStreamName: String? = null


    private lateinit var playbackStatusGif: ImageView

    var apiServer: String? = null

    @androidx.media3.common.util.UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)


        playbackStatusGif = findViewById(R.id.playbackStatusGif)

        radioName = findViewById(R.id.radioName)
        qualityInfo = findViewById(R.id.qualityInfo)
        logText = findViewById(R.id.logText)
        radioIcon = findViewById(R.id.radioIcon)

        row1 = findViewById(R.id.row1)
        row2 = findViewById(R.id.row2)
        row_s = findViewById(R.id.row_s)

        parentContainer = findViewById(R.id.parentContainer)
        addNewRow()
        loadDynamicStations()

        // Hide the status bar.
        //window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        //the action bar is hidden
        //actionBar?.hide()

        val defaultFactory = DefaultHttpDataSource.Factory()
        val dataSourceFactory = SafeHttpDataSourceFactory(
            context = this,
            defaultFactory,
            onHttpBlocked = {
                runOnUiThread {
                    qualityInfo.text = "HTTP playback blocked by settings"
                }
            })

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

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
        //adding servers
        saveServerList(
            "Vividh Bharati",
            listOf(
                Triple(
                    "https://air.pc.cdn.bitgravity.com/air/live/pbaudio001/playlist.m3u8",
                    "Hindi",
                    "All India"
                ),
                Triple(
                    "https://air.pc.cdn.bitgravity.com/air/live/pbaudio070/playlist.m3u8",
                    "Marathi",
                    "Nagpur"
                ),
                Triple(
                    "https://airhlspush.pc.cdn.bitgravity.com/httppush/hlspbaudio238/hlspbaudio238_Auto.m3u8",
                    "Hindi",
                    "Delhi"
                ),
                Triple(
                    "\thttps://airhlspush.pc.cdn.bitgravity.com/httppush/hlspbaudio011/hlspbaudio011_Auto.m3u8",
                    "Marathi",
                    "Mumbai"
                )
            )
        )

        //add update states on first run to update states and languages
        val prefs = getSharedPreferences("stations_prefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        if (isFirstRun) {
            Log.d(
                "FirstRun", "entering first run!"
            )

            updateStationStateAndLanguageInPrefs("Vividh Bharati-1+r2", "All India", "Hindi")
            updateStationStateAndLanguageInPrefs("Vividh Bharati-2+r2", "Nagpur", "Marathi")
        } else {
            Log.d("FirstRun", "No server list found for ")
        }

        // finally, set flag so it won't run again
        prefs.edit {
            putBoolean("is_first_run", false)
        }
        Log.d("FirstRun", "lol adding buttons")

        // Add fixed stations directly:
        //addStationButton(name->"name-number(if 2 same)+rownumber",iconurl,streamurl,rownumber)
        addStationButton(
            "Radio City Freedom-+r1",
            "https://onlineradiofm.in/assets/image/radio/180/PlanetRadioCity-Freedom.png",
            "https://stream-140.zeno.fm/d6f5w51zrf9uv",
            row1
        )
        addStationButton(
            "Mirchi Love-+r1",
            "https://liveradios.in/wp-content/uploads/mirchilove-1.jpg",
            "https://2.mystreaming.net/uber/lrbollywood/icecast.audio",
            row1
        )
        addStationButton(
            "Big FM-+r1",
            "https://upload.wikimedia.org/wikipedia/commons/7/74/BIGFM_NEW_LOGO_2019.png",
            "https://listen.openstream.co/4434/audio",
            row1
        )
        addStationButton(
            "Red FM-+r1",
            "https://api.redfmindia.in/filesvc/v1/file/01939efd-c535-444b-a928-88b0a0cabcd3/content",
            "https://stream.zeno.fm/9phrkb1e3v8uv",
            row1
        )
        addStationButton(
            "Fever 104 FM-+r1",
            "https://onlineradiohub.com/wp-content/uploads/2023/08/fever-fm-107_3.jpg",
            "https://radio.canstream.co.uk:8115/live.mp3",
            row1
        )
        addStationButton(
            "Radio Mirchi-+r2",
            "https://upload.wikimedia.org/wikipedia/en/a/a7/Radiomirchi.jpg",
            "https://eu8.fastcast4u.com/proxy/clyedupq/stream",
            row2
        )
        addStationButton(
            "Vividh Bharati-1+r2",
            "https://indiaradio.in/wp-content/uploads/2024/01/vividh-bharati.jpg",
            "https://air.pc.cdn.bitgravity.com/air/live/pbaudio001/playlist.m3u8",
            row2
        )
        addStationButton(
            "Vividh Bharati-2+r2",
            "https://indiaradio.in/wp-content/uploads/2024/01/vividh-bharati.jpg",
            "https://air.pc.cdn.bitgravity.com/air/live/pbaudio070/playlist.m3u8",
            row2
        )
        addStationButton(
            "AIR FM Rainbow-+r2",
            "https://onlineradiofm.in/assets/image/radio/180/all-india-air.webp",
            "https://airhlspush.pc.cdn.bitgravity.com/httppush/hlspbaudio004/hlspbaudio00464kbps.m3u8",
            row2
        )
        addStationButton(
            "AIR FM Gold-+r2",
            "https://onlineradiofm.in/assets/image/radio/180/fmgold.webp",
            "https://airhlspush.pc.cdn.bitgravity.com/httppush/hlspbaudio005/hlspbaudio005_Auto.m3u8",
            row2
        )
        //Log.d("MyApp","helloooo")
        // Add search button in row_s
        val searchButtonView = layoutInflater.inflate(R.layout.item_station_button, row_s, false)
        val settingButtonView = layoutInflater.inflate(R.layout.item_station_button, row_s, false)
        val searchText = searchButtonView.findViewById<TextView>(R.id.stationName)
        val searchIcon = searchButtonView.findViewById<ImageView>(R.id.stationIcon)
        val settingText = settingButtonView.findViewById<TextView>(R.id.stationName)
        val settingIcon = settingButtonView.findViewById<ImageView>(R.id.stationIcon)

        val paramssearch = searchButtonView.layoutParams
        val scale = searchButtonView.context.resources.displayMetrics.density
        paramssearch.width = (400 * scale + 0.5f).toInt() // 200dp
        paramssearch.height = (100 * scale + 0.5f).toInt() // 100dp
        searchButtonView.layoutParams = paramssearch
        searchText.setText(R.string.search)
        searchIcon.setImageResource(R.drawable.search_icon) // or your custom icon
        searchButtonView.setOnClickListener { openSearchDialog() }
        val settingsDialog = SettingsDialogFragment()

        settingText.text = "Setting"
        settingIcon.setImageResource(android.R.drawable.ic_menu_preferences)
        settingButtonView.setOnClickListener {
            settingsDialog.show(supportFragmentManager, "settings")

        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }

        row_s.addView(searchButtonView, params)
        row_s.addView(settingButtonView,params)

        // Example: dynamically get BigFM
        /*getStationByQuery("bigfm") { station ->
            station?.let {
                val name = it.getString("name")
                val link = it.getString("url_resolved")
                val iconUrl = it.optString("favicon", null)
                addStationButton(name, iconUrl, link, row1, it)
            }
        }*/
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackGif(isPlaying)
            }

        })


        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format
            ) {
                qualityInfo.text=""
                qualityInfo.append("Codec:${format.sampleMimeType?.substringBefore("-")?.substringAfter("/")} | ")
                qualityInfo.append("Bitrate:${format.bitrate / 1000}kbps | ")
                if (format.channelCount==1) {
                    qualityInfo.append("Channels:${format.channelCount} (Mono)")
                }
                else if (format.channelCount==2) {
                    qualityInfo.append("Channels:${format.channelCount} (Stereo)")
                }
                else {
                    qualityInfo.append("Channels:${format.channelCount}")
                }//debug area
                Log.d("ExoPlayer", "Audio bitrate: ${format.bitrate / 1000} kbps")
                Log.d("ExoPlayer", "Codec: ${format.sampleMimeType}")
                Log.d("ExoPlayer", "Channels: ${format.channelCount}")
            }
        })

    }

    private fun addNewRow() {
        val newRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8) // optional margins between rows
            }
        }
        dynamicRows.add(newRow)
        parentContainer.addView(newRow)
    }

    fun updateStationStateAndLanguageInPrefs(
        exactStationName: String,
        state: String?,
        language: String?
    ) {
        val prefs = getSharedPreferences("stations_prefs", MODE_PRIVATE)
        prefs.edit {
            putString("${exactStationName}_selected_language", language)
            putString("${exactStationName}_selected_state", state)
        }
        Log.d(
            "FirstRun",
            "Saved for $exactStationName: state=$state, language=$language"
        )
    }

    private fun initApiServer() {
        val servers = listOf(
            "de1.api.radio-browser.info", "fi1.api.radio-browser.info",
            "fr1.api.radio-browser.info", "nl1.api.radio-browser.info"
        )
        thread {
            for (server in servers) {
                try {
                    val url = "https://$server/json/stats"
                    val response =
                        okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                    if (response.isSuccessful) {
                        apiServer = server
                        log("Using API: $server")
                        break
                    }
                } catch (_: Exception) {
                }
            }
            if (apiServer == null) log("All servers failed")
        }
    }

    private fun playButtonAnimation(view: View) {
        var glowView: View? = null
        glowView = animatingView?.findViewById<View>(R.id.stationButtonBack)
        glowView?.clearAnimation()
        glowView?.visibility = View.INVISIBLE
        val fadeAnim = AnimationUtils.loadAnimation(view.context, R.anim.fade_in_out)
        glowView = view.findViewById<View>(R.id.stationButtonBack)
        glowView.startAnimation(fadeAnim)
        glowView.visibility = View.VISIBLE
        animatingView = view


    }

    private fun addStationButton(
        name: String,
        iconUrl: String?,
        link: String,
        parent: LinearLayout
    ) {
//      name-number(if same 2)+rownumber
        val prefs = getSharedPreferences("stations_prefs", MODE_PRIVATE)
        val meta = JSONObject()
        meta.put("name", name.substringBefore("+"))
        meta.put("countrycode", "India")
        meta.put("tags", "Indian Music")
        meta.put("favicon", iconUrl)
        val savedLanguage = prefs.getString("${name}_selected_language", null)
        if (savedLanguage == null) {
            meta.put("languagecodes", "Hindi")
        } else {
            meta.put("languagecodes", savedLanguage)
        }


        val view = layoutInflater.inflate(R.layout.item_station_button, parent, false)
        val nameText = view.findViewById<TextView>(R.id.stationName)
        val iconView = view.findViewById<ImageView>(R.id.stationIcon)
        view.tag = meta

        val selectedState = prefs.getString("${name}_selected_state", null)
        nameText.text = buildString {
            append(name.substringBefore("+").replace("-", " "))
            if (!selectedState.isNullOrEmpty()) {
                append("\n$selectedState")
            }
        }

        if (!iconUrl.isNullOrEmpty()) {
            Glide.with(this).load(iconUrl).into(iconView)
        } else {
            iconView.setImageResource(R.mipmap.ic_launcher)
        }
        view.setOnClickListener {
            playButtonAnimation(view.findViewById<View>(R.id.stationButtonBack))

            val prefs = getSharedPreferences("stations_prefs", MODE_PRIVATE)
            val savedLink = prefs.getString("${name}_selected_link", null)


            // fallback order:
            // 1. saved link (from prefs)
            // ##SKIPPED link saved in meta.tag (e.g., meta.optString("server"))
            // 2. original default link passed to function

            //val meta = view.tag as? JSONObject
            //val metaLink = meta?.optString("server")
            val finalLink = savedLink ?: link
            Log.d("MyApp", "Saved link: $savedLink, metaLink: unused, default: $link")

            playStationDirect(name.substringBefore("+").replace("-", " "), iconUrl, finalLink)


        }
        view.setOnLongClickListener {
            showContextMenudef(view, name, link, meta)
            true
        }

        val params =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 4; marginEnd = 4
            }
        runOnUiThread { parent.addView(view, params) }
    }

    private fun saveServerList(
        stationKey: String,
        servers: List<Triple<String, String, String>> // Pair<url, language>
    ) {
        val prefs = getSharedPreferences("stations_prefs", MODE_PRIVATE)
        val jsonArray = JSONArray().apply {
            servers.forEach { (url, language, name) ->
                put(JSONObject().apply {
                    put("url", url)
                    put("language", language)
                    put("state", name)
                    // you can also add "icon", etc.
                })
            }
        }
        prefs.edit {
            putString("${stationKey}_servers", jsonArray.toString())
        }
    }


    private fun addDynamicStation(
        name: String,
        iconUrl: String?,
        link: String,
        meta: JSONObject,
        skipDuplicateCheck: Boolean = false
    ) {
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

        view.setOnClickListener {
            playButtonAnimation(view.findViewById<View>(R.id.stationButtonBack))
            playStationDirect(name, iconUrl, link)
        }
        view.setOnLongClickListener {
            showContextMenu(view, name, link, meta)
            true
        }
        val params =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 4; marginEnd = 4
            }
        /*
        for (row in dynamicRows) {
            if (row.childCount < 3) {
                runOnUiThread { row.addView(view, params) }
                break
            }
        }*/
        for (row in dynamicRows) {
            if (row.childCount < 3) {
                runOnUiThread { row.addView(view, params) }
                return
            }
        }
        addNewRow()
        runOnUiThread { dynamicRows.last().addView(view, params) }
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
        prefs.edit {
            putString("stations", arr.toString())
        }
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
        prefs.edit {
            putString("stations", newArr.toString())
        }
        log("Removed station with link: $link")
    }


    private fun playStationDirect(name: String, iconUrl: String?, streamUrl: String) {
        if (currentStreamName == name && exoPlayer.isPlaying) {
            // Same station clicked & already playing → pause
            exoPlayer.pause()
            animatingView?.clearAnimation()
            log("Paused: ${name.substringBefore("+")}")
            return
        } else {
            //debug area
            //logAvailableDecoders()

            //debug end
            qualityInfo.text="Loading..."
            log("Playing: ${name.substringBefore("+")}")
            try {
                exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                exoPlayer.setMediaItem(
                    MediaItem.Builder()
                        .setUri(streamUrl)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(name.substringBefore("+"))
                                .setArtworkUri(iconUrl?.run(Uri::parse))
                                .build()
                        )
                        .build()
                )
                Log.d("MyApp", "playing $streamUrl")
                currentStreamName = name
                exoPlayer.prepare()
                exoPlayer.play()

                radioName.text = name.substringBefore("+")
                if (!iconUrl.isNullOrEmpty()) {
                    Glide.with(this).load(iconUrl).into(radioIcon)
                } else {
                    radioIcon.setImageResource(R.mipmap.ic_launcher)
                }

                exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        log("Error: ${error.message}")
                        animatingView?.clearAnimation()
                        radioName.setText(R.string.error)
                        radioIcon.setImageResource(android.R.drawable.ic_delete)
                    }
                })
            } catch (e: Exception) {
                log("Error: ${e.message}")
                radioName.setText(R.string.error)
                radioIcon.setImageResource(android.R.drawable.ic_delete)
            }
        }
    }


    private fun getStationByQuery(query: String, callback: (station: JSONObject?) -> Unit) {
        thread {
            try {
                val server = apiServer ?: return@thread
                val url =
                    "https://$server/json/stations/search?limit=30&name=$query&hidebroken=true&order=clickcount&reverse=true"
                val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    callback(null); return@thread
                }
                val arr = JSONArray(body)
                if (arr.length() == 0) {
                    callback(null); return@thread
                }
                callback(arr.getJSONObject(0))
            } catch (e: Exception) {
                log("Error: ${e.message}"); callback(null)
            }
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
            playButtonAnimation(anchor.findViewById<View>(R.id.stationButtonBack))
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

    private fun showContextMenudef(anchor: View, name: String, link: String, meta: JSONObject) {
        val iconUrl = meta.optString("favicon", null)
        val popup = PopupMenu(this, anchor)
        val parentRow = anchor.parent as LinearLayout
        popup.menu.add("Play").setOnMenuItemClickListener {
            playButtonAnimation(anchor.findViewById<View>(R.id.stationButtonBack))
            playStationDirect(name.substringBefore("+").replace("-", " "), iconUrl, link)
            true
        }
        val prefs = getSharedPreferences("stations_prefs", MODE_PRIVATE)
        val serversJson = prefs.getString("${name.substringBefore("-")}_servers", null)

        /*
        I have used common naming to find server list like "Vividh Bharati" and not "Vividh Bharati-1"
        This can be changed by changing subsequencebefore from - to + for exact in serversjson in ServerChangeFragment.kt
        */
        if (serversJson != null) {
            popup.menu.add("Change Server").setOnMenuItemClickListener {
                showServerDialog(name, parentRow)
                true
            }
        }
        popup.menu.add("Info").setOnMenuItemClickListener {
            showInfoDialog(meta)
            true
        }
        popup.show()
    }

    fun showServerDialog(stationKey: String, parentRow: LinearLayout) {
        val fragment = ServerChangeFragment(stationKey, parentRow)
        fragment.show(supportFragmentManager, "ServerChangeFragment")
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

    @androidx.media3.common.util.UnstableApi
    override fun onDestroy() {
        super.onDestroy()
        playerNotificationManager.setPlayer(null)
        mediaSession.release()
        exoPlayer.release()
    }


    @UnstableApi
    fun logAvailableDecoders() {
        val mimeTypesToCheck = listOf(
            MimeTypes.AUDIO_E_AC3_JOC, // Dolby Atmos over DD+
            MimeTypes.AUDIO_E_AC3,     // Dolby Digital Plus
            MimeTypes.AUDIO_AC3,       // Dolby Digital
            MimeTypes.AUDIO_AAC,       // AAC (LC)
            MimeTypes.AUDIO_MPEG       // MP3
        )
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        for (device in devices) {
            Log.d("Audio", "Device: ${device.productName}, type=${device.type}, channels=${device.channelCounts.joinToString()}")
        }

        for (mime in mimeTypesToCheck) {
            try {
                val decoderInfos: List<MediaCodecInfo> = MediaCodecUtil.getDecoderInfos(mime, /* secure= */ false, /* tunneling= */ false)
                Log.d("ExoPlayer", "MimeType: $mime, decoders found: ${decoderInfos.size}")
                decoderInfos.forEach { decoderInfo ->
                    Log.d("ExoPlayer", "  decoder name=${decoderInfo.name}, hardwareAccelerated=${decoderInfo.hardwareAccelerated}")
                }
            } catch (e: Exception) {
                Log.w("ExoPlayer", "Error checking decoders for mimeType=$mime", e)
            }
        }
    }
}
