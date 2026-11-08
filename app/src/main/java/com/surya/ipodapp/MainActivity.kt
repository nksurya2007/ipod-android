package com.surya.ipodapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Date
import kotlin.math.max

class Row(
    val title: String,
    val detail: String? = null,
    val arrow: Boolean = false,
    val action: () -> Unit
)

class Menu(val title: String, val rows: List<Row>, var sel: Int = 0)

class RowView(ctx: Context) : LinearLayout(ctx) {
    private val d = ctx.resources.displayMetrics.density
    val title = TextView(ctx)
    val detail = TextView(ctx)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((12 * d).toInt(), 0, (12 * d).toInt(), 0)
        layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (34 * d).toInt())
        title.textSize = 15f
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        detail.textSize = 12f
        detail.maxLines = 1
        detail.ellipsize = TextUtils.TruncateAt.END
        detail.maxWidth = (130 * d).toInt()
        detail.gravity = Gravity.END
        addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val dp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        dp.leftMargin = (8 * d).toInt()
        addView(detail, dp)
    }

    fun bind(t: String, dt: String?, bg: Int, tc: Int, dc: Int) {
        title.text = t
        detail.text = dt ?: ""
        setBackgroundColor(bg)
        title.setTextColor(tc)
        detail.setTextColor(dc)
    }
}

class MainActivity : Activity() {
    private enum class Kind(val key: String, val label: String) {
        SONGS("songs", "Songs"),
        ARTISTS("artists", "Artists"),
        ALBUMS("albums", "Albums"),
        PLAYLISTS("playlists", "Playlists")
    }

    private companion object {
        const val REQ_FOLDER = 42
        const val FOLDER_MENU = "Music Folder"
    }

    private val prefs by lazy { getSharedPreferences("ipod", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var engine: PlayerEngine

    private val stack = ArrayList<Menu>()
    private var inNowPlaying = false
    private var inGame = false
    private var inCoverFlow = false
    private var scrubMode = false
    private var coverTitle = "Cover Flow"
    private var coverSongs: List<Song> = emptyList()
    private var coverGroups: List<Group> = emptyList()
    private var coverIsSongs = false
    private var songs: List<Song> = emptyList()
    private var lastMenu: Menu? = null
    private var npArtId: Long? = null

    private var themeIndex = 0
    private var wheelIndex = 0
    private val theme: Theme get() = Theme.all[themeIndex]

    private val sortNames = listOf("A-Z", "Z-A", "Recently Added")
    private val songSort: Int get() = prefs.getInt("songSort", 0)
    private val clickSoundOn: Boolean get() = prefs.getBoolean("clickSound", true)
    private fun viewMode(k: Kind) = prefs.getInt("view_" + k.key, 0)

    // views
    private lateinit var root: LinearLayout
    private lateinit var screen: FrameLayout
    private lateinit var bar: FrameLayout
    private lateinit var titleLabel: TextView
    private lateinit var clockLabel: TextView
    private lateinit var indicator: PlayIcon
    private lateinit var battery: BatteryView
    private lateinit var list: ListView
    private lateinit var adapter: MenuAdapter
    private lateinit var npView: LinearLayout
    private lateinit var art: ImageView
    private lateinit var npTitle: TextView
    private lateinit var npArtist: TextView
    private lateinit var t1: TextView
    private lateinit var t2: TextView
    private lateinit var volLabel: TextView
    private lateinit var modeLabel: TextView
    private lateinit var progress: BarView
    private lateinit var volBar: BarView
    private lateinit var coverFlow: CoverFlowView
    private lateinit var game: BrickGameView
    private lateinit var wheel: ClickWheel

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private val am: AudioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---------------------------------------------------------------- lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        hideSystemUi()

        themeIndex = prefs.getInt("themeIndex", 0).coerceIn(0, Theme.all.size - 1)
        wheelIndex = prefs.getInt("wheelIndex", 0).coerceIn(0, WheelStyle.all.size - 1)

        engine = PlayerEngine(this)
        engine.onChange = { updateNP() }

        buildUI()
        applyTheme()
        wireWheel()
        wheel.style = WheelStyle.all[wheelIndex]

        stack.add(if (hasLibraryAccess()) mainMenu() else noAccessMenu())
        render()
        if (Library.folderUri(this) != null) startScan()
        if (!hasLibraryAccess()) requestAudioAccess()
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateNP()
            if (Library.scanning) refreshMusicMenu()
            handler.postDelayed(this, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (hasLibraryAccess() && stack.size == 1 && stack[0].rows.firstOrNull()?.title == "Allow Music access") {
            stack[0] = mainMenu()
            render()
        }
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        game.stop()
        engine.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (inNowPlaying || inGame || inCoverFlow || stack.size > 1) menuButton() else super.onBackPressed()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    // ---------------------------------------------------------------- permission
    private fun audioPerm(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioAccess(): Boolean =
        Build.VERSION.SDK_INT < 23 || checkSelfPermission(audioPerm()) == PackageManager.PERMISSION_GRANTED

    /** Either the audio permission is granted, or the user picked a music folder (no permission needed). */
    private fun hasLibraryAccess(): Boolean = hasAudioAccess() || Library.folderUri(this) != null

    private fun requestAudioAccess() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(audioPerm()), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasAudioAccess()) {
            stack.clear()
            stack.add(mainMenu())
            render()
        }
    }

    private fun openAppSettings() {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        startActivity(i)
    }

    // ---------------------------------------------------------------- music folder
    private fun pickFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_FOLDER)
        } catch (e: Exception) {
            Toast.makeText(this, "No folder picker available on this device", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
        }
        Library.setFolder(this, uri)
        songs = emptyList()
        if (stack.isNotEmpty() && stack[0].rows.firstOrNull()?.title == "Allow Music access") {
            stack[0] = mainMenu()
        }
        startScan()
        render()
    }

