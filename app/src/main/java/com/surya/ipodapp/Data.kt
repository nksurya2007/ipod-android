package com.surya.ipodapp

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    fun songs(ctx: Context): List<Song> {
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
