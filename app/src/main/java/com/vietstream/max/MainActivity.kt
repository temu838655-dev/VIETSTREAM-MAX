
package com.vietstream.max

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

private val Black = Color(0xFF080808)
private val Panel = Color(0xFF191919)
private val Red = Color(0xFFE50914)
private val White = Color(0xFFF5F5F5)
private val Gray = Color(0xFFAAAAAA)

data class Movie(
    val name: String,
    val slug: String,
    val poster: String,
    val year: String,
    val origin: String,
    val description: String = ""
)

data class EpisodeSource(
    val name: String,
    val slug: String,
    val m3u8: String,
    val embed: String
)

data class EpisodeServer(
    val name: String,
    val episodes: List<EpisodeSource>
)

interface MovieApi {
    @GET("v1/api/home")
    suspend fun home(): JsonObject

    @GET("v1/api/danh-sach/{type}")
    suspend fun list(
        @Path("type") type: String,
        @Query("page") page: Int = 1
    ): JsonObject

    @GET("v1/api/tim-kiem")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 30
    ): JsonObject

    @GET("phim/{slug}")
    suspend fun detail(@Path("slug") slug: String): JsonObject
}

object MovieService {
    val api: MovieApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://phimapi.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MovieApi::class.java)
    }
}

fun jsonText(obj: JsonObject, key: String): String {
    return try {
        obj.get(key)?.takeIf { !it.isJsonNull }?.asString ?: ""
    } catch (_: Exception) {
        ""
    }
}

private fun resolvePoster(raw: String, imageRoot: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""

    return when {
        value.startsWith("https://", true) -> value
        value.startsWith("http://", true) -> value
        value.startsWith("//") -> "https:$value"
        else -> "${imageRoot.trimEnd('/')}/${value.trimStart('/')}"
    }
}

fun parseMovies(root: JsonObject): List<Movie> {
    val data = try {
        root.getAsJsonObject("data")
    } catch (_: Exception) {
        null
    }

    val items = when {
        root.get("items")?.isJsonArray == true -> root.getAsJsonArray("items")
        data?.get("items")?.isJsonArray == true -> data.getAsJsonArray("items")
        else -> null
    } ?: return emptyList()

    val imageRoot = jsonText(root, "pathImage")
        .ifBlank { jsonText(data ?: JsonObject(), "pathImage") }
        .ifBlank { "https://phimapi.com/uploads/movies" }

    return items.mapNotNull { element ->
        try {
            val item = element.asJsonObject
            val name = jsonText(item, "name")
            val slug = jsonText(item, "slug")
            val rawPoster = jsonText(item, "poster_url")
                .ifBlank { jsonText(item, "thumb_url") }

            if (name.isBlank() || slug.isBlank()) null
            else Movie(
                name = name,
                slug = slug,
                poster = resolvePoster(rawPoster, imageRoot),
                year = jsonText(item, "year"),
                origin = jsonText(item, "origin_name"),
                description = jsonText(item, "content")
            )
        } catch (_: Exception) {
            null
        }
    }
}

fun parseEpisodeServers(root: JsonObject): List<EpisodeServer> {
    val episodes = try {
        root.getAsJsonArray("episodes")
    } catch (_: Exception) {
        null
    } ?: return emptyList()

    return episodes.mapNotNull { element ->
        try {
            val server = element.asJsonObject
            val serverName = jsonText(server, "server_name")
                .ifBlank { "Máy chủ" }

            val serverData = server.getAsJsonArray("server_data")
            val parsed = serverData.mapNotNull { episodeElement ->
                try {
                    val episode = episodeElement.asJsonObject
                    EpisodeSource(
                        name = jsonText(episode, "name"),
                        slug = jsonText(episode, "slug"),
                        m3u8 = jsonText(episode, "link_m3u8"),
                        embed = jsonText(episode, "link_embed")
                    )
                } catch (_: Exception) {
                    null
                }
            }

            if (parsed.isEmpty()) null
            else EpisodeServer(serverName, parsed)
        } catch (_: Exception) {
            null
        }
    }
}

// ---------------- LIVE TV ----------------

data class TvChannel(
    val name: String,
    val country: String,
    val category: String,
    val description: String,
    val url: String
)

