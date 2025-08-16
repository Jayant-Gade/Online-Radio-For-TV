package com.jay.onlinetvradio

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {


    private lateinit var radioName: TextView
    private lateinit var radioTitle: TextView
    private lateinit var radioArtist: TextView
    private lateinit var qualityCodec: TextView
    private lateinit var qualityBitrate: TextView
    private lateinit var qualityChannel: TextView
    private lateinit var logText: TextView
    private lateinit var radioIcon: ImageView
    private lateinit var mediaSession: MediaSession


    private val db = DBHelper(this, null)

    @UnstableApi

    lateinit var mediaController: MediaController

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
    private var currentStreamURL: String? = null

/*
    private lateinit var playbackStatusImage1: ImageView
    private lateinit var playbackStatusImage2: ImageView*/
    private lateinit var playbackStatusView: FrameLayout
    private lateinit var playbackStatusAnim : AnimatorSet
/*
    lateinit var animator: ObjectAnimator
    lateinit var animator2: ObjectAnimator*/

    var apiServer: String? = null

    @SuppressLint("UseKtx")
    @androidx.media3.common.util.UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val playbackStatusImage1 = findViewById<ImageView>(R.id.playbackStatusImage1)
        val playbackStatusImage2 = findViewById<ImageView>(R.id.playbackStatusImage2)
        playbackStatusView = findViewById<FrameLayout>(R.id.playbackStatusView)
        val distancePx = 90 * resources.displayMetrics.density
        val animator1 = ObjectAnimator.ofFloat(playbackStatusImage1, "translationX", -distancePx, 0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            repeatMode = ValueAnimator.RESTART
        }
        val animator2 = ObjectAnimator.ofFloat(playbackStatusImage2, "translationX", 0f, distancePx).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            repeatMode = ValueAnimator.RESTART
        }
        playbackStatusAnim = AnimatorSet().apply {
            playTogether(animator1, animator2)
        start()
        pause() // start both at the same time
        }


        //val gifuri = Uri.parse("res://${packageName}/" + R.drawable.playing)
        //playbackStatusGif.setImageURI(gifuri)
        /*controller = Fresco.newDraweeControllerBuilder()
            .setUri(gifuri)
            .setAutoPlayAnimations(false)
            .build()*/

        //playbackStatusGif.controller = controller
        radioName = findViewById(R.id.radioName)
        radioName.isSelected = true
        radioTitle = findViewById(R.id.radioTitle)
        radioTitle.isSelected = true
        radioArtist = findViewById(R.id.radioArtist)
        radioArtist.isSelected = true
        qualityCodec = findViewById(R.id.qualityCodec)
        qualityBitrate = findViewById(R.id.qualityBitrate)
        qualityChannel = findViewById(R.id.qualityChannel)
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


        initApiServer()


        //adding servers
        //key(listOf(
        //Triple(link,language,state),...))
        saveServerList(
            "Vividh Bharati",
            listOf(

            )
        )

        //add update states on first run to update states and languages
        var prefs = getSharedPreferences("stations_prefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        if (isFirstRun) {
            Log.d(
                "FirstRun", "entering first run!"
            )

            //updateStationStateAndLanguageInPrefs("Vividh Bharati-1+r2", "All India", "Hindi")
           // updateStationStateAndLanguageInPrefs("Vividh Bharati-2+r2", "Nagpur", "Marathi")
            //updateStationStateAndLanguageInPrefs("Big FM-+r1","Mumbai","Marathi")
        } else {
            Log.d("FirstRun", "No server list found for ")
        }

        // finally, set flag so it won't run again
        prefs.edit {
            putBoolean("is_first_run", false)
        }
        Log.d("FirstRun", "lol adding buttons")


        //check if hide or show default section
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val enableDefaultSection = false//prefs.getBoolean("enable_default_section", false)
        if(enableDefaultSection) {
            // Add fixed stations directly:
            //addStationButton(name->"name-number(if 2 same)+rownumber",iconurl,streamurl,rownumber)
            addStationButton(
                "Radio City Freedom-+r1",
                "",
                "",
                row1
            )
            addStationButton(
                "Mirchi Love-+r1",
                "",
                "",
                row1
            )
            addStationButton(
                "Big FM-+r1",
                "",
                "",
                row1
            )
            addStationButton(
                "Red FM-+r1",
                "",
                "",
                row1
            )
            addStationButton(
                "Fever 104 FM-+r1",
                "",
                "",
                row1
            )
            addStationButton(
                "Radio Mirchi-+r2",
                "",
                "",
                row2
            )
            addStationButton(
                "Vividh Bharati-1+r2",
                "",
                "",
                row2
            )
            addStationButton(
                "Vividh Bharati-2+r2",
                "",
                "",
                row2
            )
            addStationButton(
                "AIR FM Rainbow-+r2",
                "",
                "",
                row2
            )
            addStationButton(
                "AIR FM Gold-+r2",
                "",
                "",
                row2
            )
        }
        else{
            val defaultSection = findViewById<View>(R.id.default_station)
            defaultSection.visibility = View.GONE
        }
        //check if to show/hide playing gif
        val enableplayinggif = prefs.getBoolean("enable_playing_gif", true)
        if(!enableplayinggif){
            playbackStatusView.visibility=View.GONE
        }


        //check if hide/show log window
        val enableLogWindow = prefs.getBoolean("enable_log_window", false)
        if (!enableLogWindow){
            logText.visibility=View.GONE
        }
        //Log.d("MyApp","helloooo")
        // Add search button in row_s
        val searchButtonView = layoutInflater.inflate(R.layout.item_search_button, row_s, false)
        val settingButtonView = layoutInflater.inflate(R.layout.item_setting_button, row_s, false)


        searchButtonView.setOnClickListener { openSearchDialog() }
        val settingsDialog = SettingsDialogFragment()

        settingButtonView.setOnClickListener {
            settingsDialog.show(supportFragmentManager, "settings")

        }

        val param_setting = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
        val param_search = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            0.25f
        ).apply {
            gravity = android.view.Gravity.CENTER
        }

        row_s.addView(searchButtonView, param_search)
        row_s.addView(settingButtonView, param_setting)


        PlayerEvents.onQualityUpdate =
            {codec, bitrate, channel ->
                //debug area
                updateQualityView(codec, bitrate, channel, reset=0)////0 to reset/blank,1 to set string
                Log.d("ExoPlayer", "Audio bitrate: ${bitrate / 1000} kbps")
                Log.d("ExoPlayer", "Codec: $codec")
                Log.d("ExoPlayer", "Channels: $channel")
            }
        PlayerEvents.onPlayerError = {
            error ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
            val http_on = prefs.getBoolean("enable_http", false)
            val connectiontype = currentStreamURL?.toUri()?.scheme?.lowercase()
            if (connectiontype=="http") {
                if(http_on) {
                    log("Error: ${error.message}")
                    animatingView?.clearAnimation()
                    radioName.setText(R.string.error)
                    radioIcon.setImageResource(android.R.drawable.ic_delete)
                }
            }
            else{
                log("Error: ${error.message}")
                animatingView?.clearAnimation()
                radioName.setText(R.string.error)
                radioIcon.setImageResource(android.R.drawable.ic_delete)
            }
        }
        PlayerEvents.onPlayingUpdate = {
            isPlaying ->
            updatePlaybackGif(isPlaying)
            playButtonAnimation(animatingView?.findViewById<View>(R.id.stationButtonBack),(if (isPlaying) 1 else 0)+1)
            if(isPlaying){
                playbackStatusAnim.resume()
                log("Playing: ${currentStreamName?.substringBefore("+")}")
            }
            else{
                playbackStatusAnim.pause()
                log("Paused: ${currentStreamName?.substringBefore("+")}")
            }
        }
        PlayerEvents.onMetadataUpdate =
            { entry ->
                if (entry.isNullOrEmpty()) {
                    radioTitle.text = "Unknown"
                    radioArtist.text = "Unknown"
                } else {
                    radioTitle.text = entry.substringAfter(" - ")
                    radioArtist.text = entry.substringBefore(" - ")
                }
            }
        PlayerEvents.onBitrateUpdate =
            { bitrate ->
                updateQualityView(bitrate = bitrate)
                Log.d("Exodata","bitrate is $bitrate")
            }
    }
    override fun onResume() {
        super.onResume()
        if(!currentStreamName.isNullOrEmpty()) {
            if (mediaController.isPlaying) {
                playbackStatusView.postDelayed({
                    playbackStatusAnim.resume()
                }, 400)
            }
        }
    }
    override fun onPause() {
        playbackStatusAnim.pause()
        super.onPause()
        //playbackStatusGif.controller?.animatable?.stop()
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

    private fun playButtonAnimation(view: View?, status: Int) {//0=disappear,1=appear,2=start, (0 is not implemented!)
        var glowView: View? = null
        glowView = animatingView?.findViewById<View>(R.id.stationButtonBack)
        glowView?.clearAnimation()
        glowView?.visibility = View.INVISIBLE
        when(status){
            1 -> {
                val fadeAnim = AnimationUtils.loadAnimation(view?.context, R.anim.gradient_motion)
                glowView = view?.findViewById<View>(R.id.stationButtonBack)
                glowView?.visibility = View.VISIBLE
                animatingView = view
            }
            2 -> {
                val fadeAnim = AnimationUtils.loadAnimation(view?.context, R.anim.gradient_motion)
                glowView = view?.findViewById<View>(R.id.stationButtonBack)
                glowView?.startAnimation(fadeAnim)
                glowView?.visibility = View.VISIBLE
                animatingView = view
            }
        }

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
            playButtonAnimation(view.findViewById<View>(R.id.stationButtonBack),2)

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
                if (db.isStationExists(link)) {
                    log("Station already added: $name")
                    return
                }
            else{
                Log.d("database","call add station")
                saveDynamicStation(name, iconUrl, link, meta)
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
            playButtonAnimation(view.findViewById<View>(R.id.stationButtonBack),2)
            playStationDirect(name, iconUrl, link)
        }
        view.setOnLongClickListener {
            showContextMenu(view, name, link, meta)
            true
        }
        Log.d("database","call befo4ere add station")
        val params =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 4; marginEnd = 4
            }
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val favcol = prefs.getString("fav_columns", "3")?.toIntOrNull() ?: 3
        for (row in dynamicRows) {
            if (row.childCount < favcol) {
                runOnUiThread { row.addView(view, params) }
                return
            }
        }
        addNewRow()
        runOnUiThread { dynamicRows.last().addView(view, params) }


    }


    private fun saveDynamicStation(name: String, iconUrl: String?, link: String, meta: JSONObject) {
        Log.d("database","added station")
        db.addStationDB(name,iconUrl,link,meta)
        Toast.makeText(this, "Station added to Favorite", Toast.LENGTH_LONG).show()

    }


    private fun loadDynamicStations() {
        val cursor = db.getStationsDB()
        cursor.use {
            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.NAME_COL))
                    val iconUrl = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.ICONURL_COL))
                    val link = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.LINK_COL))
                    val meta = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.META_COL))
                    // Append data to text views
                    addDynamicStation(
                        name,
                        iconUrl,
                        link,
                        JSONObject(meta),
                        skipDuplicateCheck = true
                    )
                } while (cursor.moveToNext())
            }
        }
    }

    private fun removeDynamicStation(link: String) {
        val name = db.removeStationsDB(link)
        Toast.makeText(this, "Station \"$name\" removed from Favorite", Toast.LENGTH_LONG).show()
        log("Removed station with name: $name")
    }


    @OptIn(UnstableApi::class)
    private fun playStationDirect(name: String, iconUrl: String?, streamUrl: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
        val http_on = prefs.getBoolean("enable_http", false)
        val connectiontype = streamUrl?.toUri()?.scheme?.lowercase()
        if (currentStreamName == name) {
            if(mediaController.isPlaying){
                // Same station clicked & already playing → pause
                mediaController.pause()
                    updatePlaybackGif(false)
                    animatingView?.clearAnimation()
                log("Paused: ${name.substringBefore("+")}")
                return
            }
            else{
                if (connectiontype == "http") {
                    if (http_on) {
                        mediaController.play()
                        updatePlaybackGif(true)
                        log("Playing: ${name.substringBefore("+")}")
                        return
                    }
                    else{
                        radioTitle.text = "..."
                        radioArtist.text = "..."
                        animatingView?.clearAnimation()
                    }
                }
                else{
                    mediaController.play()
                    updatePlaybackGif(true)
                    log("Playing: ${name.substringBefore("+")}")
                    return
                }
            }
        } else {
            //debug area
            //logAvailableDecoders()
            radioTitle.text = "..."
            radioArtist.text = "..."
            //debug end
            updateQualityView(reset=1,textset = "Loading...")////0 to reset/blank,1 to set string
            //qualityInfo.text = "Loading..."
            //qualityInfo.isSelected = true
            lifecycleScope.launch {
                try {
                    val sessionToken = SessionToken(
                        this@MainActivity,
                        ComponentName(this@MainActivity, PlaybackService::class.java)
                    )
                    mediaController =
                        MediaController.Builder(this@MainActivity, sessionToken).buildAsync()
                            .await()

                    val mediaItem = MediaItem.Builder()
                        .setUri(streamUrl.toUri())
                        .setMediaId("radio-001")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(name.substringBefore("+"))
                                .setArtworkUri(iconUrl?.run(Uri::parse))
                                .setStation(name)
                                .build()
                        ).build()

                    mediaController.setMediaItem(mediaItem)

                    Log.d("MyApp", "playing $streamUrl")
                    currentStreamName = name
                    currentStreamURL = streamUrl
                    radioName.text = name.substringBefore("+")
                    radioName.isSelected = true
                    if (!iconUrl.isNullOrEmpty()) {
                        Glide.with(this@MainActivity).load(iconUrl).into(radioIcon)
                    } else {
                        radioIcon.setImageResource(R.mipmap.ic_launcher)
                    }

                    if (connectiontype == "http") {
                        if (http_on) {
                            log("Playing: ${name.substringBefore("+")}")
                            mediaController.play()
                        } else {
                            updateQualityView(reset=1,textset = "HTTP playback blocked by settings")////0 to reset/blank,1 to set string
                            //qualityInfo.text = "HTTP playback blocked by settings"
                            log("HTTP playback blocked by settings")
                            animatingView?.clearAnimation()
                        }
                    } else {
                        log("Playing: ${name.substringBefore("+")}")
                        mediaController.play()
                    }

                } catch (e: Exception) {
                    log("Error: ${e.message}")
                    radioName.setText(R.string.error)
                    radioIcon.setImageResource(android.R.drawable.ic_delete)
                }
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

    private fun updateQualityView(codec:String? = null,bitrate:Int = 0,channel:Int = 0,reset: Int=-1,textset:String? = null)//0 to reset/blank,1 to set string
    {
        if(reset==1){
            qualityCodec.text = ""
            qualityChannel.text = textset
            qualityBitrate.text = ""
        }
        else {
            if (!codec.isNullOrEmpty()) {
                if (reset == 0) {
                    qualityCodec.text = ""
                }
                qualityCodec.text = "Codec:${codec} | "
            }
            if (channel > 0) {
                if (reset == 0) {
                    qualityChannel.text = ""
                }
                when (channel) {
                    1 -> {
                        qualityChannel.text = ("Channels:${channel} (Mono)")
                    }

                    2 -> {
                        qualityChannel.text = ("Channels:${channel} (Stereo)")
                    }

                    else -> {
                        qualityChannel.text = ("Channels:${channel}")
                    }
                }//debug area
            }
            if (bitrate>0) {
                if (reset == 0) {
                    qualityBitrate.text = ""
                }
                qualityBitrate.text = "Bitrate:${bitrate}kbps | "
            }
        }
    }
    private fun showContextMenu(anchor: View, name: String, link: String, meta: JSONObject) {
        val iconUrl = meta.optString("favicon", null)
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Play").setOnMenuItemClickListener {
            playButtonAnimation(anchor.findViewById<View>(R.id.stationButtonBack),2)
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
            playButtonAnimation(anchor.findViewById<View>(R.id.stationButtonBack),2)
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
        //val anim = playbackStatusGif.controller?.animatable
        //if (isPlaying) anim?.start() else anim?.stop()
        if (isPlaying) playbackStatusAnim.resume() else playbackStatusAnim.pause()

    }


    @androidx.media3.common.util.UnstableApi
    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        mediaController.release()
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
