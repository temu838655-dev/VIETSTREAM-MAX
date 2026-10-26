
package com.vietstream.max

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
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
private val FocusColor = Color(0xFFFF4545)

data class Movie(
    val name: String,
    val slug: String,
    val poster: String,
    val year: String,
    val origin: String,
    val description: String = "",
    val countries: List<String> = emptyList(),
    val genres: List<String> = emptyList()
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

private val gson = Gson()

private fun jsonText(obj: JsonObject, key: String): String {
    return try {
        obj.get(key)
            ?.takeIf { !it.isJsonNull }
            ?.asString ?: ""
    } catch (_: Exception) {
        ""
    }
}

private fun jsonNames(obj: JsonObject, key: String): List<String> {
    return try {
        val array = obj.get(key)
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return emptyList()

        array.mapNotNull { element ->
            try {
                val value = if (element.isJsonObject) {
                    val item = element.asJsonObject
                    jsonText(item, "name")
                        .ifBlank { jsonText(item, "slug") }
                } else if (!element.isJsonNull) {
                    element.asString
                } else {
                    ""
                }

                value.trim().takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }.distinct()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun resolvePoster(raw: String, imageRoot: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""

    return when {
        value.startsWith("https://", true) -> value
        value.startsWith("http://", true) ->
            value.replaceFirst("http://", "https://", ignoreCase = true)
        value.startsWith("//") -> "https:$value"
        else -> {
            val root = imageRoot.trim().trimEnd('/')
            if (root.isBlank()) return ""
            if (value.startsWith("/")) "$root$value" else "$root/$value"
        }
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

    val imageRoot = jsonText(data ?: JsonObject(), "APP_DOMAIN_CDN_IMAGE")
        .ifBlank { jsonText(root, "APP_DOMAIN_CDN_IMAGE") }
        .ifBlank { jsonText(data ?: JsonObject(), "pathImage") }
        .ifBlank { jsonText(root, "pathImage") }
        .ifBlank { "https://phimimg.com" }
        .trimEnd('/')

    return items.mapNotNull { element ->
        try {
            val item = element.asJsonObject
            val name = jsonText(item, "name")
            val slug = jsonText(item, "slug")

            val rawPoster = jsonText(item, "poster_url")
                .ifBlank { jsonText(item, "thumb_url") }

            if (name.isBlank() || slug.isBlank()) {
                null
            } else {
                Movie(
                    name = name,
                    slug = slug,
                    poster = resolvePoster(rawPoster, imageRoot),
                    year = jsonText(item, "year"),
                    origin = jsonText(item, "origin_name"),
                    description = jsonText(item, "content"),
                    countries = jsonNames(item, "country"),
                    genres = jsonNames(item, "category")
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

// THƯ VIỆN CỤC BỘ

private const val PREFS_NAME = "vietstream_library"
private const val FAVORITES_KEY = "favorites_movies"
private const val HISTORY_KEY = "history_movies"

private fun readMovieList(
    context: Context,
    key: String
): List<Movie> {
    return try {
        val json = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(key, null) ?: return emptyList()

        val type = object : TypeToken<List<Movie>>() {}.type

        gson.fromJson<List<Movie>>(json, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveMovieList(
    context: Context,
    key: String,
    movies: List<Movie>
) {
    context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    ).edit()
        .putString(key, gson.toJson(movies))
        .apply()
}

// LIVE TV

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
        "Nền tảng truyền hình trực tuyến của VTV",
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
        "Tin tức quốc tế",
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
fun LiveTvScreen(
    modifier: Modifier = Modifier,
    isTv: Boolean = false
) {
    val context = LocalContext.current

    var region by remember { mutableStateOf("Tất cả") }
    var search by remember { mutableStateOf("") }

    val regions = listOf("Tất cả", "Việt Nam", "Quốc tế")

    val filtered = officialChannels.filter {
        (region == "Tất cả" || it.country == region) &&
            (
                it.name.contains(search, true) ||
                    it.category.contains(search, true)
                )
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Black)
            .padding(horizontal = if (isTv) 28.dp else 16.dp)
    ) {
        Text(
            "TRUYỀN HÌNH TRỰC TUYẾN",
            color = Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Live TV",
            color = White,
            fontSize = if (isTv) 34.sp else 27.sp,
            fontWeight = FontWeight.Black
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Tìm tên kênh...") }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            regions.forEach {
                FilterChip(
                    selected = region == it,
                    onClick = { region = it },
                    label = { Text(it) }
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            filtered.forEach { channel ->
                TvChannelCard(channel, isTv) {
                    context.startActivity(
                        Intent(
                            context,
                            InAppWebActivity::class.java
                        ).apply {
                            putExtra("web_title", channel.name)
                            putExtra("web_url", channel.url)
                        }
                    )
                }
            }

            if (filtered.isEmpty()) {
                Text(
                    "Không tìm thấy kênh phù hợp.",
                    color = Gray,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TvChannelCard(
    channel: TvChannel,
    isTv: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) FocusColor else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(if (isTv) 20.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(if (isTv) 62.dp else 48.dp)
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
                fontWeight = FontWeight.Bold,
                fontSize = if (isTv) 18.sp else 14.sp
            )

            Text(
                "${channel.country} • ${channel.category}",
                color = Red,
                fontSize = 11.sp
            )

            Text(
                channel.description,
                color = Gray,
                fontSize = 11.sp
            )
        }

        Text("▶", color = Red)
    }
}

// POSTER

@Composable
fun MoviePoster(
    url: String,
    name: String,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(url) { mutableStateOf(false) }

    Box(
        modifier.background(Panel),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank() && !imageFailed) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true }
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("▶", color = Red, fontSize = 30.sp)

                Text(
                    name,
                    color = Gray,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// BỘ LỌC PHIM

@Composable
private fun MovieFilterRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    if (options.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            title,
            color = Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                start = 12.dp,
                bottom = 4.dp
            )
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            option,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun MovieFilters(
    movies: List<Movie>,
    country: String,
    year: String,
    genre: String,
    onCountry: (String) -> Unit,
    onYear: (String) -> Unit,
    onGenre: (String) -> Unit,
    onReset: () -> Unit
) {
    val countries = listOf("Tất cả") +
        movies.flatMap { it.countries.orEmpty() }
            .distinct()
            .sorted()

    val years = listOf("Tất cả") +
        movies.map { it.year }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()

    val genres = listOf("Tất cả") +
        movies.flatMap { it.genres.orEmpty() }
            .distinct()
            .sorted()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LỌC PHIM",
                color = Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onReset) {
                Text(
                    "Xóa bộ lọc",
                    color = White,
                    fontSize = 11.sp
                )
            }
        }

        MovieFilterRow(
            title = "Quốc gia",
            options = countries,
            selected = country,
            onSelect = onCountry
        )

        MovieFilterRow(
            title = "Năm sản xuất",
            options = years,
            selected = year,
            onSelect = onYear
        )

        MovieFilterRow(
            title = "Thể loại",
            options = genres,
            selected = genre,
            onSelect = onGenre
        )
    }
}

// MAIN ACTIVITY

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isTv = packageManager.hasSystemFeature(
            PackageManager.FEATURE_LEANBACK
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Red,
                    background = Black,
                    surface = Panel
                )
            ) {
                VietStreamApp(isTv = isTv)
            }
        }
    }
}

@Composable
fun VietStreamApp(isTv: Boolean = false) {
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

    var favorites by remember {
        mutableStateOf(readMovieList(context, FAVORITES_KEY))
    }

    var history by remember {
        mutableStateOf(readMovieList(context, HISTORY_KEY))
    }

    // Trạng thái bộ lọc
    var filterCountry by remember { mutableStateOf("Tất cả") }
    var filterYear by remember { mutableStateOf("Tất cả") }
    var filterGenre by remember { mutableStateOf("Tất cả") }

    fun resetFilters() {
        filterCountry = "Tất cả"
        filterYear = "Tất cả"
        filterGenre = "Tất cả"
    }

    fun toggleFavorite(movie: Movie) {
        val updated = if (favorites.any { it.slug == movie.slug }) {
            favorites.filterNot { it.slug == movie.slug }
        } else {
            listOf(movie) + favorites
        }

        favorites = updated
        saveMovieList(context, FAVORITES_KEY, updated)
    }

    fun addToHistory(movie: Movie) {
        val updated = (
            listOf(movie) +
                history.filter { it.slug != movie.slug }
            ).take(100)

        history = updated
        saveMovieList(context, HISTORY_KEY, updated)
    }

    suspend fun loadMovies(section: String) {
        loading = true
        error = ""

        try {
            movies = when (section) {
                "Phim lẻ" ->
                    parseMovies(MovieService.api.list("phim-le"))

                "Phim bộ" ->
                    parseMovies(MovieService.api.list("phim-bo"))

                "Hoạt hình" ->
                    parseMovies(MovieService.api.list("hoat-hinh"))

                "Yêu thích" -> favorites

                "Lịch sử" -> history

                else -> parseMovies(MovieService.api.home())
            }

            if (movies.isEmpty()) {
                error = when (section) {
                    "Yêu thích" ->
                        "Bạn chưa thêm phim yêu thích."

                    "Lịch sử" ->
                        "Bạn chưa xem phim nào."

                    else ->
                        "Chưa có dữ liệu cho danh mục này."
                }
            }
        } catch (e: Exception) {
            error = "Không thể tải phim: ${
                e.message ?: "Lỗi kết nối"
            }"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(tab) {
        resetFilters()

        if (
            tab != "Live TV" &&
            tab != "Tìm kiếm" &&
            tab != "Cài đặt"
        ) {
            loadMovies(tab)
        }
    }

    LaunchedEffect(favorites, history, tab) {
        when (tab) {
            "Yêu thích" -> {
                movies = favorites
                error = if (favorites.isEmpty()) {
                    "Bạn chưa thêm phim yêu thích."
                } else {
                    ""
                }
            }

            "Lịch sử" -> {
                movies = history
                error = if (history.isEmpty()) {
                    "Bạn chưa xem phim nào."
                } else {
                    ""
                }
            }
        }
    }

    LaunchedEffect(selected?.slug) {
        val movie = selected ?: return@LaunchedEffect

        detailText = "Đang tải thông tin phim..."
        episodeServers = emptyList()

        try {
            val response = MovieService.api.detail(movie.slug)

            val info = try {
                response.getAsJsonObject("movie")
            } catch (_: Exception) {
                null
            }

            detailText = info?.let {
                jsonText(it, "content")
            }.orEmpty()
                .replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ")
                .trim()
                .ifBlank {
                    movie.description.ifBlank {
                        "Chưa có giới thiệu."
                    }
                }

            episodeServers = parseEpisodeServers(response)

            if (episodeServers.isEmpty()) {
                detailText +=
                    "\n\nChưa có máy chủ hoặc tập phim khả dụng."
            }
        } catch (e: Exception) {
            detailText = "Không tải được chi tiết phim: ${
                e.message ?: "Lỗi kết nối"
            }"
        }
    }

    val tabs = listOf(
        "Trang chủ",
        "Phim lẻ",
        "Phim bộ",
        "Hoạt hình",
        "Live TV",
        "Cài đặt"
    )

    Scaffold(
        containerColor = Black,
        bottomBar = {
            if (!isTv) {
                NavigationBar(containerColor = Panel) {
                    tabs.forEach { item ->
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
                                        "Live TV" -> "TV"
                                        "Cài đặt" -> "⚙"
                                        else -> "•"
                                    },
                                    color = if (tab == item) Red else Gray
                                )
                            },
                            label = {
                                Text(
                                    item,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
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
        }
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .background(Black)
                .padding(padding)
        ) {
            if (isTv) {
                Column(
                    Modifier
                        .width(190.dp)
                        .fillMaxHeight()
                        .background(Panel)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "VIETSTREAM",
                        color = Red,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(12.dp)
                    )

                    tabs.forEach { item ->
                        TvNavigationItem(
                            item,
                            tab == item
                        ) { tab = item }
                    }

                    Spacer(Modifier.height(12.dp))

                    TvNavigationItem(
                        "Tìm kiếm",
                        tab == "Tìm kiếm"
                    ) { tab = "Tìm kiếm" }

                    TvNavigationItem(
                        "Yêu thích",
                        tab == "Yêu thích"
                    ) { tab = "Yêu thích" }

                    TvNavigationItem(
                        "Lịch sử",
                        tab == "Lịch sử"
                    ) { tab = "Lịch sử" }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .background(Black)
            ) {
                // HEADER
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isTv) 28.dp else 18.dp,
                            vertical = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "N",
                        color = Red,
                        fontSize = if (isTv) 40.sp else 32.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.width(8.dp))

                    Column {
                        Row {
                            Text(
                                "VIET",
                                color = Red,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black
                            )

                            Text(
                                "STREAM",
                                color = White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Text(
                            "MAX",
                            color = Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (!isTv) {
                        TextButton(onClick = {
                            tab = "Tìm kiếm"
                        }) {
                            Text("⌕ Tìm kiếm", color = White)
                        }
                    }
                }

                when (tab) {
                    "Live TV" -> {
                        LiveTvScreen(
                            Modifier.weight(1f),
                            isTv
                        )
                    }

                    "Cài đặt" -> {
                        SettingsScreen(
                            isTv = isTv,
                            favoriteCount = favorites.size,
                            historyCount = history.size,
                            onNavigate = { destination ->
                                tab = destination
                            },
                            onClearFavorites = {
                                favorites = emptyList()
                                saveMovieList(
                                    context,
                                    FAVORITES_KEY,
                                    emptyList()
                                )
                            },
                            onClearHistory = {
                                history = emptyList()
                                saveMovieList(
                                    context,
                                    HISTORY_KEY,
                                    emptyList()
                                )
                            }
                        )
                    }

                    else -> {
                        // TÌM KIẾM
                        if (tab == "Tìm kiếm") {
                            OutlinedTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                singleLine = true,
                                placeholder = {
                                    Text("Nhập tên phim...")
                                },
                                trailingIcon = {
                                    TextButton(onClick = {
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
                                                        error =
                                                            "Không tìm thấy phim."
                                                    }
                                                } catch (e: Exception) {
                                                    error =
                                                        "Tìm kiếm thất bại: ${
                                                            e.message
                                                        }"
                                                } finally {
                                                    loading = false
                                                }
                                            }
                                        }
                                    }) {
                                        Text("Tìm", color = Red)
                                    }
                                }
                            )
                        }

                        if (
                            tab == "Yêu thích" ||
                            tab == "Lịch sử"
                        ) {
                            Text(
                                tab,
                                color = White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        // PHIM NỔI BẬT
                        if (tab == "Trang chủ" && movies.isNotEmpty()) {
                            val hero = movies.first()

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        if (isTv) 300.dp else 225.dp
                                    )
                                    .padding(horizontal = 12.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selected = hero }
                            ) {
                                MoviePoster(
                                    hero.poster,
                                    hero.name,
                                    Modifier.fillMaxSize()
                                )

                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(
                                                        alpha = 0.95f
                                                    )
                                                )
                                            )
                                        )
                                )

                                Column(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        "VIETSTREAM MAX",
                                        color = Red,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )

                                    Text(
                                        hero.name,
                                        color = White,
                                        fontSize = if (isTv) 30.sp else 22.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 2
                                    )

                                    Text(
                                        "${hero.year} • ${hero.origin}",
                                        color = Gray,
                                        fontSize = 11.sp
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Button(
                                        onClick = { selected = hero },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Red
                                        )
                                    ) {
                                        Text("▶  Xem ngay")
                                    }
                                }
                            }
                        }

                        // TIÊU ĐỀ DANH SÁCH
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 10.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (tab) {
                                    "Trang chủ" -> "Phim mới cập nhật"
                                    "Tìm kiếm" -> "Kết quả tìm kiếm"
                                    else -> tab
                                },
                                color = White,
                                fontSize = if (isTv) 25.sp else 19.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.weight(1f))

                            if (loading) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    color = Red,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        // ÁP DỤNG BỘ LỌC
                        val visibleMovies = movies.filter { movie ->
                            val matchesCountry =
                                filterCountry == "Tất cả" ||
                                    movie.countries.orEmpty().any {
                                        it.equals(
                                            filterCountry,
                                            ignoreCase = true
                                        )
                                    }

                            val matchesYear =
                                filterYear == "Tất cả" ||
                                    movie.year == filterYear

                            val matchesGenre =
                                filterGenre == "Tất cả" ||
                                    movie.genres.orEmpty().any {
                                        it.equals(
                                            filterGenre,
                                            ignoreCase = true
                                        )
                                    }

                            matchesCountry &&
                                matchesYear &&
                                matchesGenre
                        }

                        // HIỂN THỊ BỘ LỌC
                        if (
                            movies.isNotEmpty() &&
                            tab != "Live TV" &&
                            tab != "Cài đặt"
                        ) {
                            MovieFilters(
                                movies = movies,
                                country = filterCountry,
                                year = filterYear,
                                genre = filterGenre,
                                onCountry = { filterCountry = it },
                                onYear = { filterYear = it },
                                onGenre = { filterGenre = it },
                                onReset = { resetFilters() }
                            )
                        }

                        // DANH SÁCH PHIM
                        when {
                            loading -> {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Red)
                                }
                            }

                            error.isNotBlank() -> {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(error, color = Gray)
                                }
                            }

                            movies.isEmpty() -> {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Chưa có dữ liệu phim.",
                                        color = Gray
                                    )
                                }
                            }

                            visibleMovies.isEmpty() -> {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Không có phim phù hợp với bộ lọc.",
                                        color = Gray
                                    )
                                }
                            }

                            else -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(
                                        minSize = if (isTv) {
                                            190.dp
                                        } else {
                                            145.dp
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(
                                        if (isTv) 22.dp else 12.dp
                                    ),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            if (isTv) 18.dp else 12.dp
                                        ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            if (isTv) 22.dp else 16.dp
                                        )
                                ) {
                                    gridItems(
                                        items = visibleMovies,
                                        key = { it.slug }
                                    ) { movie ->
                                        MovieCard(
                                            movie = movie,
                                            favorite = favorites.any {
                                                it.slug == movie.slug
                                            },
                                            isTv = isTv
                                        ) {
                                            selected = movie
                                        }
                                    }
                                }
                            }
                        }

                        if (tab == "Tìm kiếm") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { tab = "Yêu thích" }
                                ) {
                                    Text("♥ Yêu thích", color = Red)
                                }

                                TextButton(
                                    onClick = { tab = "Lịch sử" }
                                ) {
                                    Text("◷ Lịch sử", color = White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // CHI TIẾT PHIM VÀ CHỌN TẬP
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
                    Modifier
                        .heightIn(
                            max = if (isTv) 650.dp else 520.dp
                        )
                        .verticalScroll(rememberScrollState())
                ) {
                    MoviePoster(
                        movie.poster,
                        movie.name,
                        Modifier
                            .fillMaxWidth()
                            .height(if (isTv) 240.dp else 180.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Text(
                        "${movie.origin} • ${movie.year}",
                        color = Red,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Text(
                        detailText,
                        color = White,
                        fontSize = 13.sp
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { toggleFavorite(movie) }
                        ) {
                            Text(
                                if (
                                    favorites.any {
                                        it.slug == movie.slug
                                    }
                                ) {
                                    "♥ Đã yêu thích"
                                } else {
                                    "♡ Yêu thích"
                                },
                                color = Red
                            )
                        }

                        TextButton(onClick = {
                            addToHistory(movie)
                            selected = null
                            tab = "Lịch sử"
                        }) {
                            Text("◷ Lịch sử xem", color = White)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    HorizontalDivider(
                        color = Gray.copy(alpha = 0.3f)
                    )

                    Text(
                        "CHỌN MÁY CHỦ VÀ TẬP",
                        color = Red,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    if (episodeServers.isEmpty()) {
                        Text(
                            "Chưa có tập phim khả dụng.",
                            color = Gray,
                            fontSize = 12.sp
                        )
                    }

                    episodeServers.forEach { server ->
                        Text(
                            server.name,
                            color = White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        server.episodes.forEach { episode ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF292929))
                                    .clickable {
                                        val videoUrl =
                                            episode.m3u8.trim()

                                        val embedUrl =
                                            episode.embed.trim()

                                        val validM3u8 =
                                            videoUrl.startsWith(
                                                "https://",
                                                true
                                            ) ||
                                                videoUrl.startsWith(
                                                    "http://",
                                                    true
                                                )

                                        val validEmbed =
                                            embedUrl.startsWith(
                                                "https://",
                                                true
                                            ) ||
                                                embedUrl.startsWith(
                                                    "http://",
                                                    true
                                                )

                                        when {
                                            validM3u8 -> {
                                                val playlist =
                                                    server.episodes.filter {
                                                        it.m3u8.startsWith(
                                                            "https://",
                                                            true
                                                        ) ||
                                                            it.m3u8.startsWith(
                                                                "http://",
                                                                true
                                                            )
                                                    }

                                                val index =
                                                    playlist.indexOfFirst {
                                                        it.slug == episode.slug
                                                    }.coerceAtLeast(0)

                                                addToHistory(movie)

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
                                                            ArrayList(
                                                                playlist.map {
                                                                    "${movie.name} - ${it.name}"
                                                                }
                                                            )
                                                        )

                                                        putStringArrayListExtra(
                                                            "episode_urls",
                                                            ArrayList(
                                                                playlist.map {
                                                                    it.m3u8.trim()
                                                                }
                                                            )
                                                        )

                                                        putExtra(
                                                            "episode_index",
                                                            index
                                                        )
                                                    }
                                                )

                                                selected = null
                                            }

                                            validEmbed -> {
                                                addToHistory(movie)

                                                context.startActivity(
                                                    Intent(
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
                                                )

                                                selected = null
                                            }

                                            else -> {
                                                Toast.makeText(
                                                    context,
                                                    "Tập này chưa có đường dẫn phát.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("▶", color = Red)

                                Spacer(Modifier.width(10.dp))

                                Text(
                                    episode.name.ifBlank {
                                        "Tập phim"
                                    },
                                    color = White,
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
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text("Đóng", color = Red)
                }
            }
        )
    }
}

// CÀI ĐẶT

@Composable
private fun SettingsScreen(
    isTv: Boolean,
    favoriteCount: Int,
    historyCount: Int,
    onNavigate: (String) -> Unit,
    onClearFavorites: () -> Unit,
    onClearHistory: () -> Unit
) {
    var showClearFavorites by remember {
        mutableStateOf(false)
    }

    var showClearHistory by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTv) 28.dp else 18.dp)
    ) {
        Text(
            "CÀI ĐẶT",
            color = Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Thiết lập ứng dụng",
            color = White,
            fontSize = if (isTv) 30.sp else 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        Text(
            "VIETSTREAM MAX",
            color = Gray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "THƯ VIỆN CỦA BẠN",
            color = Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        SettingsItem(
            title = "Phim yêu thích",
            subtitle = "$favoriteCount phim đã lưu",
            onClick = { onNavigate("Yêu thích") }
        )

        SettingsItem(
            title = "Lịch sử xem",
            subtitle = "$historyCount phim trong lịch sử",
            onClick = { onNavigate("Lịch sử") }
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "KHÁM PHÁ",
            color = Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        SettingsItem(
            title = "Tìm kiếm phim",
            subtitle = "Tìm phim theo tên",
            onClick = { onNavigate("Tìm kiếm") }
        )

        SettingsItem(
            title = "Phim lẻ",
            subtitle = "Khám phá phim lẻ",
            onClick = { onNavigate("Phim lẻ") }
        )

        SettingsItem(
            title = "Phim bộ",
            subtitle = "Khám phá phim bộ",
            onClick = { onNavigate("Phim bộ") }
        )

        SettingsItem(
            title = "Phim hoạt hình",
            subtitle = "Khám phá phim hoạt hình",
            onClick = { onNavigate("Hoạt hình") }
        )

        SettingsItem(
            title = "Live TV",
            subtitle = "Truyền hình trực tuyến",
            onClick = { onNavigate("Live TV") }
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "QUẢN LÝ DỮ LIỆU",
            color = Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        SettingsItem(
            title = "Xóa danh sách yêu thích",
            subtitle = "Xóa toàn bộ phim đã lưu",
            onClick = { showClearFavorites = true }
        )

        SettingsItem(
            title = "Xóa lịch sử xem",
            subtitle = "Xóa toàn bộ lịch sử trên thiết bị",
            onClick = { showClearHistory = true }
        )

        Spacer(Modifier.height(24.dp))

        HorizontalDivider(
            color = Gray.copy(alpha = 0.25f)
        )

        Text(
            "VIETSTREAM MAX • Phiên bản 1.0.0",
            color = Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 18.dp)
        )
    }

    if (showClearFavorites) {
        AlertDialog(
            onDismissRequest = {
                showClearFavorites = false
            },
            containerColor = Panel,
            title = {
                Text("Xóa phim yêu thích?", color = White)
            },
            text = {
                Text(
                    "Toàn bộ danh sách phim yêu thích sẽ bị xóa.",
                    color = Gray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearFavorites()
                    showClearFavorites = false
                }) {
                    Text("Xóa", color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClearFavorites = false
                }) {
                    Text("Hủy", color = White)
                }
            }
        )
    }

    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = {
                showClearHistory = false
            },
            containerColor = Panel,
            title = {
                Text("Xóa lịch sử xem?", color = White)
            },
            text = {
                Text(
                    "Toàn bộ lịch sử xem trên thiết bị sẽ bị xóa.",
                    color = Gray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearHistory = false
                }) {
                    Text("Xóa", color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClearHistory = false
                }) {
                    Text("Hủy", color = White)
                }
            }
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color(0xFF292020) else Panel
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) FocusColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                subtitle,
                color = Gray,
                fontSize = 12.sp
            )
        }

        Text("›", color = Red, fontSize = 24.sp)
    }
}

// ĐIỀU HƯỚNG TV

@Composable
private fun TvNavigationItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) Color(0xFF382020)
                else Color.Transparent
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) FocusColor else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = 14.dp,
                vertical = 15.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = if (selected || focused) White else Gray,
            fontSize = 15.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            }
        )
    }
}

// THẺ PHIM

@Composable
fun MovieCard(
    movie: Movie,
    favorite: Boolean = false,
    isTv: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) FocusColor else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Box {
            MoviePoster(
                movie.poster,
                movie.name,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(10.dp))
            )

            if (movie.year.isNotBlank()) {
                Text(
                    movie.year,
                    color = White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(7.dp)
                        .background(
                            Color.Black.copy(alpha = 0.75f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(
                            horizontal = 6.dp,
                            vertical = 3.dp
                        )
                )
            }

            if (favorite) {
                Text(
                    "♥",
                    color = Red,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            movie.name,
            color = White,
            fontSize = if (isTv) 16.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            movie.origin,
            color = Gray,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