private val officialChannels = listOf(
    TvChannel("VTV1", "Việt Nam", "Tin tức", "Kênh thời sự và chính luận của VTV",
        "https://vtv.vn/truyen-hinh-truc-tuyen/vtv1.htm"),
    TvChannel("VTVgo", "Việt Nam", "Tổng hợp", "Nền tảng truyền hình trực tuyến của VTV",
        "https://vtvgo.vn/"),
    TvChannel("VTV4", "Việt Nam", "Quốc tế", "Kênh truyền hình đối ngoại Việt Nam",
        "https://vtv4.vtv.vn/xem-vtv4-moi-luc-moi-noi.html"),
    TvChannel("Vietnam Today", "Việt Nam", "Tin tức", "Kênh đối ngoại quốc gia",
        "https://vietnamtoday.vtv.vn/"),
    TvChannel("Al Jazeera English", "Quốc tế", "Tin tức", "Tin tức quốc tế",
        "https://www.aljazeera.com/video/live"),
    TvChannel("TRT World", "Quốc tế", "Tin tức", "Tin tức thế giới",
        "https://www.trtworld.com/live"),
    TvChannel("France 24", "Quốc tế", "Tin tức", "Kênh tin tức quốc tế của Pháp",
        "https://www.france24.com/en/live"),
    TvChannel("DW News", "Quốc tế", "Tin tức", "Kênh tin tức quốc tế của Đức",
        "https://www.dw.com/en/live-tv/s-100825"),
    TvChannel("NHK World-Japan", "Quốc tế", "Tin tức", "Truyền hình quốc tế Nhật Bản",
        "https://www3.nhk.or.jp/nhkworld/en/live/")
)

@Composable
fun LiveTvScreen() {
    val context = LocalContext.current
    var region by remember { mutableStateOf("Tất cả") }
    var search by remember { mutableStateOf("") }

    val regions = listOf("Tất cả", "Việt Nam", "Quốc tế")
    val filtered = officialChannels.filter {
        (region == "Tất cả" || it.country == region) &&
            (it.name.contains(search, true) ||
                it.category.contains(search, true))
    }

    Column(
        Modifier.fillMaxSize().background(Black).padding(horizontal = 16.dp)
    ) {
        Text("TRUYỀN HÌNH TRỰC TUYẾN", color = Red,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Live TV", color = White, fontSize = 27.sp,
            fontWeight = FontWeight.Black)

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Tìm tên kênh...") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            regions.forEach {
                FilterChip(
                    selected = region == it,
                    onClick = { region = it },
                    label = { Text(it) }
                )
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            filtered.forEach { channel ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Panel)
                        .clickable {
                            context.startActivity(
                                Intent(context, InAppWebActivity::class.java).apply {
                                    putExtra("web_title", channel.name)
                                    putExtra("web_url", channel.url)
                                }
                            )
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(48.dp)
                            .background(Color(0xFF303030), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(channel.name.take(3).uppercase(),
                            color = Red, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, color = White, fontWeight = FontWeight.Bold)
                        Text("${channel.country} • ${channel.category}",
                            color = Red, fontSize = 11.sp)
                        Text(channel.description, color = Gray, fontSize = 11.sp)
                    }
                    Text("▶", color = Red)
                }
            }
        }
    }
}

// ---------------- MAIN ACTIVITY ----------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Red,
                    background = Black,
                    surface = Panel
                )
            ) {
                VietStreamApp()
            }
        }
    }
}

