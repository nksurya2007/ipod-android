package com.surya.ipodapp

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.util.concurrent.Executors

class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val dateAdded: Long,
    val duration: Long,
    val track: Int,
    val uri: Uri
)

/** An artist, album or playlist: a name plus its songs. */
class Group(val name: String, val sub: String, val songs: List<Song>)

object Library {
    private fun clean(s: String?, fallback: String): String =
        if (s == null || s.isBlank() || s == "<unknown>") fallback else s

    private val trackOrder = compareBy<Song>({ it.album.lowercase() }, { it.track }, { it.title.lowercase() })

    // Many songs (Downloads, WhatsApp, Bluetooth, SD card) are not flagged IS_MUSIC,
    // so accept any audio file that is not a ringtone/alarm/notification and is 30s+ long.
    private val SONG_FILTER =
        "(" + MediaStore.Audio.Media.IS_MUSIC + " != 0 OR (" +
            MediaStore.Audio.Media.IS_RINGTONE + " = 0 AND " +
            MediaStore.Audio.Media.IS_NOTIFICATION + " = 0 AND " +
            MediaStore.Audio.Media.IS_ALARM + " = 0 AND " +
            MediaStore.Audio.Media.DURATION + " >= 30000))"

    // ------------------------------------------------------------ music folder (Storage Access Framework)
    private const val PREFS = "ipod"
    private const val KEY_FOLDER = "musicFolder"
    private val ui = Handler(Looper.getMainLooper())
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "amr")

    @Volatile private var folderSongs: List<Song> = emptyList()
    @Volatile private var gen = 0

    @Volatile
    var scanning = false
        private set

    @Volatile
    var scanned = 0
        private set

    val folderCount: Int get() = folderSongs.size

    /** The folder the user picked, or null when the phone's MediaStore library is used. */
    fun folderUri(ctx: Context): Uri? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FOLDER, null)?.let { Uri.parse(it) }

    /** Pass null to go back to the phone library. */
    fun setFolder(ctx: Context, uri: Uri?) {
        val old = folderUri(ctx)
        if (old != null && old != uri) {
            try {
                ctx.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
        }
        val ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (uri == null) ed.remove(KEY_FOLDER) else ed.putString(KEY_FOLDER, uri.toString())
        ed.apply()
        gen++
        folderSongs = emptyList()
        scanned = 0
        scanning = false
    }

    fun folderName(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/')
            .ifBlank { "Storage" }
    } catch (e: Exception) {
        "Folder"
    }

    /** Scans the picked folder on a background thread. [onDone] runs on the main thread. */
    fun scan(ctx: Context, onDone: () -> Unit) {
        val tree = folderUri(ctx) ?: return
        val app = ctx.applicationContext
        val g = ++gen
        scanning = true
        scanned = 0
        Thread {
            val out = ArrayList<Song>()
            try {
                walk(app, tree, DocumentsContract.getTreeDocumentId(tree), out, g)
            } catch (e: Exception) {
            }
            if (g == gen) {
                folderSongs = out.sortedBy { it.title.lowercase() }
                scanning = false
                ui.post(onDone)
            }
        }.start()
    }

    private class Doc(val id: String, val name: String, val modified: Long)

    private fun walk(ctx: Context, tree: Uri, docId: String, out: MutableList<Song>, g: Int) {
        if (g != gen) return
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val dirs = ArrayList<String>()
        val files = ArrayList<Doc>()
        try {
            ctx.contentResolver.query(
                kids,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        dirs.add(id)
                    } else if (mime.startsWith("audio/") || mime == "application/ogg" ||
                        name.substringAfterLast('.', "").lowercase() in AUDIO_EXT
                    ) {
                        files.add(Doc(id, name, c.getLong(3)))
                    }
                }
            }
        } catch (e: Exception) {
        }
        for (f in files) {
            if (g != gen) return
            out.add(readSong(ctx, DocumentsContract.buildDocumentUriUsingTree(tree, f.id), f))
            scanned = out.size
        }
        for (d in dirs) walk(ctx, tree, d, out, g)
    }

    private fun readSong(ctx: Context, uri: Uri, f: Doc): Song {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration = 0L
        var track = 0
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            track = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
        } catch (e: Exception) {
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
            }
        }
        // Negative ids never collide with MediaStore ids.
        val id = -(uri.toString().hashCode().toLong() and 0x7fffffffL) - 1
        return Song(
            id,
            clean(title, f.name.substringBeforeLast('.')),
            clean(artist, "Unknown Artist"),
            clean(album, "Unknown Album"),
            -1L, f.modified / 1000, duration, track, uri
        )
    }

    /** Songs from the picked folder if one is set, otherwise from the phone's media database. */
    fun songs(ctx: Context): List<Song> =
        if (folderUri(ctx) != null) folderSongs.sortedBy { it.title.lowercase() } else phoneSongs(ctx)

    private fun phoneSongs(ctx: Context): List<Song> {
        val out = ArrayList<Song>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK
        )
        try {
            ctx.contentResolver.query(base, proj, SONG_FILTER, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out.add(
                        Song(
                            id,
                            clean(c.getString(1), "Unknown"),
                            clean(c.getString(2), "Unknown Artist"),
                            clean(c.getString(3), "Unknown Album"),
                            c.getLong(4), c.getLong(5), c.getLong(6), c.getInt(7),
                            ContentUris.withAppendedId(base, id)
                        )
                    )
                }
            }
        } catch (e: Exception) {
        }
        return out.sortedBy { it.title.lowercase() }
    }

    fun artists(all: List<Song>): List<Group> =
        all.groupBy { it.artist }
            .map { (k, v) -> Group(k, "${v.size} songs", v.sortedWith(trackOrder)) }
            .sortedBy { it.name.lowercase() }

    fun albums(all: List<Song>): List<Group> =
        all.groupBy { it.album }
            .map { (k, v) ->
                val s = v.sortedWith(trackOrder)
                Group(k, s.first().artist, s)
            }
            .sortedBy { it.name.lowercase() }

    /** Best effort: Android only exposes playlists stored in MediaStore. */
    @Suppress("DEPRECATION")
    fun playlists(ctx: Context, all: List<Song>): List<Group> {
        val byId = all.associateBy { it.id }
        val out = ArrayList<Group>()
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val pid = c.getLong(0)
                    val name = c.getString(1) ?: "Playlist"
                    val songs = ArrayList<Song>()
                    try {
                        ctx.contentResolver.query(
                            MediaStore.Audio.Playlists.Members.getContentUri("external", pid),
                            arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),
                            null, null, MediaStore.Audio.Playlists.Members.PLAY_ORDER
                        )?.use { m ->
                            while (m.moveToNext()) {
                                byId[m.getLong(0)]?.let { songs.add(it) }
                            }
                        }
                    } catch (e: Exception) {
                    }
                    if (songs.isNotEmpty()) out.add(Group(name, "${songs.size} songs", songs))
                }
            }
        } catch (e: Exception) {
        }
        return out.sortedBy { it.name.lowercase() }
    }
}