    private fun startScan() {
        Library.scan(this) { refreshMusicMenu() }
        refreshMusicMenu()
    }

    private fun refreshMusicMenu() {
        if (stack.lastOrNull()?.title == FOLDER_MENU) refreshTop(musicFolderMenu())
    }

    private fun usePhoneLibrary() {
        Library.setFolder(this, null)
        songs = emptyList()
        if (!hasAudioAccess()) {
            stack.clear()
            stack.add(noAccessMenu())
            render()
            requestAudioAccess()
        } else {
            refreshTop(musicFolderMenu())
        }
    }

    // ---------------------------------------------------------------- UI
    @Suppress("DEPRECATION")
    private fun buildUI() {
        val dm = resources.displayMetrics
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setOnApplyWindowInsetsListener { v, ins ->
            var top = ins.systemWindowInsetTop
            var bottom = ins.systemWindowInsetBottom
            if (Build.VERSION.SDK_INT >= 28) {
                val dc = ins.displayCutout
                if (dc != null) {
                    top = max(top, dc.safeInsetTop)
                    bottom = max(bottom, dc.safeInsetBottom)
                }
            }
            v.setPadding(0, top, 0, bottom)
            ins
        }

        screen = FrameLayout(this)
        screen.setPadding(dp(3), dp(3), dp(3), dp(3))
        val sp = LinearLayout.LayoutParams(match, 0, 1f)
        sp.setMargins(dp(16), dp(12), dp(16), dp(16))
        root.addView(screen, sp)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        screen.addView(content, FrameLayout.LayoutParams(match, match))

        // title bar: title (left), time (center), play state + battery (right)
        bar = FrameLayout(this)
        content.addView(bar, LinearLayout.LayoutParams(match, dp(24)))

        titleLabel = TextView(this)
        titleLabel.setTypeface(null, Typeface.BOLD)
        titleLabel.textSize = 13f
        titleLabel.maxLines = 1
        titleLabel.ellipsize = TextUtils.TruncateAt.END
        titleLabel.gravity = Gravity.CENTER_VERTICAL
        titleLabel.maxWidth = max(dp(60), (dm.widthPixels - dp(38)) / 2 - dp(48))
        val tl = FrameLayout.LayoutParams(wrap, match, Gravity.START or Gravity.CENTER_VERTICAL)
        tl.marginStart = dp(8)
        bar.addView(titleLabel, tl)

        clockLabel = TextView(this)
        clockLabel.setTypeface(null, Typeface.BOLD)
        clockLabel.textSize = 12f
        clockLabel.gravity = Gravity.CENTER
        bar.addView(clockLabel, FrameLayout.LayoutParams(wrap, match, Gravity.CENTER))

        val right = LinearLayout(this)
        right.orientation = LinearLayout.HORIZONTAL
        right.gravity = Gravity.CENTER_VERTICAL
        indicator = PlayIcon(this)
        battery = BatteryView(this)
        val ip = LinearLayout.LayoutParams(dp(12), dp(12))
        ip.marginEnd = dp(8)
        right.addView(indicator, ip)
        right.addView(battery, LinearLayout.LayoutParams(dp(26), dp(12)))
        val rp = FrameLayout.LayoutParams(wrap, match, Gravity.END)
        rp.marginEnd = dp(8)
        bar.addView(right, rp)

        val body = FrameLayout(this)
        content.addView(body, LinearLayout.LayoutParams(match, 0, 1f))

        // menu list
        list = ListView(this)
        list.divider = null
        list.dividerHeight = 0
        list.isVerticalScrollBarEnabled = false
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.setOnTouchListener { _, _ -> true }
        adapter = MenuAdapter()
        list.adapter = adapter
        body.addView(list, FrameLayout.LayoutParams(match, match))

        // now playing
        npView = LinearLayout(this)
        npView.orientation = LinearLayout.VERTICAL
        npView.setPadding(dp(10), dp(6), dp(10), dp(6))
        npView.visibility = View.GONE
        art = ImageView(this)
        art.scaleType = ImageView.ScaleType.FIT_CENTER
        npView.addView(art, LinearLayout.LayoutParams(match, 0, 1f))
        npTitle = TextView(this)
        npTitle.setTypeface(null, Typeface.BOLD)
        npTitle.textSize = 16f
        npTitle.gravity = Gravity.CENTER
        npTitle.maxLines = 1
        npTitle.ellipsize = TextUtils.TruncateAt.END
        npArtist = TextView(this)
        npArtist.textSize = 13f
        npArtist.gravity = Gravity.CENTER
        npArtist.maxLines = 1
        npArtist.ellipsize = TextUtils.TruncateAt.END
        progress = BarView(this)
        t1 = TextView(this)
        t1.textSize = 11f
        t2 = TextView(this)
        t2.textSize = 11f
        t2.gravity = Gravity.END
        val times = LinearLayout(this)
        times.orientation = LinearLayout.HORIZONTAL
        times.addView(t1, LinearLayout.LayoutParams(0, wrap, 1f))
        times.addView(t2, LinearLayout.LayoutParams(0, wrap, 1f))
        volLabel = TextView(this)
        volLabel.text = "Volume"
        volLabel.textSize = 10f
        modeLabel = TextView(this)
        modeLabel.setTypeface(null, Typeface.BOLD)
        modeLabel.textSize = 10f
        modeLabel.gravity = Gravity.END
        val volRow = LinearLayout(this)
        volRow.orientation = LinearLayout.HORIZONTAL
        volRow.addView(volLabel, LinearLayout.LayoutParams(0, wrap, 1f))
        volRow.addView(modeLabel, LinearLayout.LayoutParams(0, wrap, 2f))
        volBar = BarView(this)

        fun gap(lp: LinearLayout.LayoutParams): LinearLayout.LayoutParams {
            lp.topMargin = dp(5)
            return lp
        }
        npView.addView(npTitle, gap(LinearLayout.LayoutParams(match, wrap)))
        npView.addView(npArtist, gap(LinearLayout.LayoutParams(match, wrap)))
        npView.addView(progress, gap(LinearLayout.LayoutParams(match, dp(6))))
        npView.addView(times, gap(LinearLayout.LayoutParams(match, wrap)))
        npView.addView(volRow, gap(LinearLayout.LayoutParams(match, wrap)))
        npView.addView(volBar, gap(LinearLayout.LayoutParams(match, dp(6))))
        body.addView(npView, FrameLayout.LayoutParams(match, match))

        coverFlow = CoverFlowView(this)
        coverFlow.visibility = View.GONE
        body.addView(coverFlow, FrameLayout.LayoutParams(match, match))

        game = BrickGameView(this)
        game.visibility = View.GONE
        body.addView(game, FrameLayout.LayoutParams(match, match))

        // wheel
        val ws = (minOf(dm.widthPixels, dm.heightPixels) * 0.76f).toInt()
        wheel = ClickWheel(this)
        val wp = LinearLayout.LayoutParams(ws, ws)
        wp.gravity = Gravity.CENTER_HORIZONTAL
        wp.bottomMargin = dp(16)
        root.addView(wheel, wp)

        setContentView(root)
        root.requestApplyInsets()
    }