@Composable
fun VietStreamApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("vietstream_library", android.content.Context.MODE_PRIVATE)
    }

    var tab by remember { mutableStateOf("Trang chủ") }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var selected by remember { mutableStateOf<Movie?>(null) }
    var keyword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var detailText by remember { mutableStateOf("") }
    var episodeServers by remember { mutableStateOf<List<EpisodeServer>>(emptyList()) }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var history by remember {
        mutableStateOf(prefs.getStringSet("history", emptySet())?.toList() ?: emptyList())
    }

    fun toggleFavorite(movie: Movie) {
        val updated = favorites.toMutableSet()
        if (!updated.add(movie.slug)) updated.remove(movie.slug)
        favorites = updated
        prefs.edit().putStringSet("favorites", updated).apply()
    }

    suspend fun loadMovies(section: String) {
        loading = true
        error = ""

        try {
            movies = when (section) {
                "Phim lẻ" -> parseMovies(MovieService.api.list("phim-le"))
                "Phim bộ" -> parseMovies(MovieService.api.list("phim-bo"))
                "Hoạt hình" -> parseMovies(MovieService.api.list("hoat-hinh"))
                "Yêu thích" -> {
                    val response = MovieService.api.home()
                    parseMovies(response).filter { it.slug in favorites }
                }
                "Lịch sử" -> {
                    val response = MovieService.api.home()
                    parseMovies(response).filter { it.slug in history }
                }
                else -> parseMovies(MovieService.api.home())
            }

            if (movies.isEmpty()) error = "Chưa có dữ liệu cho danh mục này."
        } catch (e: Exception) {
            error = "Không thể tải phim: ${e.message ?: "Lỗi kết nối"}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(tab, favorites, history) {
        if (tab != "Live TV" && tab != "Tìm kiếm") loadMovies(tab)
    }

    LaunchedEffect(selected?.slug) {
        val movie = selected ?: return@LaunchedEffect
        detailText = "Đang tải thông tin phim..."
        episodeServers = emptyList()

        try {
            val response = MovieService.api.detail(movie.slug)
            val info = try { response.getAsJsonObject("movie") } catch (_: Exception) { null }

            detailText = info?.let { jsonText(it, "content") }
                .orEmpty()
                .replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ")
                .trim()
                .ifBlank { movie.description.ifBlank { "Chưa có giới thiệu." } }

            episodeServers = parseEpisodeServers(response)
            if (episodeServers.isEmpty()) {
                detailText += "\n\nChưa có máy chủ hoặc tập phim khả dụng."
            }
        } catch (e: Exception) {
            detailText = "Không tải được chi tiết phim: ${e.message ?: "Lỗi kết nối"}"
        }
    }

    Scaffold(
        containerColor = Black,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf("Trang chủ", "Phim lẻ", "Phim bộ", "Hoạt hình", "Live TV").forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Text(
                                when (item) {
                                    "Trang chủ" -> "⌂"
                                    "Phim lẻ" -> "▶"
                                    "Phim bộ" -> "▣"
                                    "Hoạt hình" -> "★"
                                    else -> "TV"
                                },
                                color = if (tab == item) Red else Gray
                            )
                        },
                        label = { Text(item, fontSize = 9.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = White,
                            unselectedTextColor = Gray,
                            indicatorColor = Color(0xFF382020)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(Black).padding(padding)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("N", color = Red, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Column {
                    Row {
                        Text("VIET", color = Red, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("STREAM", color = White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    }
                    Text("MAX", color = Gray, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { tab = "Tìm kiếm" }) {
                    Text("⌕ Tìm kiếm", color = White)
                }
            }

            if (tab == "Live TV") {
                LiveTvScreen()
            } else {
                if (tab == "Tìm kiếm") {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        singleLine = true,
                        placeholder = { Text("Nhập tên phim...") },
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    if (keyword.isNotBlank()) {
                                        scope.launch {
                                            loading = true
                                            error = ""
                                            try {
                                                movies = parseMovies(
                                                    MovieService.api.search(keyword.trim())
                                                )
                                                if (movies.isEmpty()) error = "Không tìm thấy phim."
                                            } catch (e: Exception) {
                                                error = "Tìm kiếm thất bại: ${e.message}"
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    }
                                }
                            ) { Text("Tìm", color = Red) }
                        }
                    )
                }

                if (tab == "Yêu thích" || tab == "Lịch sử") {
                    Text(
                        tab,
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                if (tab == "Trang chủ" && movies.isNotEmpty()) {
                    val hero = movies.first()
                    Box(
                        Modifier.fillMaxWidth().height(225.dp).padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(16.dp)).clickable { selected = hero }
                    ) {
                        AsyncImage(
                            model = hero.poster,
                            contentDescription = hero.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                                )
                            )
                        )
                        Column(
                            Modifier.align(Alignment.BottomStart).padding(16.dp)
                        ) {
                            Text("VIETSTREAM MAX", color = Red, fontSize = 10.sp,
                                fontWeight = FontWeight.Black)
                            Text(hero.name, color = White, fontSize = 22.sp,
                                fontWeight = FontWeight.Black, maxLines = 2)
                            Text("${hero.year} • ${hero.origin}", color = Gray, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { selected = hero },
                                colors = ButtonDefaults.buttonColors(containerColor = Red)
                            ) { Text("▶  Xem ngay") }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (tab) {
                            "Trang chủ" -> "Phim mới cập nhật"
                            "Tìm kiếm" -> "Kết quả tìm kiếm"
                            else -> tab
                        },
                        color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    if (loading) CircularProgressIndicator(
                        Modifier.size(20.dp), color = Red, strokeWidth = 2.dp
                    )
                }

                when {
                    loading -> Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Red) }

                    error.isNotBlank() -> Text(
                        error, color = Red, modifier = Modifier.padding(18.dp)
                    )

                    movies.isEmpty() -> Text(
                        "Chưa có dữ liệu phim.", color = Gray,
                        modifier = Modifier.padding(18.dp)
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 145.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        gridItems(movies, key = { it.slug }) { movie ->
                            MovieCard(
                                movie = movie,
                                favorite = movie.slug in favorites
                            ) { selected = movie }
                        }
                    }
                }

                if (tab == "Tìm kiếm") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { tab = "Yêu thích" }) {
                            Text("♥ Yêu thích", color = Red)
                        }
                        TextButton(onClick = { tab = "Lịch sử" }) {
                            Text("◷ Lịch sử", color = White)
                        }
                    }
                }
            }
        }
    }

    selected?.let { movie ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Panel,
            title = {
                Text(movie.name, color = White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                ) {
                    if (movie.poster.isNotBlank()) {
                        AsyncImage(
                            model = movie.poster,
                            contentDescription = movie.name,
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text("${movie.origin} • ${movie.year}", color = Red,
                        modifier = Modifier.padding(vertical = 8.dp))
                    Text(detailText, color = White, fontSize = 13.sp)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { toggleFavorite(movie) }) {
                            Text(
                                if (movie.slug in favorites) "♥ Đã yêu thích"
                                else "♡ Yêu thích",
                                color = Red
                            )
                        }
                        TextButton(onClick = {
                            selected = null
                            tab = "Lịch sử"
                        }) {
                            Text("Lịch sử xem", color = White)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Gray.copy(alpha = 0.3f))
                    Text(
                        "CHỌN MÁY CHỦ VÀ TẬP",
                        color = Red, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    if (episodeServers.isEmpty()) {
                        Text("Chưa có tập phim khả dụng.", color = Gray, fontSize = 12.sp)
                    }

                    episodeServers.forEach { server ->
                        Text(
                            server.name, color = White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        server.episodes.forEach { episode ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF292929))
                                    .clickable {
                                        val videoUrl = episode.m3u8.trim()
                                        val embedUrl = episode.embed.trim()

                                        if (videoUrl.startsWith("https://", true) ||
                                            videoUrl.startsWith("http://", true)
                                        ) {
                                            val playlist = server.episodes.filter {
                                                it.m3u8.startsWith("https://", true) ||
                                                    it.m3u8.startsWith("http://", true)
                                            }
                                            val index = playlist.indexOfFirst {
                                                it.slug == episode.slug
                                            }.coerceAtLeast(0)

                                            val recent = (listOf(movie.slug) + history)
                                                .distinct().take(100)
                                            history = recent
                                            prefs.edit().putStringSet(
                                                "history", recent.toSet()
                                            ).apply()

                                            context.startActivity(
                                                Intent(
                                                    context,
                                                    InAppPlayerActivity::class.java
                                                ).apply {
                                                    putExtra(
                                                        "video_title",
                                                        "${movie.name} - ${episode.name}"
                                                    )
                                                    putStringArrayListExtra(
                                                        "episode_titles",
                                                        ArrayList(playlist.map {
                                                            "${movie.name} - ${it.name}"
                                                        })
                                                    )
                                                    putStringArrayListExtra(
                                                        "episode_urls",
                                                        ArrayList(playlist.map { it.m3u8.trim() })
                                                    )
                                                    putExtra("episode_index", index)
                                                }
                                            )
                                            selected = null
                                        } else if (
                                            embedUrl.startsWith("https://", true) ||
                                            embedUrl.startsWith("http://", true)
                                        ) {
                                            val recent = (listOf(movie.slug) + history)
                                                .distinct().take(100)
                                            history = recent
                                            prefs.edit().putStringSet(
                                                "history", recent.toSet()
                                            ).apply()

                                            context.startActivity(
                                                Intent(
                                                    context,
                                                    InAppWebActivity::class.java
                                                ).apply {
                                                    putExtra(
                                                        "web_title",
                                                        "${movie.name} - ${episode.name}"
                                                    )
                                                    putExtra("web_url", embedUrl)
                                                }
                                            )
                                            selected = null
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Tập này chưa có đường dẫn phát.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("▶", color = Red)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    episode.name.ifBlank { "Tập phim" },
                                    color = White, modifier = Modifier.weight(1f)
                                )
                                Text("Xem", color = Red, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text("Đóng", color = Red)
                }
            }
        )
    }
}

@Composable
fun MovieCard(
    movie: Movie,
    favorite: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Box {
            AsyncImage(
                model = movie.poster.ifBlank { null },
                contentDescription = movie.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                    .clip(RoundedCornerShape(10.dp)).background(Panel),
                contentScale = ContentScale.Crop
            )

            if (movie.year.isNotBlank()) {
                Text(
                    movie.year, color = White, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            if (favorite) {
                Text(
                    "♥", color = Red, fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(movie.name, color = White, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2)
        Text(movie.origin, color = Gray, fontSize = 10.sp, maxLines = 1)
    }
}
