package com.vietstream.max

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

    private var restoredPosition = 0L
    private var finishedPlayback = false

    private val preferences by lazy {
        getSharedPreferences("vietstream_playback", MODE_PRIVATE)
    }

    private val isTv: Boolean
        get() = packageManager.hasSystemFeature(
            PackageManager.FEATURE_LEANBACK
        ) || resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        enableImmersiveMode()

        val singleTitle =
            intent.getStringExtra("video_title") ?: "VIETSTREAM MAX"

        val singleUrl =
            intent.getStringExtra("video_url").orEmpty()

        titles = intent.getStringArrayListExtra("episode_titles")
            ?: arrayListOf()

        urls = intent.getStringArrayListExtra("episode_urls")
            ?: arrayListOf()

        startIndex = intent.getIntExtra("episode_index", 0)

        if (urls.isEmpty() && singleUrl.isNotBlank()) {
            urls.add(singleUrl)
            titles.add(singleTitle)
        }

        val validEpisodes = urls.mapIndexedNotNull { index, url ->
            if (url.isBlank()) {
                null
            } else {
                Pair(
                    titles.getOrElse(index) { "Tập ${index + 1}" },
                    url.trim()
                )
            }
        }

        urls = ArrayList(validEpisodes.map { it.second })
        titles = ArrayList(validEpisodes.map { it.first })

        if (urls.isEmpty()) {
            Toast.makeText(
                this,
                "Không có đường dẫn video hợp lệ",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        startIndex = startIndex.coerceIn(0, urls.lastIndex)

        restoreLastPosition()
        buildInterface()
        initializePlayer()
    }

    private fun enableImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun restoreLastPosition() {
        val lastUrl = preferences.getString("last_url", null)
        val lastPosition = preferences.getLong("last_position", 0L)

        if (lastUrl != null) {
            val savedIndex = urls.indexOf(lastUrl)

            if (savedIndex >= 0) {
                startIndex = savedIndex
                restoredPosition = lastPosition.coerceAtLeast(0L)
            }
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            isFocusable = true
        }

        titleView = TextView(this).apply {
            text = titles.getOrElse(startIndex) {
                "VIETSTREAM MAX"
            }

            textSize = if (isTv) 22f else 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                if (isTv) 28 else 16,
                if (isTv) 18 else 12,
                if (isTv) 28 else 16,
                if (isTv) 18 else 12
            )
            maxLines = 1
        }

        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            controllerHideOnTouch = !isTv

            setShowBuffering(
                PlayerView.SHOW_BUFFERING_WHEN_PLAYING
            )

            setBackgroundColor(android.graphics.Color.BLACK)

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            isFocusable = true
            isFocusableInTouchMode = true
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                if (isTv) 20 else 6,
                if (isTv) 14 else 8,
                if (isTv) 20 else 6,
                if (isTv) 14 else 8
            )
            setBackgroundColor(
                android.graphics.Color.rgb(15, 15, 15)
            )
        }

        val buttonLabels = listOf(
            "Tập trước",
            "Tốc độ",
            "Phụ đề",
            "Tập tiếp"
        )

        val actions = listOf<() -> Unit>(
            { previousEpisode() },
            { showSpeedOptions() },
            { showSubtitleOptions() },
            { nextEpisode() }
        )

        buttonLabels.forEachIndexed { index, label ->
            controls.addView(
                makeButton(label, actions[index])
            )
        }

        root.addView(titleView)
        root.addView(playerView)
        root.addView(controls)

        setContentView(root)

        playerView.requestFocus()
    }

    private fun makeButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = if (isTv) 16f else 11f
            isFocusable = true
            isFocusableInTouchMode = true
            minHeight = if (isTv) 64 else 48
            minWidth = 0

            setOnClickListener {
                action()
            }

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = if (isTv) 8 else 2
                marginEnd = if (isTv) 8 else 2
            }
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

        exoPlayer.setMediaItems(
            mediaItems,
            startIndex,
            restoredPosition
        )

        exoPlayer.addListener(
            object : Player.Listener {

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    finishedPlayback = false

                    val index = exoPlayer.currentMediaItemIndex

                    titleView.text = titles.getOrElse(index) {
                        "Tập ${index + 1}"
                    }

                    saveProgress()
                }

                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    Toast.makeText(
                        this@InAppPlayerActivity,
                        "Không phát được video: ${error.errorCodeName}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    if (playbackState == Player.STATE_ENDED) {
                        finishedPlayback = true
                        clearFinishedProgress()
                    }
                }
            }
        )

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun showSpeedOptions() {
        val speeds = arrayOf(
            "0.5x",
            "0.75x",
            "1.0x",
            "1.25x",
            "1.5x",
            "1.75x",
            "2.0x"
        )

        val values = floatArrayOf(
            0.5f,
            0.75f,
            1f,
            1.25f,
            1.5f,
            1.75f,
            2f
        )

        AlertDialog.Builder(this)
            .setTitle("Tốc độ phát")
            .setItems(speeds) { _, which ->
                player?.setPlaybackSpeed(values[which])
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showSubtitleOptions() {
        val exoPlayer = player ?: return

        val subtitleTracks = exoPlayer.currentTracks.groups.filter {
            it.type == C.TRACK_TYPE_TEXT
        }

        val options = mutableListOf("Tắt phụ đề")
        val selections = mutableListOf<Pair<Int, Int>>()

        subtitleTracks.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)

                val language = format.language
                    ?: "Không rõ ngôn ngữ"

                val label = format.label
                    ?: "Phụ đề ${trackIndex + 1}"

                options.add("$language - $label")
                selections.add(groupIndex to trackIndex)
            }
        }

        if (options.size == 1) {
            Toast.makeText(
                this,
                "Video hiện không có phụ đề tích hợp",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Chọn phụ đề")
            .setItems(options.toTypedArray()) { _, which ->

                val builder = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)

                if (which == 0) {
                    builder.setTrackTypeDisabled(
                        C.TRACK_TYPE_TEXT,
                        true
                    )
                } else {
                    val (groupIndex, trackIndex) = selections[which - 1]
                    val group = subtitleTracks[groupIndex]

                    val override = TrackSelectionOverride(
                        group.mediaTrackGroup,
                        listOf(trackIndex)
                    )

                    builder
                        .setTrackTypeDisabled(
                            C.TRACK_TYPE_TEXT,
                            false
                        )
                        .addOverride(override)
                }

                exoPlayer.trackSelectionParameters = builder.build()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun previousEpisode() {
        val exoPlayer = player ?: return

        if (exoPlayer.hasPreviousMediaItem()) {
            finishedPlayback = false
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.play()
        } else {
            Toast.makeText(
                this,
                "Đây là tập đầu tiên",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun nextEpisode() {
        val exoPlayer = player ?: return

        if (exoPlayer.hasNextMediaItem()) {
            finishedPlayback = false
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else {
            Toast.makeText(
                this,
                "Đây là tập cuối cùng",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun saveProgress() {
        val exoPlayer = player ?: return

        if (finishedPlayback) {
            clearFinishedProgress()
            return
        }

        val index = exoPlayer.currentMediaItemIndex

        if (index < 0 || index >= urls.size) return

        preferences.edit()
            .putString(
                "last_title",
                titles.getOrElse(index) {
                    "VIETSTREAM MAX"
                }
            )
            .putString("last_url", urls[index])
            .putInt("last_index", index)
            .putLong(
                "last_position",
                exoPlayer.currentPosition.coerceAtLeast(0L)
            )
            .apply()
    }

    private fun clearFinishedProgress() {
        preferences.edit()
            .remove("last_title")
            .remove("last_url")
            .remove("last_index")
            .remove("last_position")
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
        saveProgress()

        if (::playerView.isInitialized) {
            playerView.player = null
        }

        player?.release()
        player = null

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        super.onDestroy()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        saveProgress()
        super.onBackPressed()
    }
}