/** Album artwork loader with a small cache, loading off the main thread. */
object Art {
    private val cache = LruCache<String, Bitmap>(40)
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, s: Song, size: Int, cb: (Bitmap?) -> Unit) {
        val key = s.id.toString() + "@" + size
        val hit = cache.get(key)
        if (hit != null) {
            cb(hit)
            return
        }
        val app = ctx.applicationContext
        pool.execute {
            val b = fetch(app, s, size)
            if (b != null) cache.put(key, b)
            main.post { cb(b) }
        }
    }

    private fun square(b: Bitmap, size: Int): Bitmap =
        if (b.width == size && b.height == size) b else Bitmap.createScaledBitmap(b, size, size, true)

    private fun fetch(ctx: Context, s: Song, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return ctx.contentResolver.loadThumbnail(s.uri, Size(size, size), null)
            } catch (e: Exception) {
            }
        }
        try {
            val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.albumId)
            ctx.contentResolver.openInputStream(uri)?.use { st ->
                val bmp = BitmapFactory.decodeStream(st)
                if (bmp != null) return square(bmp, size)
            }
        } catch (e: Exception) {
        }
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, s.uri)
                val data = r.embeddedPicture
                if (data != null) {
                    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bmp != null) return square(bmp, size)
                }
            } finally {
                r.release()
            }
        } catch (e: Exception) {
        }
        return null
    }
}
