package com.vietstream.max

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Background = Color(0xFF090D18)
private val SurfaceDark = Color(0xFF151D2D)
private val Accent = Color(0xFF21C7A8)

data class VideoItem(
    val title: String,
    val category: String,
    val description: String,
    val url: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Background,
                    surface = SurfaceDark
                )
            ) {
                VietStreamApp()
            }
        }
    }
}

@Composable
fun VietStreamApp() {
    var selectedTab by remember { mutableStateOf("Trang chủ") }
    var playingItem by remember { mutableStateOf<VideoItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var customVideos by remember { mutableStateOf(listOf<VideoItem>()) }

    val demoVideos = listOf(
        VideoItem(
            "Big Buck Bunny",
            "Phim mẫu",
            "Video mẫu công khai để kiểm tra trình phát.",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        VideoItem(
            "Elephants Dream",
            "Phim mẫu",
            "Video mẫu công khai thứ hai.",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        VideoItem(
            "For Bigger Escapes",
            "Video giải trí",
            "Video mẫu kiểm tra khả năng phát.",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
        )
    )

    val allVideos = demoVideos + customVideos

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                val tabs = listOf(
                    "Trang chủ" to Icons.Default.Home,
                    "Live TV" to Icons.Default.LiveTv,
                    "Phim" to Icons.Default.Movie,
                    "Trình duyệt" to Icons.Default.Public,
                    "Cài đặt" to Icons.Default.Settings
                )

                tabs.forEach { (name, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == name,
                        onClick = { selectedTab = name },
                        icon = { Icon(icon, contentDescription = name) },
                        label = { Text(name, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            when (selectedTab) {
                "Trang chủ" -> HomeScreen(
                    videos = allVideos,
                    onPlay = { playingItem = it },
                    onAdd = { showAddDialog = true }
                )

                "Live TV" -> LiveScreen(
                    onAdd = { showAddDialog = true },
                    onPlay = { playingItem = it }
                )

                "Phim" -> MovieScreen(
                    videos = allVideos,
                    onPlay = { playingItem = it },
                    onAdd = { showAddDialog = true }
                )

                "Trình duyệt" -> BrowserScreen()

                "Cài đặt" -> SettingsScreen()
            }
        }
    }

    if (showAddDialog) {
        AddVideoDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, url ->
                customVideos = customVideos + VideoItem(
                    title = title,
                    category = "Nội dung của tôi",
                    description = "Nguồn video do người dùng thêm.",
                    url = url
                )
                showAddDialog = false
            }
        )
    }

    playingItem?.let { item ->
        VideoPlayerDialog(
            item = item,
            onDismiss = { playingItem = null }
        )
    }
}

@Composable
fun HomeScreen(
    videos: List<VideoItem>,
    onPlay: (VideoItem) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    "VIETSTREAM MAX",
                    color = Accent,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Giải trí của bạn, trong một ứng dụng",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .background(
                        Color(0xFF183B48),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "YOUR ENTERTAINMENT HUB",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Một thế giới giải trí",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Phim • Truyền hình • Video cá nhân",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Button(onClick = onAdd) {
                        Text("Thêm nội dung")
                    }
                }
            }
        }

        item {
            SectionTitle("Nội dung nổi bật")
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos) { video ->
                    VideoCard(video = video, onClick = { onPlay(video) })
                }
            }
        }

        item {
            SectionTitle("Khám phá")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryCard("Live TV", "Truyền hình", Modifier.weight(1f))
                CategoryCard("Movies", "Phim và video", Modifier.weight(1f))
            }
        }

        item {
            Text(
                "Lưu ý: Nội dung hiển thị hiện là video mẫu và nguồn do bạn tự thêm. Ứng dụng chưa tích hợp kho phim hoặc kênh truyền hình có bản quyền.",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun LiveScreen(
    onAdd: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("Live TV")
        Text(
            "Thêm đường dẫn luồng truyền hình mà bạn có quyền sử dụng.",
            color = Color.LightGray
        )
        Button(onClick = onAdd) {
            Text("＋ Thêm nguồn phát")
        }
        Text(
            "Hỗ trợ các định dạng phổ biến như HLS (.m3u8), DASH (.mpd) và video MP4 tùy nguồn phát.",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun MovieScreen(
    videos: List<VideoItem>,
    onPlay: (VideoItem) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("Thư viện phim")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdd) {
                Text("＋ Thêm phim hoặc video")
            }
        }

        items(videos) { video ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(14.dp))
                    .clickable { onPlay(video) }
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(video.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(video.category, color = Accent, fontSize = 12.sp)
                }
                Text("▶", color = Accent, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun BrowserScreen() {
    var address by remember { mutableStateOf("https://www.google.com") }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Địa chỉ website") }
            )
            Button(onClick = {
                val input = address.trim()
                val url = if (
                    input.startsWith("https://") ||
                    input.startsWith("http://")
                ) input else "https://$input"

                if (url.startsWith("https://")) {
                    currentUrl = url
                }
            }) {
                Text("Đi")
            }
        }

        Spacer(Modifier.height(10.dp))

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode =
                        android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl(currentUrl)
                }
            },
            update = { webView ->
                if (webView.url != currentUrl) {
                    webView.loadUrl(currentUrl)
                }
            }
        )
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("Cài đặt")
        SettingRow("Phiên bản", "1.0.0 MVP")
        SettingRow("Giao diện", "Tối")
        SettingRow("Nền tảng", "Android / Android TV")
        SettingRow("Trình phát", "Media3 ExoPlayer")
        SettingRow("Nguồn nội dung", "Nguồn hợp pháp do người dùng thêm")
    }
}

@Composable
fun SettingRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Color.White)
        Text(value, color = Accent, fontSize = 12.sp)
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun CategoryCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Accent, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(95.dp)
                .background(
                    Color(0xFF263B50),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = Accent, fontSize = 34.sp)
        }

        Spacer(Modifier.height(10.dp))
        Text(video.title, color = Color.White, fontWeight = FontWeight.Bold)
        Text(video.category, color = Accent, fontSize = 11.sp)
    }
}

@Composable
fun AddVideoDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm nguồn video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên nội dung") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Đường dẫn HTTPS") },
                    placeholder = { Text("https://...") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (
                        title.isNotBlank() &&
                        url.trim().startsWith("https://")
                    ) {
                        onAdd(title.trim(), url.trim())
                    }
                }
            ) {
                Text("Thêm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun VideoPlayerDialog(
    item: VideoItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val player = remember(item.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(item.title, color = Color.White)
        },
        text = {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                update = { it.player = player }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        },
        containerColor = SurfaceDark
    )
}
