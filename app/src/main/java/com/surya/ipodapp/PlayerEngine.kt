package com.surya.ipodapp

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager

/** Small queue-based player on top of MediaPlayer. */
class PlayerEngine(private val ctx: Context) {
    private var mp: MediaPlayer? = null
    private var prepared = false

    var queue: List<Song> = emptyList()
        private set
    var index = -1
        private set
    var onChange: (() -> Unit)? = null

    val current: Song? get() = queue.getOrNull(index)

    val isPlaying: Boolean
        get() = prepared && try { mp?.isPlaying == true } catch (e: Exception) { false }

    val position: Int
        get() = if (prepared) try { mp?.currentPosition ?: 0 } catch (e: Exception) { 0 } else 0

    val duration: Int get() = current?.duration?.toInt() ?: 0

    fun play(list: List<Song>, i: Int) {
        if (i < 0 || i >= list.size) return
        queue = list
        index = i
        start()
    }

    private fun hasNext() = index + 1 < queue.size

    private fun start() {
        release()
        val s = current ?: return
        val p = MediaPlayer()
        mp = p
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(ctx, s.uri)
            p.setOnPreparedListener {
                prepared = true
                it.start()
                onChange?.invoke()
            }
            p.setOnCompletionListener { next() }
            p.setOnErrorListener { _, _, _ ->
                if (hasNext()) next() else stop()
                true
            }
            p.prepareAsync()
        } catch (e: Exception) {
            if (hasNext()) next() else stop()
        }
        onChange?.invoke()
    }

    fun next() {
        if (hasNext()) {
            index++
            start()
        } else {
            stop()
        }
    }

    fun prev() {
        if (position > 3000 || index <= 0) {
            seekTo(0)
        } else {
            index--
            start()
        }
    }

    fun toggle() {
        val p = mp
        if (p != null && prepared) {
            try {
                if (p.isPlaying) p.pause() else p.start()
            } catch (e: Exception) {
            }
            onChange?.invoke()
        } else if (current != null) {
            start()
        }
    }

    fun seekTo(ms: Int) {
        val p = mp
        if (p != null && prepared) {
            try {
                p.seekTo(ms.coerceIn(0, maxOf(0, duration)))
            } catch (e: Exception) {
            }
        }
    }

    fun seekBy(ms: Int) = seekTo(position + ms)

    fun stop() {
        release()
        onChange?.invoke()
    }

    fun release() {
        prepared = false
        try {
            mp?.release()
        } catch (e: Exception) {
        }
        mp = null
    }
}
