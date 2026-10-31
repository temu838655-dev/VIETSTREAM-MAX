
package com.vietstream.max

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

@UnstableApi
class InAppPlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var fullscreenExitButton: Button
    private lateinit var controlOverlay: FrameLayout
    private lateinit var progressBar: SeekBar
    private lateinit var positionLabel: TextView
    private lateinit var durationLabel: TextView
    private lateinit var playPauseButton: Button
    private var userSeeking = false

    private var titles = arrayListOf<String>()
    private var urls = arrayListOf<String>()
    private var startIndex = 0
    private var restoredPosition = 0L
    private var finishedPlayback = false
    private var isFullscreen = false
    private var originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private val mainHandler = Handler(Looper.getMainLooper())

    private val hideFullscreenControlsRunnable = Runnable {
        if (isFullscreen && !isFinishing &&
            ::playerView.isInitialized &&
            player?.isPlaying == true
        ) {
            controlOverlay.visibility = View.GONE
            fullscreenExitButton.visibility = View.GONE
        }
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            val exoPlayer = player
            if (exoPlayer != null && ::progressBar.isInitialized) {
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                val position = exoPlayer.currentPosition.coerceAtLeast(0L)
                progressBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
                if (!userSeeking) progressBar.progress = position.coerceAtMost(progressBar.max.toLong()).toInt()
                positionLabel.text = formatTime(position)
                durationLabel.text = formatTime(duration)
                if (::playPauseButton.isInitialized) playPauseButton.text = if (exoPlayer.isPlaying) "Ⅱ" else "▶"
            }
            mainHandler.postDelayed(this, 500L)
        }
    }

    private val preferences by lazy {
        getSharedPreferences("vietstream_playback", MODE_PRIVATE)
    }

    private val settings by lazy {
        getSharedPreferences("vietstream_settings", MODE_PRIVATE)
    }

    private val isTv: Boolean
        get() = packageManager.hasSystemFeature(
            PackageManager.FEATURE_LEANBACK
        ) || resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        originalRequestedOrientation = requestedOrientation
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()

        val singleTitle =
            intent.getStringExtra("video_title") ?: "VIETSTREAM MAX"

        val singleUrl =
            intent.getStringExtra("video_url").orEmpty()

        titles =
            intent.getStringArrayListExtra("episode_titles") ?: arrayListOf()

        urls =
            intent.getStringArrayListExtra("episode_urls") ?: arrayListOf()

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
        applySubtitleStyle()
    }

    private fun enableImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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
            setBackgroundColor(Color.BLACK)
            isFocusable = true
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                if (isTv) 12 else 4,
                4,
                if (isTv) 12 else 4,
                4
            )
            setBackgroundColor(Color.argb(220, 8, 8, 8))
        }

        val backButton = Button(this).apply {
            text = "‹ Quay lại"
            textSize = if (isTv) 17f else 12f
            isFocusable = true

            setOnClickListener {
                saveProgress()
                finish()
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        titleView = TextView(this).apply {
            text = titles.getOrElse(startIndex) { "VIETSTREAM MAX" }
            textSize = if (isTv) 22f else 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 12, 8, 12)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val fullscreenButton = Button(this).apply {
            text = "Toàn màn hình"
            textSize = if (isTv) 15f else 10f
            isFocusable = true
            setOnClickListener { setFullscreen(true) }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        topBar.addView(backButton)
        topBar.addView(titleView)
        topBar.addView(fullscreenButton)

        playerView = PlayerView(this).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val playerContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        playerContainer.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        fullscreenExitButton = Button(this).apply {
            text = "✕ Thoát toàn màn hình"
            textSize = if (isTv) 15f else 11f
            visibility = View.GONE
            isFocusable = true

            setOnClickListener {
                setFullscreen(false)
            }

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = if (isTv) 16 else 8
                marginEnd = if (isTv) 16 else 8
            }
        }

        playerContainer.addView(fullscreenExitButton)

        controlOverlay = buildCustomControls()
        playerContainer.addView(
            controlOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenExitButton.bringToFront()
        playerView.setOnClickListener { toggleCustomControls() }
        controlOverlay.setOnClickListener { toggleCustomControls() }

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                if (isTv) 16 else 3,
                if (isTv) 12 else 5,
                if (isTv) 16 else 3,
                if (isTv) 12 else 5
            )
            setBackgroundColor(Color.argb(235, 8, 8, 8))
        }

        val buttonLabels = listOf(
            "Tập trước",
            "Danh sách tập",
            "Tốc độ",
            "Âm thanh",
            "Dolby / Spatial",
            "Phụ đề",
            "Kiểu chữ",
            "Tập tiếp"
        )

        val actions = listOf<() -> Unit>(
            { previousEpisode() },
            { showEpisodeList() },
            { showSpeedOptions() },
            { showAudioTrackOptions() },
            { showAudioModeOptions() },
            { showSubtitleOptions() },
            { showSubtitleStyleOptions() },
            { nextEpisode() }
        )

        buttonLabels.forEachIndexed { index, label ->
            controls.addView(makeButton(label, actions[index]))
        }

        root.addView(topBar)
        root.addView(playerContainer)
        val controlsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(controls, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(controlsScroll)

        setContentView(root)

        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (!::fullscreenExitButton.isInitialized) {
                    return@ControllerVisibilityListener
                }

                if (isFullscreen && visibility == View.VISIBLE) {
                    fullscreenExitButton.visibility = View.VISIBLE
                    scheduleFullscreenControlsHide()
                } else {
                    fullscreenExitButton.visibility = View.GONE
                    mainHandler.removeCallbacks(
                        hideFullscreenControlsRunnable
                    )
                }
            }
        )

        mainHandler.post(updateProgressRunnable)
        playerView.requestFocus()
    }

    private fun buildCustomControls(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = false
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
        fun controlButton(label: String, action: () -> Unit, isMain: Boolean = false): Button = Button(this).apply {
            text = label
            textSize = if (isMain) { if (isTv) 34f else 30f } else { if (isTv) 29f else 25f }
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isMain) Color.argb(245, 190, 20, 32) else Color.argb(225, 18, 18, 18))
                cornerRadius = dp(48).toFloat()
            }
            elevation = dp(8).toFloat()
            setOnClickListener { action() }
            val side = if (isTv) 96 else 82
            val height = if (isTv) 88 else 76
            layoutParams = LinearLayout.LayoutParams(dp(if (isMain) side + 12 else side), dp(if (isMain) height + 8 else height)).apply {
                marginStart = dp(8); marginEnd = dp(8)
            }
        }
        center.addView(controlButton("↶ 10", { player?.seekBack() }))
        playPauseButton = controlButton("▶", {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }, isMain = true)
        center.addView(playPauseButton)
        center.addView(controlButton("10 ↷", { player?.seekForward() }))
        overlay.addView(center, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 8, 18, 12)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(225, 0, 0, 0))
            )
        }
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        positionLabel = TextView(this).apply { text = "00:00"; setTextColor(Color.WHITE); textSize = 14f; setShadowLayer(3f, 1f, 1f, Color.BLACK) }
        durationLabel = TextView(this).apply { text = "00:00"; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.END; setShadowLayer(3f, 1f, 1f, Color.BLACK) }
        progressBar = SeekBar(this).apply {
            max = 1
            minimumHeight = (32 * resources.displayMetrics.density).toInt()
            setPadding(8, 8, 8, 8)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) { userSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar) { player?.seekTo(seekBar.progress.toLong()); userSeeking = false }
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) positionLabel.text = formatTime(progress.toLong())
                }
            })
        }
        times.addView(positionLabel)
        times.addView(progressBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(durationLabel)
        bottom.addView(times)
        overlay.addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        return overlay
    }

    private fun formatTime(milliseconds: Long): String {
        val total = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0) String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun toggleCustomControls() {
        if (!::controlOverlay.isInitialized) return
        controlOverlay.visibility = if (controlOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (isFullscreen && controlOverlay.visibility == View.VISIBLE) scheduleFullscreenControlsHide()
    }

    private fun scheduleFullscreenControlsHide() {
        mainHandler.removeCallbacks(hideFullscreenControlsRunnable)

        if (isFullscreen && player?.isPlaying == true) {
            mainHandler.postDelayed(
                hideFullscreenControlsRunnable,
                1000L
            )
        }
    }

    private fun setFullscreen(enabled: Boolean) {
        if (isFullscreen == enabled) {
            if (enabled) {
                controlOverlay.visibility = View.VISIBLE
                fullscreenExitButton.visibility = View.VISIBLE
                scheduleFullscreenControlsHide()
            }
            return
        }

        isFullscreen = enabled

        // Phones must rotate to landscape when fullscreen is requested.
        // Android TV devices keep their existing orientation behavior.
        if (!isTv) {
            requestedOrientation = if (enabled) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                originalRequestedOrientation
            }
        }

        mainHandler.removeCallbacks(hideFullscreenControlsRunnable)

        topBar.visibility = if (enabled) View.GONE else View.VISIBLE
        controls.visibility = if (enabled) View.GONE else View.VISIBLE

        if (enabled) {
            fullscreenExitButton.visibility = View.VISIBLE
            controlOverlay.visibility = View.VISIBLE
            scheduleFullscreenControlsHide()
        } else {
            fullscreenExitButton.visibility = View.GONE
            controlOverlay.visibility = View.VISIBLE

            titleView.text = titles.getOrElse(
                player?.currentMediaItemIndex ?: startIndex
            ) {
                "VIETSTREAM MAX"
            }

            playerView.requestFocus()
        }
    }

    private fun makeButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = if (isTv) 14f else 9f
            setTextColor(Color.WHITE)
            isAllCaps = false
            isFocusable = true
            isFocusableInTouchMode = true
            minHeight = if (isTv) 64 else 44
            minWidth = 0
            setPadding(4, 0, 4, 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(185, 48, 48, 48))
                cornerRadius = 18f
                setStroke(1, Color.argb(70, 255, 255, 255))
            }

            setOnClickListener {
                action()
            }

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = if (isTv) 6 else 1
                marginEnd = if (isTv) 6 else 1
            }
        }
    }

    private fun initializePlayer() {
        val audioMode = settings.getString("app_audio", AppSettings.AUDIO_AUTO)
            ?: AppSettings.AUDIO_AUTO
        val selector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().apply {
                when (audioMode) {
                    AppSettings.AUDIO_STEREO -> setMaxAudioChannelCount(2)
                    AppSettings.AUDIO_DOLBY -> setPreferredAudioMimeTypes(
                        MimeTypes.AUDIO_E_AC3_JOC,
                        MimeTypes.AUDIO_AC4,
                        MimeTypes.AUDIO_E_AC3
                    )
                    AppSettings.AUDIO_SPATIAL -> setPreferredAudioMimeTypes(
                        MimeTypes.AUDIO_E_AC3_JOC,
                        MimeTypes.AUDIO_AC4,
                        MimeTypes.AUDIO_E_AC3,
                        MimeTypes.AUDIO_AC3,
                        MimeTypes.AUDIO_AAC
                    )
                }
            }.build()
        }
        trackSelector = selector
        val exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(selector)
            .build()

        // Android handles Atmos passthrough/spatialization when source and output support it.
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true)

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

        exoPlayer.addListener(object : Player.Listener {

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                finishedPlayback = false

                val index = exoPlayer.currentMediaItemIndex

                titleView.text = titles.getOrElse(index) {
                    "Tập ${index + 1}"
                }

                applySubtitleStyle()
                saveProgress()
            }

            // HLS subtitle tracks may arrive after playback preparation.
            // Re-apply the user's saved appearance when Media3 publishes tracks.
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                applySubtitleStyle()
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
                    finishedPlayback = true
                    clearFinishedProgress()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && isFullscreen) {
                    mainHandler.removeCallbacks(
                        hideFullscreenControlsRunnable
                    )
                    controlOverlay.visibility = View.VISIBLE
                    fullscreenExitButton.visibility = View.VISIBLE
                } else if (isPlaying && isFullscreen) {
                    scheduleFullscreenControlsHide()
                }
            }
        })

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun showEpisodeList() {
        if (titles.isEmpty()) return
        val current = player?.currentMediaItemIndex ?: startIndex
        AlertDialog.Builder(this)
            .setTitle("Danh sách tập")
            .setSingleChoiceItems(titles.toTypedArray(), current) { dialog, which ->
                val exoPlayer = player
                if (exoPlayer != null && which in urls.indices) {
                    finishedPlayback = false
                    exoPlayer.seekTo(which, 0L)
                    exoPlayer.play()
                    titleView.text = titles[which]
                    saveProgress()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showAudioModeOptions() {
        val support = try {
            AudioSupport.detect(this)
        } catch (_: Throwable) {
            AudioSupport(false, false, false)
        }
        val modes = arrayOf("Tự động (khuyến nghị)", "Stereo", "Dolby Atmos", "Spatial 360°")
        val values = arrayOf(AppSettings.AUDIO_AUTO, AppSettings.AUDIO_STEREO, AppSettings.AUDIO_DOLBY, AppSettings.AUDIO_SPATIAL)
        val current = settings.getString("app_audio", AppSettings.AUDIO_AUTO) ?: AppSettings.AUDIO_AUTO
        val selected = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Chế độ âm thanh")
            .setSingleChoiceItems(modes, selected) { dialog, which ->
                val mode = values[which]
                AppSettings.saveAudioMode(this, mode)
                val selector = trackSelector
                selector?.parameters = selector.buildUponParameters().apply {
                    when (mode) {
                        AppSettings.AUDIO_STEREO -> setMaxAudioChannelCount(2)
                        AppSettings.AUDIO_DOLBY -> {
                            setMaxAudioChannelCount(Int.MAX_VALUE)
                            setPreferredAudioMimeTypes(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC4, MimeTypes.AUDIO_E_AC3)
                        }
                        AppSettings.AUDIO_SPATIAL -> {
                            setMaxAudioChannelCount(Int.MAX_VALUE)
                            setPreferredAudioMimeTypes(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC4, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_AAC)
                        }
                        else -> {
                            setMaxAudioChannelCount(Int.MAX_VALUE)
                            setPreferredAudioMimeTypes()
                        }
                    }
                }.build()
                Toast.makeText(this, when (mode) {
                    AppSettings.AUDIO_DOLBY -> if (support.dolbyAtmos) "Đã ưu tiên track Dolby; cần nguồn Atmos và thiết bị tương thích." else "Thiết bị chưa báo hỗ trợ Atmos; sẽ phát định dạng tương thích."
                    AppSettings.AUDIO_SPATIAL -> if (support.spatialReady) "Spatializer Android đang sẵn sàng." else "Spatial 360° chưa được thiết bị/đường âm thanh hỗ trợ."
                    else -> "Đã đổi chế độ âm thanh."
                }, Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showAudioTrackOptions() {
        val exoPlayer = player ?: return
        val audioGroups = exoPlayer.currentTracks.groups.filter {
            it.type == C.TRACK_TYPE_AUDIO
        }
        val options = mutableListOf("Tự động")
        val selections = mutableListOf<Pair<Int, Int>>()

        audioGroups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val language = format.language ?: "Không rõ ngôn ngữ"
                val label = format.label ?: "Âm thanh ${trackIndex + 1}"
                options.add("$language - $label")
                selections.add(groupIndex to trackIndex)
            }
        }

        if (options.size == 1) {
            Toast.makeText(
                this,
                "Nguồn phim không có lựa chọn âm thanh khác",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Âm thanh")
            .setItems(options.toTypedArray()) { _, which ->
                val builder = exoPlayer.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)

                if (which > 0) {
                    val (groupIndex, trackIndex) = selections[which - 1]
                    builder.addOverride(
                        TrackSelectionOverride(
                            audioGroups[groupIndex].mediaTrackGroup,
                            listOf(trackIndex)
                        )
                    )
                }
                exoPlayer.trackSelectionParameters = builder.build()
            }
            .setNegativeButton("Đóng", null)
            .show()
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
                val language = format.language ?: "Không rõ ngôn ngữ"
                val label = format.label ?: "Phụ đề ${trackIndex + 1}"

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
                    val (groupIndex, trackIndex) =
                        selections[which - 1]

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

    private fun showSubtitleStyleOptions() {
        val options = arrayOf(
            "Cỡ chữ phụ đề",
            "Màu chữ phụ đề"
        )

        AlertDialog.Builder(this)
            .setTitle("Tùy chỉnh phụ đề")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSubtitleSizeOptions()
                    1 -> showSubtitleColorOptions()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showSubtitleSizeOptions() {
        val sizes = intArrayOf(
            14, 16, 18, 20, 24, 28, 32
        )

        val labels = sizes.map { "$it sp" }.toTypedArray()

        val currentSize = settings.getInt("subtitle_size", 20)
        val selectedIndex = sizes.indexOf(currentSize).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Cỡ chữ phụ đề")
            .setSingleChoiceItems(
                labels,
                selectedIndex
            ) { dialog, which ->
                settings.edit()
                    .putInt("subtitle_size", sizes[which])
                    .apply()

                applySubtitleStyle()
                dialog.dismiss()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showSubtitleColorOptions() {
        val colors = intArrayOf(
            Color.WHITE,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.RED,
            Color.MAGENTA
        )

        val names = arrayOf(
            "Trắng",
            "Vàng",
            "Xanh lá",
            "Xanh dương nhạt",
            "Đỏ",
            "Hồng tím"
        )

        val currentColor = settings.getInt(
            "subtitle_color",
            Color.WHITE
        )

        val selectedIndex =
            colors.indexOf(currentColor).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Màu chữ phụ đề")
            .setSingleChoiceItems(
                names,
                selectedIndex
            ) { dialog, which ->
                settings.edit()
                    .putInt("subtitle_color", colors[which])
                    .apply()

                applySubtitleStyle()
                dialog.dismiss()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun applySubtitleStyle() {
        if (!::playerView.isInitialized) return

        val size = settings.getInt("subtitle_size", 20)
        val color = settings.getInt("subtitle_color", Color.WHITE)

        playerView.subtitleView?.apply {
            setFixedTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                size.toFloat()
            )

            setStyle(
                CaptionStyleCompat(
                    color,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null
                )
            )
        }
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
                titles.getOrElse(index) { "VIETSTREAM MAX" }
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

    override fun onResume() {
        super.onResume()
        applySubtitleStyle()
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
        mainHandler.removeCallbacksAndMessages(null)
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
        if (isFullscreen) {
            setFullscreen(false)
            return
        }

        saveProgress()
        super.onBackPressed()
    }
}
