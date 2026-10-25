package com.vietstream.max

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@UnstableApi
class InAppPlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView

    private var titles = arrayListOf<String>()
    private var urls = arrayListOf<String>()
    private var startIndex = 0

    private val preferences by lazy {
        getSharedPreferences("vietstream_playback", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val singleTitle = intent.getStringExtra("video_title") ?: "VIETSTREAM MAX"
        val singleUrl = intent.getStringExtra("video_url").orEmpty()

        titles = intent.getStringArrayListExtra("episode_titles") ?: arrayListOf()
        urls = intent.getStringArrayListExtra("episode_urls") ?: arrayListOf()
        startIndex = intent.getIntExtra("episode_index", 0)

        if (urls.isEmpty() && singleUrl.isNotBlank()) {
            urls.add(singleUrl)
            titles.add(singleTitle)
        }

        if (urls.isEmpty()) {
            Toast.makeText(this, "Không có đường dẫn video để phát", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (titles.size != urls.size) {
            titles = ArrayList(urls.indices.map { "Tập ${it + 1}" })
        }

        startIndex = startIndex.coerceIn(0, urls.lastIndex)

        buildInterface()
        initializePlayer()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        titleView = TextView(this).apply {
            text = titles.getOrElse(startIndex) { "VIETSTREAM MAX" }
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(6, 8, 6, 8)
        }

        controls.addView(makeButton("Tập trước") { previousEpisode() })
        controls.addView(makeButton("Tốc độ") { showSpeedOptions() })
        controls.addView(makeButton("Phụ đề") { showSubtitleOptions() })
        controls.addView(makeButton("Tập tiếp") { nextEpisode() })

        root.addView(titleView)
        root.addView(playerView)
        root.addView(controls)

        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
    }

    private fun initializePlayer() {
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer

        val mediaItems = urls.mapIndexed { index, url ->
            MediaItem.Builder()
                .setUri(url)
                .setMediaId(index.toString())
                .build()
        }

        exoPlayer.setMediaItems(mediaItems, startIndex, 0L)

        exoPlayer.addListener(object : Player.Listener {

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                val index = exoPlayer.currentMediaItemIndex
                titleView.text = titles.getOrElse(index) { "Tập ${index + 1}" }
                saveProgress()
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@InAppPlayerActivity,
                    "Không phát được video: ${error.errorCodeName}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (exoPlayer.hasNextMediaItem()) {
                        exoPlayer.seekToNextMediaItem()
                        exoPlayer.play()
                    }
                }
            }
        })

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun showSpeedOptions() {
        val speeds = arrayOf(
            "0.5x", "0.75x", "1.0x", "1.25x",
            "1.5x", "1.75x", "2.0x"
        )
        val values = floatArrayOf(
            0.5f, 0.75f, 1f, 1.25f,
            1.5f, 1.75f, 2f
        )

        AlertDialog.Builder(this)
            .setTitle("Tốc độ phát")
            .setItems(speeds) { _, which ->
                player?.setPlaybackSpeed(values[which])
            }
            .show()
    }

    private fun showSubtitleOptions() {
        val exoPlayer = player ?: return
        val tracks = exoPlayer.currentTracks
        val subtitleTracks = tracks.groups.filter {
            it.type == C.TRACK_TYPE_TEXT
        }

        val options = mutableListOf("Tắt phụ đề")
        val selections = mutableListOf<Pair<Int, Int>>()

        subtitleTracks.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val language = format.language ?: "Không rõ ngôn ngữ"
                options.add("$language - Phụ đề ${trackIndex + 1}")
                selections.add(groupIndex to trackIndex)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Chọn phụ đề")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                } else {
                    val (groupIndex, trackIndex) = selections[which - 1]
                    val group = subtitleTracks[groupIndex]
                    val override = TrackSelectionOverride(
                        group.mediaTrackGroup,
                        listOf(trackIndex)
                    )

                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(override)
                            .build()
                }
            }
            .show()
    }

    private fun previousEpisode() {
        val exoPlayer = player ?: return
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.play()
        } else {
            Toast.makeText(this, "Đây là tập đầu tiên", Toast.LENGTH_SHORT).show()
        }
    }

    private fun nextEpisode() {
        val exoPlayer = player ?: return
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else {
            Toast.makeText(this, "Đây là tập cuối cùng", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProgress() {
        val exoPlayer = player ?: return
        val index = exoPlayer.currentMediaItemIndex
        if (index < 0) return

        preferences.edit()
            .putString("last_title", titles.getOrElse(index) { "VIETSTREAM MAX" })
            .putString("last_url", urls.getOrNull(index))
            .putInt("last_index", index)
            .putLong("last_position", exoPlayer.currentPosition)
            .apply()
    }

    override fun onPause() {
        saveProgress()
        player?.pause()
        super.onPause()
    }

    override fun onStop() {
        saveProgress()
        super.onStop()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        saveProgress()
        super.onBackPressed()
    }
}