    private fun applyTheme() {
        val t = theme
        root.setBackgroundColor(t.body)
        window.setBackgroundDrawable(ColorDrawable(t.body))
        val bg = GradientDrawable()
        bg.setColor(t.screen)
        bg.setStroke(dp(3), t.border)
        bg.cornerRadius = dp(8).toFloat()
        screen.background = bg
        screen.clipToOutline = true
        bar.setBackgroundColor(t.bar)
        titleLabel.setTextColor(t.barText)
        clockLabel.setTextColor(t.barText)
        indicator.color = t.barText
        battery.color = t.barText
        list.setBackgroundColor(t.screen)
        npView.setBackgroundColor(t.screen)
        art.setBackgroundColor(t.bar)
        npTitle.setTextColor(t.text)
        npArtist.setTextColor(t.subtext)
        t1.setTextColor(t.subtext)
        t2.setTextColor(t.subtext)
        volLabel.setTextColor(t.subtext)
        modeLabel.setTextColor(t.selBg)
        volBar.track = t.bar
        volBar.fill = t.selBg
        progress.track = t.bar
        progress.fill = t.selBg
        adapter.notifyDataSetChanged()
    }

    private fun wireWheel() {
        wheel.onStep = { s -> step(s) }
        wheel.onCenter = { center() }
        wheel.onRotate = { dd ->
            if (inGame && !inNowPlaying) game.rotate(dd)
        }
        wheel.onMenu = { menuButton() }
        wheel.onPlay = { engine.toggle() }
        wheel.onNext = { engine.next() }
        wheel.onPrev = { engine.prev() }
    }

