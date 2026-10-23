package com.roohu.roohux

import android.app.Dialog
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
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private var activeTab = "Discover"
    private var playerView: PlayerView? = null
    private var playerContainer: FrameLayout? = null
    private var youtubeWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { runOnUiThread { refreshMini() } }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                runOnUiThread { message("Playback failed: ${error.errorCodeName}") }
            }
        })
        showShell()
        showDiscover()
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
        youtubeWebView?.apply { stopLoading(); destroy() }
        youtubeWebView = null
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(20, 14, 38), bg, Color.rgb(8, 10, 20))); setPadding(dp(16), dp(14), dp(16), dp(8)) }
        setContentView(root)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("ROOHU", 27f, Color.WHITE, true))
        header.addView(label("X", 27f, violet, true))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(label("✦  YOUR UNIVERSE", 10f, pale, true))
        root.addView(header); header.alpha = 0f; header.translationY = -dp(8).toFloat(); header.animate().alpha(1f).translationY(0f).setDuration(420).start(); addSpace(root, 20)
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(16)); alpha = 0f }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.animate().alpha(1f).setDuration(360).start()
        status = label("", 12f, pale)
        root.addView(status)
        mini = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(48, 31, 76), Color.rgb(29, 27, 49))).apply { cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.rgb(88, 65, 126)) }; setPadding(dp(14), dp(10), dp(10), dp(10)); elevation = dp(5).toFloat() }
        root.addView(mini); addSpace(root, 10)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Discover", "YouTube", "Favorites", "Downloads", "About").forEach { tab ->
            val item = label(if (tab == activeTab) "●  $tab" else tab, 12f, if (tab == activeTab) violet else pale, true).apply { gravity = Gravity.CENTER; setPadding(3, 12, 3, 12); setOnClickListener { activeTab = tab; showShell(); when (tab) { "Discover" -> showDiscover(); "YouTube" -> showYouTube(); "Favorites" -> showFavorites(); "Downloads" -> showDownloads(); else -> showAbout() } } }
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
        mini.addView(button(if (player.isPlaying) "Ⅱ" else "▶") { if (player.isPlaying) player.pause() else player.play(); refreshMini() })
    }
    private fun title(s: String, sub: String) { content.addView(label(s, 26f, Color.WHITE, true)); addSpace(content, 5); content.addView(label(sub, 13f, pale)); addSpace(content, 22) }
    private fun message(s: String) { status.text = s; Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    private fun showDiscover() {
        content.removeAllViews(); title("Find your next favorite", "Free-to-access audio & video from Internet Archive")
        val input = EditText(this).apply { hint = "Search songs, concerts, films..."; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(pale); background = shape(panel); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        content.addView(input); addSpace(content)
        content.addView(button("⌕   Search online") { val q = input.text.toString().trim(); if (q.isNotBlank()) search(q) else message("Enter a search term") })
        addSpace(content, 12)
        content.addView(button("▣   Open music or video from phone") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*")) }
            startActivityForResult(i, 100)
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
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(37, 31, 59), panel)).apply { cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.rgb(54, 47, 76)) }; setPadding(dp(16), dp(15), dp(16), dp(15)); elevation = dp(2).toFloat(); isClickable = true; isFocusable = true; setOnClickListener { animate().scaleX(0.985f).scaleY(0.985f).setDuration(80).withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(120).start(); action() }.start() } }
        box.addView(label(heading, 15f, Color.WHITE, true)); addSpace(box, 6); box.addView(label(sub, 12f, pale))
        box.alpha = 0f; box.translationY = dp(10).toFloat(); content.addView(box); box.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(DecelerateInterpolator()).start(); addSpace(content, 10)
    }
    private fun play(url: String, name: String, type: String) {
        currentTitle = name; currentUrl = url; currentType = type
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url))); player.prepare(); player.play()
        refreshMini(); message("Playing: $name")
        playerContainer?.let { (it.parent as? ViewGroup)?.removeView(it) }
        playerView?.player = null
        playerContainer = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(47, 31, 72), panel)).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.rgb(91, 65, 130)) }
            clipToOutline = true
        }
        playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setControllerAutoShow(true)
            setControllerHideOnTouch(true)
        }
        playerContainer!!.addView(playerView, FrameLayout.LayoutParams(-1, dp(if (type == "video") 250 else 112)))
        val expand = TextView(this).apply {
            text = "⛶"; textSize = 25f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(0x99000000.toInt()); cornerRadius = dp(16).toFloat() }
            elevation = dp(5).toFloat(); setPadding(dp(10), dp(4), dp(10), dp(4))
            contentDescription = "Open full screen player"
            setOnClickListener { openFullScreenPlayer() }
        }
        playerContainer!!.addView(expand, FrameLayout.LayoutParams(dp(48), dp(44), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
        content.addView(playerContainer, 0)
        playerContainer!!.alpha = 0f
        playerContainer!!.translationY = dp(12).toFloat()
        playerContainer!!.animate().alpha(1f).translationY(0f).setDuration(300).start()
    }
    private fun openFullScreenPlayer() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val fullView = PlayerView(this).apply {
            player = this@MainActivity.player
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setControllerAutoShow(true)
            setControllerHideOnTouch(true)
        }
        frame.addView(fullView, FrameLayout.LayoutParams(-1, -1))
        val close = TextView(this).apply {
            text = "✕"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(0x99000000.toInt()); cornerRadius = dp(24).toFloat() }
            setPadding(dp(12), dp(8), dp(12), dp(8)); elevation = dp(8).toFloat()
            contentDescription = "Exit full screen"
            setOnClickListener { dialog.dismiss() }
        }
        frame.addView(close, FrameLayout.LayoutParams(dp(52), dp(48), Gravity.TOP or Gravity.START).apply { setMargins(dp(14), dp(18), 0, 0) })
        dialog.setContentView(frame)
        dialog.setOnDismissListener { fullView.player = null }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        dialog.window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
    private fun favorite(name: String, url: String, type: String) {
        val old = prefs.getString("favorites", "[]") ?: "[]"
        val arr = org.json.JSONArray(old)
        if ((0 until arr.length()).any { arr.getJSONObject(it).optString("url") == url }) { message("Already in favorites"); return }
        arr.put(JSONObject().put("name", name).put("url", url).put("type", type))
        prefs.edit().putString("favorites", arr.toString()).apply(); message("Added to favorites")
    }
    private fun showYouTube() {
        player.pause()
        refreshMini()
        content.removeAllViews()
        title("YouTube", "Search on YouTube, then play a video here")
        content.addView(label("RoohuX uses YouTube's embedded player. It does not extract the audio stream.", 12f, pale))
        addSpace(content, 12)
        val input = EditText(this).apply {
            hint = "Paste a YouTube video link or video ID"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(pale)
            background = shape(panel)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        content.addView(input)
        addSpace(content, 10)
        content.addView(button("⌕   Search YouTube") {
            val q = input.text.toString().trim()
            if (q.isBlank()) {
                message("Enter a song, artist, or video title")
            } else {
                try {
                    val searchUrl = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
                } catch (e: Exception) {
                    message("Could not open YouTube search")
                }
            }
        })
        addSpace(content, 10)
        content.addView(button("▶   Play video in RoohuX") {
            val id = extractYouTubeVideoId(input.text.toString().trim())
            if (id == null) {
                message("Paste a valid YouTube video link or 11-character video ID")
            } else {
                showYouTubeEmbed(id)
            }
        })
        addSpace(content, 14)
        content.addView(label("How to use: search YouTube, copy a video's link, return here, paste it, and tap Play video in RoohuX.", 12f, pale))
        addSpace(content, 10)
        content.addView(label("Availability and playback controls are determined by YouTube. This embedded player does not provide unrestricted downloads or background playback.", 12f, pale))
    }

    private fun extractYouTubeVideoId(value: String): String? {
        if (value.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return value
        val patterns = listOf(
            Regex("(?:youtu\\.be/)([A-Za-z0-9_-]{11})"),
            Regex("(?:youtube(?:-nocookie)?\\.com)/(?:watch\\?v=|embed/|shorts/|live/)([A-Za-z0-9_-]{11})")
        )
        return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.getOrNull(1) }
    }

    private fun showYouTubeEmbed(videoId: String) {
        player.pause()
        refreshMini()
        content.removeAllViews()
        title("Now on YouTube", "Playback provided by the official embedded player")
        content.addView(button("←   Back to YouTube search") { showYouTube() })
        addSpace(content, 12)
        val web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            isFocusable = true
            isFocusableInTouchMode = true
        }
        youtubeWebView = web
        content.addView(web, LinearLayout.LayoutParams(-1, dp(235)))
        val html = """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
              <style>
                html,body{margin:0;padding:0;background:#000;width:100%;height:100%;overflow:hidden}
                .player{position:absolute;inset:0}
                iframe{width:100%;height:100%;border:0}
              </style>
            </head>
            <body>
              <div class="player">
                <iframe
                  src="https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&controls=1&rel=0"
                  title="YouTube video player"
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                  referrerpolicy="strict-origin-when-cross-origin"
                  allowfullscreen>
                </iframe>
              </div>
            </body>
            </html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://www.youtube-nocookie.com/", html, "text/html", "UTF-8", null)
        addSpace(content, 12)
        content.addView(label("Use YouTube's on-player controls. Some videos may not allow embedding.", 12f, pale))
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
        content.removeAllViews(); title("RoohuX", "Your music. Your videos. Your universe.")
        card("✦  Built for personal enjoyment", "Streaming and local playback • No account required") {}
        card("Developer: Roohu", "Support: roohukhan01@gmail.com") {}
        content.addView(label("Privacy: Favorites are saved locally on your device. Online searches and streams contact archive.org. Downloads use Android Download Manager. No analytics or advertising SDKs are included.", 13f, pale))
        addSpace(content); content.addView(label("Media rights: Check the license for each item before downloading or redistributing it.", 13f, pale))
    }
    private fun request(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "RoohuX/1.0 (personal media client)")
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
    override fun onDestroy() { youtubeWebView?.apply { stopLoading(); destroy() }; youtubeWebView = null; playerView?.player = null; player.release(); io.shutdownNow(); super.onDestroy() }
}
