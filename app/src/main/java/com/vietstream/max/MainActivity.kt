
package com.vietstream.max

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val Black = Color(0xFF090909)
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

    // Endpoint chi tiết phim có episodes/server_data
    @GET("phim/{slug}")
    suspend fun detail(
        @Path("slug") slug: String
    ): JsonObject
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
        obj.get(key)
            ?.takeIf { !it.isJsonNull }
            ?.asString ?: ""
    } catch (_: Exception) {
        ""
    }
}

fun parseMovies(root: JsonObject): List<Movie> {
    val data = try {
        root.getAsJsonObject("data")
    } catch (_: Exception) {
        null
    }

    val items = when {
        root.get("items")?.isJsonArray == true ->
            root.getAsJsonArray("items")

        data?.get("items")?.isJsonArray == true ->
            data.getAsJsonArray("items")

        else -> null
    } ?: return emptyList()

    val imageRoot = jsonText(root, "pathImage")
        .ifBlank {
            "https://phimapi.com/uploads/movies/"
        }

    return items.mapNotNull { element ->
        try {
            val item = element.asJsonObject
            val name = jsonText(item, "name")
            val slug = jsonText(item, "slug")

            val rawPoster = jsonText(item, "poster_url")
                .ifBlank {
                    jsonText(item, "thumb_url")
                }

            val poster = when {
                rawPoster.startsWith("http") -> rawPoster
                rawPoster.isNotBlank() -> "$imageRoot$rawPoster"
                else -> ""
            }

            if (name.isBlank() || slug.isBlank()) {
                null
            } else {
                Movie(
                    name = name,
                    slug = slug,
                    poster = poster,
                    year = jsonText(item, "year"),
                    origin = jsonText(item, "origin_name"),
                    description = jsonText(item, "content")
                )
            }
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

    return episodes.mapNotNull { serverElement ->
        try {
            val server = serverElement.asJsonObject
            val serverName = jsonText(server, "server_name")
                .ifBlank { "Máy chủ" }

            val serverData = server.getAsJsonArray("server_data")

            val parsedEpisodes = serverData.mapNotNull { episodeElement ->
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

            if (parsedEpisodes.isEmpty()) null
            else EpisodeServer(serverName, parsedEpisodes)

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
    TvChannel(
        "VTV1", "Việt Nam", "Tin tức",
        "Kênh thời sự và chính luận của VTV",
        "https://vtv.vn/truyen-hinh-truc-tuyen/vtv1.htm"
    ),
    TvChannel(
        "VTVgo", "Việt Nam", "Tổng hợp",
        "Nền tảng truyền hình trực tuyến chính thức của VTV",
        "https://vtvgo.vn/"
    ),
    TvChannel(
        "VTV4", "Việt Nam", "Quốc tế",
        "Kênh truyền hình đối ngoại Việt Nam",
        "https://vtv4.vtv.vn/xem-vtv4-moi-luc-moi-noi.html"
    ),
    TvChannel(
        "Vietnam Today", "Việt Nam", "Tin tức",
        "Kênh đối ngoại quốc gia",
        "https://vietnamtoday.vtv.vn/"
    ),
    TvChannel(
        "Al Jazeera English", "Quốc tế", "Tin tức",
        "Tin tức quốc tế trực tiếp",
        "https://www.aljazeera.com/video/live"
    ),
    TvChannel(
        "TRT World", "Quốc tế", "Tin tức",
        "Tin tức thế giới",
        "https://www.trtworld.com/live"
    ),
    TvChannel(
        "France 24", "Quốc tế", "Tin tức",
        "Kênh tin tức quốc tế của Pháp",
        "https://www.france24.com/en/live"
    ),
    TvChannel(
        "DW News", "Quốc tế", "Tin tức",
        "Kênh tin tức quốc tế của Đức",
        "https://www.dw.com/en/live-tv/s-100825"
    ),
    TvChannel(
        "NHK World-Japan", "Quốc tế", "Tin tức",
        "Truyền hình quốc tế Nhật Bản",
        "https://www3.nhk.or.jp/nhkworld/en/live/"
    )
)

@Composable
fun LiveTvScreen() {
    val context = LocalContext.current

    var selectedRegion by remember {
        mutableStateOf("Tất cả")
    }

    var search by remember {
        mutableStateOf("")
    }

    val regions = listOf("Tất cả", "Việt Nam", "Quốc tế")

    val filtered = officialChannels.filter {
        (selectedRegion == "Tất cả" ||
            it.country == selectedRegion) &&
            (it.name.contains(search, true) ||
                it.category.contains(search, true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "LIVE TV",
            color = Red,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Truyền hình thế giới",
            color = White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Việt Nam • Quốc tế",
            color = Gray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("Tìm tên kênh...")
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            regions.forEach { region ->
                FilterChip(
                    selected = selectedRegion == region,
                    onClick = { selectedRegion = region },
                    label = { Text(region) }
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(filtered) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Panel,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            val intent = Intent(
                                context,
                                InAppWebActivity::class.java
                            ).apply {
                                putExtra("web_title", channel.name)
                                putExtra("web_url", channel.url)
                            }

                            context.startActivity(intent)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                Color(0xFF303030),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            channel.name.take(3).uppercase(),
                            color = Red,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            channel.name,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${channel.country} • ${channel.category}",
                            color = Red,
                            fontSize = 11.sp
                        )
                        Text(
                            channel.description,
                            color = Gray,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }

                    Text("▶", color = Red, fontSize = 20.sp)
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

    var tab by remember { mutableStateOf("Trang chủ") }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var selected by remember { mutableStateOf<Movie?>(null) }

    var keyword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    var detailText by remember { mutableStateOf("") }
    var episodeServers by remember {
        mutableStateOf<List<EpisodeServer>>(emptyList())
    }

    var selectedServer by remember { mutableIntStateOf(0) }

    suspend fun loadMovies(section: String) {
        loading = true
        error = ""

        try {
            val response = when (section) {
                "Phim lẻ" ->
                    MovieService.api.list("phim-le")

                "Phim bộ" ->
                    MovieService.api.list("phim-bo")

                "Hoạt hình" ->
                    MovieService.api.list("hoat-hinh")

                else ->
                    MovieService.api.home()
            }

            movies = parseMovies(response)

            if (movies.isEmpty()) {
                error = "Nguồn dữ liệu chưa trả về danh sách phim."
            }
        } catch (e: Exception) {
            error = "Không thể tải phim: ${e.message ?: "Lỗi kết nối"}"
        }

        loading = false
    }

    LaunchedEffect(tab) {
        if (tab != "Live TV" && tab != "Tìm kiếm") {
            loadMovies(tab)
        }
    }

    LaunchedEffect(selected?.slug) {
        val movie = selected ?: return@LaunchedEffect

        detailText = "Đang tải thông tin phim..."
        episodeServers = emptyList()
        selectedServer = 0

        try {
            val response = MovieService.api.detail(movie.slug)

            val movieInfo = try {
                response.getAsJsonObject("movie")
            } catch (_: Exception) {
                null
            }

            detailText = movieInfo?.let {
                jsonText(it, "content")
            }.orEmpty()
                .replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ")
                .trim()
                .ifBlank {
                    "Chưa có giới thiệu cho phim này."
                }

            episodeServers = parseEpisodeServers(response)

            if (episodeServers.isEmpty()) {
                detailText += "\n\nAPI chưa trả về danh sách tập hoặc máy chủ phát."
            }

        } catch (e: Exception) {
            detailText = "Không tải được chi tiết phim: ${e.message}"
            episodeServers = emptyList()
        }
    }

    Scaffold(
        containerColor = Black,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf(
                    "Trang chủ",
                    "Phim lẻ",
                    "Phim bộ",
                    "Hoạt hình",
                    "Live TV"
                ).forEach { item ->
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
                        label = {
                            Text(item, fontSize = 9.sp, maxLines = 1)
                        },
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
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "VIET",
                    color = Red,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "STREAM",
                    color = White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    " MAX",
                    color = Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (tab == "Live TV") {
                LiveTvScreen()
            } else {

                if (tab == "Trang chủ") {
                    Text(
                        "Giải trí không giới hạn",
                        color = White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }

                if (tab == "Tìm kiếm") {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        placeholder = {
                            Text("Nhập tên phim cần tìm...")
                        },
                        singleLine = true,
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    if (keyword.isNotBlank()) {
                                        scope.launch {
                                            loading = true
                                            error = ""

                                            try {
                                                movies = parseMovies(
                                                    MovieService.api.search(
                                                        keyword.trim()
                                                    )
                                                )

                                                if (movies.isEmpty()) {
                                                    error = "Không tìm thấy phim."
                                                }
                                            } catch (e: Exception) {
                                                error = "Tìm kiếm thất bại: ${e.message}"
                                            }

                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                Text("Tìm", color = Red)
                            }
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { tab = "Tìm kiếm" }
                        ) {
                            Text("⌕ Tìm phim", color = Red)
                        }

                        Text(
                            "Phim mới cập nhật",
                            color = Gray,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                when {
                    loading -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(35.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Red)
                        }
                    }

                    error.isNotBlank() -> {
                        Text(
                            error,
                            color = Red,
                            modifier = Modifier.padding(20.dp)
                        )
                    }

                    movies.isEmpty() -> {
                        Text(
                            "Chưa có dữ liệu phim.",
                            color = Gray,
                            modifier = Modifier.padding(20.dp)
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            gridItems(movies) { movie ->
                                MovieCard(movie) {
                                    selected = movie
                                }
                            }
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
                Text(
                    movie.name,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 540.dp)
                ) {
                    if (movie.poster.isNotBlank()) {
                        AsyncImage(
                            model = movie.poster,
                            contentDescription = movie.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "${movie.origin} • ${movie.year}",
                        color = Red
                    )

                    Text(
                        detailText,
                        color = White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Divider(color = Gray.copy(alpha = 0.3f))

                    Text(
                        "CHỌN MÁY CHỦ VÀ TẬP PHIM",
                        color = Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    if (episodeServers.isEmpty()) {
                        Text(
                            "Chưa có tập phim khả dụng.",
                            color = Gray,
                            fontSize = 12.sp
                        )
                    } else {
                        episodeServers.forEachIndexed { serverIndex, server ->
                            Text(
                                server.name,
                                color = White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )

                            server.episodes.forEach { episode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(
                                            Color(0xFF292929),
                                            RoundedCornerShape(7.dp)
                                        )
                                        .clickable {
                                            val videoUrl = episode.m3u8
                                            val embedUrl = episode.embed

                                            try {
                                                if (videoUrl.startsWith("https://") ||
                                                    videoUrl.startsWith("http://")
                                                ) {
                                                    val intent = Intent(
                                                        context,
                                                        InAppPlayerActivity::class.java
                                                    ).apply {
                                                        putExtra(
                                                            "video_title",
                                                            "${movie.name} - ${episode.name}"
                                                        )
                                                        putExtra(
                                                            "video_url",
                                                            videoUrl
                                                        )
                                                    }

                                                    context.startActivity(intent)
                                                    selected = null

                                                } else if (
                                                    embedUrl.startsWith("https://") ||
                                                    embedUrl.startsWith("http://")
                                                ) {
                                                    val intent = Intent(
                                                        context,
                                                        InAppWebActivity::class.java
                                                    ).apply {
                                                        putExtra(
                                                            "web_title",
                                                            "${movie.name} - ${episode.name}"
                                                        )
                                                        putExtra(
                                                            "web_url",
                                                            embedUrl
                                                        )
                                                    }

                                                    context.startActivity(intent)
                                                    selected = null

                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Tập này chưa có đường dẫn phát.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Không thể mở tập phim.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "▶",
                                        color = Red
                                    )

                                    Spacer(Modifier.width(9.dp))

                                    Text(
                                        episode.name.ifBlank { "Tập phim" },
                                        color = White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text(
                                        "Xem",
                                        color = Red,
                                        fontSize = 12.sp
                                    )
                                }
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
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = movie.poster.ifBlank { null },
            contentDescription = movie.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Panel, RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.height(6.dp))

        Text(
            movie.name,
            color = White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )

        Text(
            movie.year,
            color = Gray,
            fontSize = 11.sp
        )
    }
}