    private inner class MenuAdapter : BaseAdapter() {
        override fun getCount(): Int = stack.lastOrNull()?.rows?.size ?: 0
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = (convertView as? RowView) ?: RowView(this@MainActivity)
            val m = stack.lastOrNull() ?: return v
            if (position >= m.rows.size) return v
            val t = theme
            val row = m.rows[position]
            val sel = position == m.sel
            v.bind(
                row.title,
                if (row.arrow) "\u203A" else row.detail,
                if (sel) t.selBg else t.screen,
                if (sel) t.selText else t.text,
                if (sel) t.selText else t.subtext
            )
            return v
        }
    }

    // ---------------------------------------------------------------- menus
    private fun mainMenu(): Menu = Menu("iPod", listOf(
        Row("Playlists", arrow = true) { openList(Kind.PLAYLISTS) },
        Row("Artists", arrow = true) { openList(Kind.ARTISTS) },
        Row("Albums", arrow = true) { openList(Kind.ALBUMS) },
        Row("Songs", arrow = true) { openList(Kind.SONGS) },
        Row("Now Playing", arrow = true) { showNP() },
        Row("Shuffle Songs") { shuffleAll() },
        Row("Extras", arrow = true) { push(extrasMenu()) },
        Row("Settings", arrow = true) { push(settingsMenu()) }
    ))

    private fun noAccessMenu(): Menu = Menu("iPod", listOf(
        Row("Allow Music access", "Grant") { requestAudioAccess() },
        Row("Choose music folder", arrow = true) { pickFolder() },
        Row("App permission settings", arrow = true) { openAppSettings() },
        Row("Settings", arrow = true) { push(settingsMenu()) }
    ))

    private fun sortedSongs(): List<Song> = when (songSort) {
        1 -> songs.reversed()
        2 -> songs.sortedByDescending { it.dateAdded }
        else -> songs
    }

    private fun groupsFor(k: Kind): List<Group> {
        val g = when (k) {
            Kind.ARTISTS -> Library.artists(songs)
            Kind.ALBUMS -> Library.albums(songs)
            else -> Library.playlists(this, songs)
        }
        return when (songSort) {
            1 -> g.sortedByDescending { it.name.lowercase() }
            2 -> g.sortedByDescending { grp -> grp.songs.maxOfOrNull { it.dateAdded } ?: 0L }
            else -> g.sortedBy { it.name.lowercase() }
        }
    }

    /** Opens Songs / Artists / Albums / Playlists as a line list or Cover Flow, per Settings. */
    private fun openList(k: Kind) {
        if (Library.scanning) {
            push(Menu(k.label, listOf(Row("Scanning folder... " + Library.scanned) {})))
            return
        }
        songs = Library.songs(this)
        val cf = viewMode(k) == 1
        if (k == Kind.SONGS) {
            val items = sortedSongs()
            if (cf) {
                coverSongs = items
                coverGroups = emptyList()
                coverIsSongs = true
                coverFlow.setItems(items.map { CoverItem(it.title, it.artist, it) })
                coverTitle = k.label
                inCoverFlow = true
                render()
            } else {
                push(songsMenu(k.label, items))
            }
            return
        }
        val gs = groupsFor(k)
        if (cf) {
            coverGroups = gs
            coverSongs = emptyList()
            coverIsSongs = false
            coverFlow.setItems(gs.map { CoverItem(it.name, it.sub, it.songs.firstOrNull()) })
            coverTitle = k.label
            inCoverFlow = true
            render()
        } else {
            push(groupsMenu(k.label, gs))
        }
    }

    private fun groupsMenu(title: String, gs: List<Group>): Menu {
        val rows = gs.map { g -> Row(g.name, arrow = true) { push(songsMenu(g.name, g.songs)) } }
        return Menu(title, if (rows.isEmpty()) listOf(Row("Empty") {}) else rows)
    }

    private fun songsMenu(title: String, items: List<Song>): Menu {
        val rows = items.mapIndexed { i, s -> Row(s.title, s.artist) { play(items, i) } }
        return Menu(title, if (rows.isEmpty()) listOf(Row("No songs") {}) else rows)
    }

    private fun settingsMenu(): Menu = Menu("Settings", listOf(
        Row("Theme", arrow = true) { push(themeMenu()) },
        Row("Controller Style", arrow = true) { push(controllerMenu()) },
        Row("Cover Flow", arrow = true) { push(coverFlowSettingsMenu()) },
        Row("Sort", arrow = true) { push(sortMenu()) },
        Row(FOLDER_MENU, arrow = true) { push(musicFolderMenu()) },
        Row("Click Sound", if (clickSoundOn) "On" else "Off") {
            val on = !clickSoundOn
            prefs.edit().putBoolean("clickSound", on).apply()
            refreshTop(settingsMenu())
            if (on) playClick()
        },
        Row("About", arrow = true) { push(aboutMenu()) }
    ))

    /** Choose a folder to read music from, rescan it, or go back to the phone's library. */
    private fun musicFolderMenu(): Menu {
        val folder = Library.folderUri(this)
        val rows = ArrayList<Row>()
        rows.add(Row("Choose Folder", if (folder != null) Library.folderName(folder) else null) { pickFolder() })
        if (folder != null) {
            rows.add(
                Row(
                    "Rescan Folder",
                    if (Library.scanning) "Scanning " + Library.scanned else Library.folderCount.toString() + " songs"
                ) { if (!Library.scanning) startScan() }
            )
        }
        rows.add(Row("Phone Library", if (folder == null) "\u2713" else null) { usePhoneLibrary() })
        return Menu(FOLDER_MENU, rows)
    }

    private fun themeMenu(): Menu {
        val rows = Theme.all.mapIndexed { i, t ->
            Row(t.name, if (i == themeIndex) "\u2713" else null) {
                themeIndex = i
                prefs.edit().putInt("themeIndex", i).apply()
                applyTheme()
                refreshTop(themeMenu())
            }
        }
        return Menu("Theme", rows, themeIndex)
    }

    private fun controllerMenu(): Menu {
        val rows = WheelStyle.all.mapIndexed { i, s ->
            Row(s.name, if (i == wheelIndex) "\u2713" else null) {
                wheelIndex = i
                prefs.edit().putInt("wheelIndex", i).apply()
                wheel.style = WheelStyle.all[i]
                refreshTop(controllerMenu())
            }
        }
        return Menu("Controller Style", rows, wheelIndex)
    }

    /** Press center on a row to switch it between In Line and Cover Flow. */
    private fun coverFlowSettingsMenu(): Menu {
        val rows = Kind.values().map { k ->
            Row(k.label, if (viewMode(k) == 1) "Cover Flow" else "In Line") {
                prefs.edit().putInt("view_" + k.key, if (viewMode(k) == 1) 0 else 1).apply()
                refreshTop(coverFlowSettingsMenu())
            }
        }
        return Menu("Cover Flow", rows)
    }

    private fun sortMenu(): Menu {
        val rows = sortNames.mapIndexed { i, n ->
            Row(n, if (i == songSort) "\u2713" else null) {
                prefs.edit().putInt("songSort", i).apply()
                refreshTop(sortMenu())
            }
        }
        return Menu("Sort", rows, songSort)
    }

    private fun aboutMenu(): Menu {
        songs = Library.songs(this)
        var version = "1.0"
        try {
            version = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
        }
        val info = listOf(
            "Songs" to songs.size.toString(),
            "Artists" to songs.map { it.artist }.toSet().size.toString(),
            "Albums" to songs.map { it.album }.toSet().size.toString(),
            "Version" to version,
            "Software" to "Android " + Build.VERSION.RELEASE,
            "Model" to Build.MODEL,
            "Made by" to "SN STUDIO \uD83D\uDC99"
        )
        return Menu("About", info.map { (a, b) -> Row(a, b) {} })
    }

    private fun extrasMenu(): Menu = Menu("Extras", listOf(
        Row("Brick Game") {
            inGame = true
            game.newGame()
            render()
        }
    ))

    private fun refreshTop(m: Menu) {
        if (stack.isEmpty()) return
        m.sel = stack[stack.size - 1].sel
        stack[stack.size - 1] = m
        render()
    }

    // ---------------------------------------------------------------- playback
    private fun play(items: List<Song>, i: Int) {
        engine.play(items, i)
        showNP()
    }

    private fun shuffleAll() {
        songs = Library.songs(this)
        if (songs.isEmpty()) return
        play(songs.shuffled(), 0)
    }

    private fun changeVolume(s: Int) {
        val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val stepSize = max(1, mx / 16)
        val v = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + s * stepSize).coerceIn(0, mx)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
    }

    private fun playClick() {
        am.playSoundEffect(AudioManager.FX_KEY_CLICK)
        wheel.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // ---------------------------------------------------------------- navigation
    private fun push(m: Menu) {
        stack.add(m)
        render()
    }

    private fun menuButton() {
        when {
            inNowPlaying -> inNowPlaying = false
            inGame -> inGame = false
            inCoverFlow -> inCoverFlow = false
            stack.size > 1 -> stack.removeAt(stack.size - 1)
        }
        render()
    }

    private fun showNP() {
        inNowPlaying = true
        render()
    }

    private fun step(s: Int) {
        if (inGame && !inNowPlaying) return
        if (clickSoundOn) playClick()
        if (inNowPlaying) {
            if (scrubMode) engine.seekBy(s * 5000) else changeVolume(s)
            updateNP()
        } else if (inCoverFlow) {
            coverFlow.move(s)
        } else if (stack.isNotEmpty()) {
            val m = stack[stack.size - 1]
            m.sel = (m.sel + s).coerceIn(0, max(0, m.rows.size - 1))
            render()
        }
    }

    private fun center() {
        if (inNowPlaying) {
            scrubMode = !scrubMode
            updateNP()
            return
        }
        if (inGame) {
            game.press()
            return
        }
        if (inCoverFlow) {
            if (coverIsSongs) {
                play(coverSongs, coverFlow.index)
            } else {
                val g = coverGroups.getOrNull(coverFlow.index)
                if (g != null && g.songs.isNotEmpty()) play(g.songs, 0)
            }
            return
        }
        val m = stack.lastOrNull() ?: return
        if (m.sel < m.rows.size) m.rows[m.sel].action()
    }

    // ---------------------------------------------------------------- render
    private fun render() {
        val showTable = !inNowPlaying && !inGame && !inCoverFlow
        list.visibility = if (showTable) View.VISIBLE else View.GONE
        npView.visibility = if (inNowPlaying) View.VISIBLE else View.GONE
        coverFlow.visibility = if (inCoverFlow && !inNowPlaying) View.VISIBLE else View.GONE
        game.visibility = if (inGame && !inNowPlaying) View.VISIBLE else View.GONE
        if (inGame) game.start() else game.stop()
        titleLabel.text = when {
            inNowPlaying -> "Now Playing"
            inGame -> "Brick Game"
            inCoverFlow -> coverTitle
            else -> stack.lastOrNull()?.title ?: "iPod"
        }
        if (inNowPlaying) {
            updateNP()
        } else if (showTable) {
            adapter.notifyDataSetChanged()
            val m = stack.lastOrNull()
            if (m != null && m.rows.isNotEmpty()) {
                val changed = m !== lastMenu
                lastMenu = m
                list.post {
                    val vis = max(1, list.height / dp(34))
                    if (changed) {
                        list.setSelection(if (m.sel < vis) 0 else m.sel - vis + 1)
                    } else {
                        val first = list.firstVisiblePosition
                        if (m.sel < first) list.setSelection(m.sel)
                        else if (m.sel > first + vis - 1) list.setSelection(m.sel - vis + 1)
                    }
                }
            }
        }
    }

    private fun updateStatus() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (i != null) {
            val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            battery.level = if (lvl >= 0 && scale > 0) lvl.toFloat() / scale else 0f
            battery.charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                    st == BatteryManager.BATTERY_STATUS_FULL
        }
        clockLabel.text = DateFormat.getTimeFormat(this).format(Date())
    }

    private fun updateNP() {
        updateStatus()
        indicator.playing = engine.isPlaying
        if (!inNowPlaying) return
        val mx = max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        volBar.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / mx
        modeLabel.text = if (scrubMode) "Wheel: Seek (press center)" else "Wheel: Volume (press center)"
        val s = engine.current
        npTitle.text = s?.title ?: "Nothing Playing"
        npArtist.text = if (s == null) "" else s.artist + " - " + s.album
        if (s?.id != npArtId) {
            npArtId = s?.id
            art.setImageBitmap(null)
            if (s != null) {
                Art.load(this, s, dp(200)) { b ->
                    if (npArtId == s.id) art.setImageBitmap(b)
                }
            }
        }
        val d = engine.duration
        val c = engine.position
        progress.progress = if (d > 0) c.toFloat() / d else 0f
        t1.text = fmt(c)
        t2.text = "-" + fmt(max(0, d - c))
    }

    private fun fmt(ms: Int): String {
        val sec = max(0, ms) / 1000
        return String.format("%d:%02d", sec / 60, sec % 60)
    }
}
