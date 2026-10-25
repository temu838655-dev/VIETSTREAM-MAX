
package com.vietstream.max

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

private const val TEST_HLS_URL =
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

class InAppPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val title = intent.getStringExtra("video_title")
            ?: "VIETSTREAM MAX"

        val videoUrl = intent.getStringExtra("video_url")
            ?.takeIf { it.isNotBlank() }
            ?: TEST_HLS_URL

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE50914),
                    background = Color.Black,
                    surface = Color(0xFF191919)
                )
            ) {
                InAppVideoPlayerScreen(
                    title = title,
                    streamUrl = videoUrl,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        window.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
fun InAppVideoPlayerScreen(
    title: String,
    streamUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var playbackError by remember {
        mutableStateOf<String?>(null)
    }

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
        }
    }

    BackHandler {
        onBack()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError =
                    error.message ?: "Không thể phát video này."
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(streamUrl, player) {
        playbackError = null

        try {
            player.setMediaItem(
                MediaItem.fromUri(streamUrl)
            )
            player.prepare()
            player.playWhenReady = true
        } catch (error: Exception) {
            playbackError =
                error.message ?: "Không thể tải video."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101010))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = Color.White
                )
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(
                            PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                        )
                        setKeepContentOnPlayerReset(true)
                    }
                },
                update = { view ->
                    view.player = player
                }
            )

            if (playbackError == null &&
                player.playbackState == Player.STATE_BUFFERING
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFE50914)
                )
            }

            playbackError?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Không thể phát video",
                        color = Color(0xFFE50914),
                        fontSize = 18.sp
                    )

                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Text(
            text = "VIETSTREAM MAX • Trình phát Media3",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(10.dp)
        )
    }
}
