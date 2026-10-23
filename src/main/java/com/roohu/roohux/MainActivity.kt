package com.roohu.roohux

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val bg = Color.rgb(9, 9, 19)
    private val panel = Color.rgb(25, 23, 43)
    private val violet = Color.rgb(166, 112, 255)
    private val pale = Color.rgb(223, 218, 238)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var mini: LinearLayout
    private lateinit var player: ExoPlayer
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("roohux", MODE_PRIVATE) }
    private var currentTitle = "Nothing playing"
    private var currentUrl = ""
    private var currentType = "audio"
    private var activeTab = "Home"
    private val ticker = Handler(Looper.getMainLooper())
    private var progressBar: SeekBar? = null
    private var positionText: TextView? = null
    private var timer: Runnable? = null
    private var playerView: PlayerView? = null
    private var playerContainer: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { runOnUiThread { refreshMini() } }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                runOnUiThread { message("Playback failed: ${error.errorCodeName}") }
            }
        })
        showShell()
        showHome()
        ticker.post(object : Runnable { override fun run() { updateProgress(); ticker.postDelayed(this, 1000) } })
    }
    private fun shape(color: Int, radius: Float = 22f): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun label(s: String, size: Float = 16f, color: Int = Color.WHITE, bold: Boolean = false): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun button(s: String, action: () -> Unit): TextView = label(s, 14f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER; background = shape(panel); setPadding(18, 15, 18, 15)
        setOnClickListener { action() }
    }
    private fun addSpace(parent: LinearLayout, h: Int = 12) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(h))) }
    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    private fun showShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(16), dp(14), dp(16), dp(8)) }
        setContentView(root)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("ROOHU", 27f, Color.WHITE, true))
        header.addView(label("X", 27f, violet, true))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(label("✦  YOUR UNIVERSE", 10f, pale, true))
        root.addView(header); addSpace(root, 20)
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(16)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        status = label("", 12f, pale)
        root.addView(status)
        mini = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(panel); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        root.addView(mini); addSpace(root, 10)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Home", "Search", "Library", "Settings").forEach { tab ->
            val item = label(tab, 12f, if (tab == activeTab) violet else pale, true).apply { gravity = Gravity.CENTER; setPadding(3, 12, 3, 12); setOnClickListener { activeTab = tab; showShell(); when (tab) { "Home" -> showHome(); "Search" -> showDiscover(); "Library" -> showLibrary(); else -> showSettings() } } }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav)
        refreshMini()
    }
    private fun refreshMini() {
        if (!::mini.isInitialized) return
        mini.removeAllViews()
        val info = label("♫  $currentTitle", 13f, Color.WHITE, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        mini.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        info.setOnClickListener { showNowPlaying() }
        mini.addView(button(if (player.isPlaying) "Ⅱ" else "▶") { if (player.isPlaying) player.pause() else player.play(); refreshMini() })
    }
    private fun formatTime(ms: Long): String { val sec = (ms.coerceAtLeast(0) / 1000); return "%d:%02d".format(sec / 60, sec % 60) }
    private fun updateProgress() {
        if (!::player.isInitialized) return
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        progressBar?.max = 1000
        progressBar?.progress = if (duration > 0) ((player.currentPosition.coerceAtLeast(0) * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
        positionText?.text = "${formatTime(player.currentPosition)} / ${formatTime(duration)}"
    }
    private fun showNowPlaying() {
        content.removeAllViews()
        title("NOW PLAYING", "RoohuX immersive player")
        val artwork = label(if (currentType == "video") "▣" else "♫", 100f, violet, true).apply {
            gravity = Gravity.CENTER; background = shape(panel, dp(30).toFloat())
        }
        content.addView(artwork, LinearLayout.LayoutParams(-1, dp(230)))
        addSpace(content, 24)
        content.addView(label(currentTitle, 24f, Color.WHITE, true)); addSpace(content, 20)
        positionText = label("0:00 / 0:00", 13f, pale)
        content.addView(positionText)
        progressBar = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(violet)
            thumbTintList = android.content.res.ColorStateList.valueOf(violet)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && player.duration > 0) player.seekTo(player.duration * progress / 1000L)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        content.addView(progressBar); addSpace(content, 14)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(button("↺  -10s") { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) }, LinearLayout.LayoutParams(0, dp(58), 1f))
        controls.addView(button("Ⅱ / ▶") { if (player.isPlaying) player.pause() else player.play(); refreshMini() }, LinearLayout.LayoutParams(0, dp(58), 1f))
        controls.addView(button("+10s  ↻") { player.seekTo(player.currentPosition + 10000) }, LinearLayout.LayoutParams(0, dp(58), 1f))
        content.addView(controls); addSpace(content, 18)
        content.addView(button("♡  Add to favorites") { favorite(currentTitle, currentUrl, currentType) }); addSpace(content)
        content.addView(button("☷  Add to playlist") { choosePlaylist(currentTitle, currentUrl, currentType) }); addSpace(content)
        content.addView(button("◷  Sleep timer") { chooseSleepTimer() })
        updateProgress()
    }
    private fun chooseSleepTimer() {
        android.app.AlertDialog.Builder(this).setTitle("Stop playback after")
            .setItems(arrayOf("Off", "15 minutes", "30 minutes", "60 minutes")) { _, which ->
                timer?.let { ticker.removeCallbacks(it) }; timer = null
                val minutes = when (which) { 1 -> 15; 2 -> 30; 3 -> 60; else -> 0 }
                if (minutes > 0) {
                    val stop = Runnable { player.pause(); refreshMini(); message("Sleep timer ended") }
                    timer = stop; ticker.postDelayed(stop, minutes * 60_000L)
                }
                message(if (minutes > 0) "Sleep timer: ${minutes} minutes" else "Sleep timer off")
            }.show()
    }
    private fun playlistNames(): org.json.JSONArray = try { org.json.JSONArray(prefs.getString("playlist_names", "[]")) } catch (_: Exception) { org.json.JSONArray() }
    private fun choosePlaylist(name: String, url: String, type: String) {
        val names = playlistNames()
        val options = (0 until names.length()).map { names.optString(it) } + "＋ Create new playlist"
        android.app.AlertDialog.Builder(this).setTitle("Add to playlist").setItems(options.toTypedArray()) { _, index ->
            if (index == names.length()) createPlaylist(name, url, type)
            else { saveMedia("playlist_" + names.optString(index), name, url, type); message("Saved to playlist") }
        }.show()
    }
    private fun createPlaylist(name: String = "", url: String = "", type: String = "") {
        val input = EditText(this).apply { hint = "Playlist name"; setSingleLine() }
        android.app.AlertDialog.Builder(this).setTitle("New playlist").setView(input).setPositiveButton("Create") { _, _ ->
            val title = input.text.toString().trim().take(40)
            if (title.isNotBlank()) {
                val names = playlistNames()
                if ((0 until names.length()).none { names.optString(it) == title }) {
                    names.put(title); prefs.edit().putString("playlist_names", names.toString()).apply()
                }
                if (url.isNotBlank()) saveMedia("playlist_" + title, name, url, type)
                showLibrary()
            }
        }.setNegativeButton("Cancel", null).show()
    }
    private fun showPlaylist(name: String) {
        content.removeAllViews(); title(name, "Your custom playlist")
        showSaved("playlist_" + name, 500)
        addSpace(content)
        content.addView(button("Delete playlist") {
            val names = playlistNames(); val remaining = org.json.JSONArray()
            for (i in 0 until names.length()) if (names.optString(i) != name) remaining.put(names.optString(i))
            prefs.edit().putString("playlist_names", remaining.toString()).remove("playlist_" + name).apply(); showLibrary()
        })
    }
    private fun title(s: String, sub: String) { content.addView(label(s, 26f, Color.WHITE, true)); addSpace(content, 5); content.addView(label(sub, 13f, pale)); addSpace(content, 22) }
    private fun message(s: String) { status.text = s; Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    private fun showHome() {
        content.removeAllViews(); title("Your soundspace", "Music, films and everything you love")
        card("✦  Explore new music", "Discover public audio and video collections") { activeTab = "Search"; showShell(); showDiscover() }
        card("♫  Your favorites", "Songs and films you saved") { activeTab = "Library"; showShell(); showLibrary() }
        card("▣  Open media from phone", "Play your own audio and video files") { openLocal() }
        addSpace(content, 16)
        content.addView(label("CURATED FOR YOU", 12f, violet, true)); addSpace(content)
        listOf("Jazz sessions" to "jazz", "Classical essentials" to "classical music", "Live concerts" to "live concert", "Documentary cinema" to "documentary").forEach { (name, query) ->
            card("✦  $name", "Explore the collection →") { activeTab = "Search"; showShell(); search(query) }
        }
        addSpace(content, 16)
        content.addView(label("RECENTLY PLAYED", 12f, violet, true)); addSpace(content)
        showSaved("history", 6)
    }
    private fun openLocal() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*")) }
        startActivityForResult(i, 100)
    }
    private fun stored(key: String): org.json.JSONArray = try { org.json.JSONArray(prefs.getString(key, "[]") ?: "[]") } catch (_: Exception) { org.json.JSONArray() }
    private fun saveMedia(key: String, name: String, url: String, type: String) {
        val arr = stored(key)
        if ((0 until arr.length()).any { arr.optJSONObject(it)?.optString("url") == url }) return
        arr.put(JSONObject().put("name", name).put("url", url).put("type", type))
        prefs.edit().putString(key, arr.toString()).apply()
    }
    private fun showSaved(key: String, limit: Int) {
        val arr = stored(key)
        if (arr.length() == 0) content.addView(label("Nothing here yet.", 13f, pale))
        for (i in 0 until minOf(arr.length(), limit)) {
            val o = arr.optJSONObject(i) ?: continue
            card("♫  " + o.optString("name"), "Tap to play") { play(o.optString("url"), o.optString("name"), o.optString("type")) }
        }
    }
    private fun showLibrary() {
        content.removeAllViews(); title("Your library", "All your music, saved in one place")
        card("▣  Play local files", "Choose music or video on your phone") { openLocal() }
        card("↓  Downloads", "Open Android Downloads") { showDownloads() }
        addSpace(content, 16)
        content.addView(label("YOUR PLAYLISTS", 12f, violet, true)); addSpace(content)
        content.addView(button("＋  Create playlist") { createPlaylist() }); addSpace(content)
        val names = playlistNames()
        for (i in 0 until names.length()) {
            val name = names.optString(i)
            card("☷  " + name, "Open your playlist →") { showPlaylist(name) }
        }
        addSpace(content, 16)
        content.addView(label("FAVORITES", 12f, violet, true)); addSpace(content)
        showSaved("favorites", 100)
        addSpace(content, 16)
        content.addView(label("RECENTLY PLAYED", 12f, violet, true)); addSpace(content)
        showSaved("history", 25)
        addSpace(content)
        content.addView(button("Clear listening history") { prefs.edit().remove("history").apply(); showLibrary() })
    }
    private fun showSettings() {
        content.removeAllViews(); title("Settings", "Make RoohuX your own")
        card("Repeat playback", "Choose repeat mode") {
            android.app.AlertDialog.Builder(this).setItems(arrayOf("Repeat off", "Repeat one", "Repeat all")) { _, i ->
                player.repeatMode = when (i) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
                message("Repeat mode updated")
            }.show()
        }
        card("Shuffle playback", if (player.shuffleModeEnabled) "On" else "Off") { player.shuffleModeEnabled = !player.shuffleModeEnabled; showSettings() }
        card("About RoohuX", "Developer Roohu • Version 3.0") { showAbout() }
    }
    private fun showDiscover() {
        content.removeAllViews(); title("Find your next favorite", "Free-to-access audio & video from Internet Archive")
        val input = EditText(this).apply { hint = "Search songs, concerts, films..."; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(pale); background = shape(panel); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        content.addView(input); addSpace(content)
        content.addView(button("⌕   Search online") { val q = input.text.toString().trim(); if (q.isNotBlank()) search(q) else message("Enter a search term") })
        addSpace(content, 12)
        content.addView(button("▣   Open music or video from phone") {
            openLocal()
        })
        addSpace(content, 26)
        content.addView(label("EXPLORE", 12f, violet, true)); addSpace(content)
        listOf("Jazz" to "jazz", "Classical" to "classical music", "Live concerts" to "live concert", "Documentary films" to "documentary").forEach { (name, q) ->
            content.addView(button("✦   $name    →") { input.setText(q); search(q) }); addSpace(content, 10)
        }
    }
    private fun search(query: String) {
        content.removeAllViews(); title("Results for “$query”", "Searching public media collections")
        content.addView(button("←   New search") { showDiscover() })
        message("Searching...")
        io.execute {
            try {
                val term = URLEncoder.encode("($query) AND mediatype:(audio OR movies)", "UTF-8")
                val json = request("https://archive.org/advancedsearch.php?q=$term&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=mediatype&rows=25&page=1&output=json")
                val docs = JSONObject(json).getJSONObject("response").getJSONArray("docs")
                runOnUiThread {
                    status.text = "${docs.length()} results"
                    if (docs.length() == 0) content.addView(label("No results. Try another search."))
                    for (i in 0 until docs.length()) {
                        val obj = docs.getJSONObject(i)
                        val id = obj.optString("identifier")
                        val name = obj.optString("title", id)
                        val kind = obj.optString("mediatype")
                        card("${if (kind == "audio") "♫" else "▣"}  $name", "${kind.uppercase()}  •  Open media") { loadItem(id, name, kind) }
                    }
                }
            } catch (e: Exception) { runOnUiThread { message("Search unavailable: ${e.localizedMessage ?: "network error"}") } }
        }
    }
    private fun loadItem(id: String, name: String, kind: String) {
        message("Loading available media...")
        io.execute {
            try {
                val files = JSONObject(request("https://archive.org/metadata/${URLEncoder.encode(id, "UTF-8")}")).getJSONArray("files")
                val candidates = mutableListOf<Pair<String, String>>()
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val filename = f.optString("name")
                    val ext = filename.substringAfterLast('.', "").lowercase()
                    if (ext in setOf("mp3", "m4a", "ogg", "opus", "mp4", "webm", "mkv")) {
                        val fileUrl = "https://archive.org/download/${Uri.encode(id)}/${filename.split('/').joinToString("/") { Uri.encode(it) }}"
                        candidates.add(filename to fileUrl)
                    }
                }
                runOnUiThread {
                    content.removeAllViews(); title(name, "Available media files • ${candidates.size}")
                    content.addView(button("←   Back to search") { showDiscover() }); addSpace(content)
                    if (candidates.isEmpty()) content.addView(label("No compatible playable files found."))
                    candidates.take(35).forEach { (filename, url) ->
                        val type = if (filename.substringAfterLast('.').lowercase() in setOf("mp4", "webm", "mkv")) "video" else "audio"
                        card(filename, "▶ Play  •  ☆ Favorite  •  Download") {
                            play(url, filename, type)
                            content.addView(button("☆   Save to favorites") { favorite(filename, url, type) })
                            addSpace(content)
                            content.addView(button("↓   Download if licensed/permitted") { download(url, filename) })
                        }
                    }
                }
            } catch (e: Exception) { runOnUiThread { message("Could not load files: ${e.localizedMessage ?: "network error"}") } }
        }
    }
    private fun card(heading: String, sub: String, action: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(panel); setPadding(dp(15), dp(14), dp(15), dp(14)); setOnClickListener { action() } }
        box.addView(label(heading, 15f, Color.WHITE, true)); addSpace(box, 6); box.addView(label(sub, 12f, pale))
        content.addView(box); addSpace(content, 10)
    }
    private fun play(url: String, name: String, type: String) {
        saveMedia("history", name, url, type)
        currentTitle = name; currentUrl = url; currentType = type
        progressBar = null; positionText = null
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url))); player.prepare(); player.play()
        refreshMini(); message("Playing: $name")
        playerContainer?.let { (it.parent as? ViewGroup)?.removeView(it) }
        playerView?.player = null
        playerContainer = FrameLayout(this).apply { background = shape(panel) }
        playerView = PlayerView(this).apply { player = this@MainActivity.player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) }
        playerContainer!!.addView(playerView, FrameLayout.LayoutParams(-1, dp(if (type == "video") 230 else 100)))
        content.addView(playerContainer, 0)
    }
    private fun favorite(name: String, url: String, type: String) {
        val old = prefs.getString("favorites", "[]") ?: "[]"
        val arr = org.json.JSONArray(old)
        if ((0 until arr.length()).any { arr.getJSONObject(it).optString("url") == url }) { message("Already in favorites"); return }
        arr.put(JSONObject().put("name", name).put("url", url).put("type", type))
        prefs.edit().putString("favorites", arr.toString()).apply(); message("Added to favorites")
    }
    private fun showFavorites() {
        content.removeAllViews(); title("Your favorites", "Saved on this device")
        val arr = org.json.JSONArray(prefs.getString("favorites", "[]"))
        if (arr.length() == 0) content.addView(label("No favorites yet. Find media in Discover."))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            card(o.optString("name"), "▶ Tap to play") { play(o.optString("url"), o.optString("name"), o.optString("type")) }
        }
        content.addView(button("Clear favorites") { prefs.edit().remove("favorites").apply(); showFavorites() })
    }
    private fun download(url: String, filename: String) {
        try {
            val clean = filename.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(100)
            val req = DownloadManager.Request(Uri.parse(url)).setTitle(clean).setDescription("RoohuX media download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, clean)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            message("Download started. Check Android Downloads.")
        } catch (e: Exception) { message("Download failed: ${e.localizedMessage}") }
    }
    private fun showDownloads() {
        content.removeAllViews(); title("Downloads", "Files are saved in your Android Downloads folder")
        content.addView(button("↗   Open Android Downloads") {
            try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (e: Exception) { message("Downloads app unavailable") }
        })
        addSpace(content); content.addView(label("Only download media when its license or rights allow it.", 13f, pale))
    }
    private fun showAbout() {
        content.removeAllViews(); title("RoohuX 3.0", "Your music. Your videos. Your universe.")
        card("✦  Built for personal enjoyment", "Streaming and local playback • No account required") {}
        card("Developer: Roohu", "Support: roohukhan01@gmail.com") {}
        content.addView(label("Privacy: Favorites are saved locally on your device. Online searches and streams contact archive.org. Downloads use Android Download Manager. No analytics or advertising SDKs are included.", 13f, pale))
        addSpace(content); content.addView(label("Media rights: Check the license for each item before downloading or redistributing it.", 13f, pale))
    }
    private fun request(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "RoohuX/3.0 (personal media client)")
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
    @Deprecated("Android activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) data?.data?.let { uri ->
            val mime = contentResolver.getType(uri) ?: "audio/*"
            play(uri.toString(), uri.lastPathSegment ?: "Local media", if (mime.startsWith("video")) "video" else "audio")
        }
    }
    override fun onDestroy() { ticker.removeCallbacksAndMessages(null); playerView?.player = null; player.release(); io.shutdownNow(); super.onDestroy() }
}
