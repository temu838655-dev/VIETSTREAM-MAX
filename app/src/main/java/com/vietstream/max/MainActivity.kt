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
import androidx.compose.foundation.lazy.items as columnItems
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

private const val TEST_VIDEO_URL =
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

data class Movie(
    val name: String,
    val slug: String,
    val poster: String,
    val year: String,
    val origin: String,
    val description: String = ""
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

    @GET("v1/api/phim/{slug}")
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
        .ifBlank { "https://phimapi.com/uploads/movies/" }

    return items.mapNotNull { element ->
        try {
            val item = element.asJsonObject
            val name = jsonText(item, "name")
            val slug = jsonText(item, "slug")
            val posterRaw = jsonText(item, "poster_url")
                .ifBlank { jsonText(item, "thumb_url") }

            val poster = when {
                posterRaw.startsWith("http") -> posterRaw
                posterRaw.isNotBlank() -> imageRoot + posterRaw
                else -> ""
            }

            if (name.isBlank() || slug.isBlank()) null
            else Movie(
                name = name,
                slug = slug,
                poster = poster,
                year = jsonText(item, "year"),
                origin = jsonText(item, "origin_name"),
                description = jsonText(item, "content")
            )
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
        "Kênh đối ngoại quốc gia bằng tiếng Anh",
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

    val filteredChannels = officialChannels.filter { channel ->
        val regionMatches =
            selectedRegion == "Tất cả" ||
                channel.country == selectedRegion

        val searchMatches =
            channel.name.contains(search, ignoreCase = true) ||
                channel.category.contains(search, ignoreCase = true)

        regionMatches && searchMatches
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
            "Việt Nam • Quốc tế • Nguồn xem chính thức",
            color = Gray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("Tìm tên kênh truyền hình...")
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedBorderColor = Red,
                unfocusedBorderColor = Gray,
                cursorColor = Red
            )
        )

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            regions.forEach { region ->
                FilterChip(
                    selected = selectedRegion == region,
                    onClick = { selectedRegion = region },
                    label = { Text(region) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Red,
                        selectedLabelColor = Color.White,
                        labelColor = White
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "DANH SÁCH KÊNH",
                color = White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Text(
                "${filteredChannels.size} kênh",
                color = Gray,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        if (filteredChannels.isEmpty()) {
            Text(
                "Không tìm thấy kênh phù hợp.",
                color = Gray,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                columnItems(filteredChannels) { channel ->
                    TvChannelCard(channel = channel) {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(channel.url)
                                )
                            )
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Không thể mở nguồn xem kênh.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvChannelCard(
    channel: TvChannel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(55.dp)
                .background(
                    Color(0xFF303030),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                channel.name.take(3).uppercase(),
                color = Red,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                color = White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
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

    suspend fun loadMovies(section: String) {
        loading = true
        error = ""

        try {
            val response = when (section) {
                "Phim lẻ" -> MovieService.api.list("phim-le")
                "Phim bộ" -> MovieService.api.list("phim-bo")
                "Hoạt hình" -> MovieService.api.list("hoat-hinh")
                else -> MovieService.api.home()
            }

            movies = parseMovies(response)

            if (movies.isEmpty()) {
                error = "Nguồn dữ liệu chưa trả về danh sách phim."
            }
        } catch (_: Exception) {
            error = "Không thể tải dữ liệu. Hãy kiểm tra mạng và thử lại."
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
        detailText = "Đang tải thông tin..."

        try {
            val response = MovieService.api.detail(movie.slug)
            val data = try {
                response.getAsJsonObject("data")
            } catch (_: Exception) {
                null
            }

            val item = try {
                data?.getAsJsonObject("item")
            } catch (_: Exception) {
                null
            }

            detailText = item?.let {
                jsonText(it, "content")
            }.orEmpty()
                .replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ")
                .trim()
                .ifBlank { "Chưa có giới thiệu cho phim này." }
        } catch (_: Exception) {
            detailText = "Chưa tải được phần giới thiệu phim."
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
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 5.dp
                        )
                    )
                    Text(
                        "Khám phá phim mới mỗi ngày",
                        color = Gray,
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
                                            } catch (_: Exception) {
                                                error = "Tìm kiếm thất bại. Hãy thử lại."
                                            }

                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                Text("Tìm", color = Red)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Red
                        )
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
                Column {
                    if (movie.poster.isNotBlank()) {
                        AsyncImage(
                            model = movie.poster,
                            contentDescription = movie.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "${movie.origin} • ${movie.year}",
                        color = Red
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        detailText,
                        color = White,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = { selected = null }
                    ) {
                        Text("Đóng", color = Gray)
                    }

                    TextButton(
                        onClick = {
                            val intent = Intent(
                                context,
                                InAppPlayerActivity::class.java
                            ).apply {
                                putExtra("video_title", movie.name)
                                putExtra("video_url", TEST_VIDEO_URL)
                            }

                            try {
                                context.startActivity(intent)
                                selected = null
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "Không thể mở trình phát.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text(
                            "▶ PHÁT NGAY",
                            color = Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
