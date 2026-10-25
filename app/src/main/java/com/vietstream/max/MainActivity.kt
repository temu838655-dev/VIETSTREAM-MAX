
package com.vietstream.max

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import com.google.gson.JsonElement
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
    val data = root.getAsJsonObject("data")
    val items = when {
        root.get("items")?.isJsonArray == true -> root.getAsJsonArray("items")
        data?.get("items")?.isJsonArray == true -> data.getAsJsonArray("items")
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
            if (movies.isEmpty()) error = "Nguồn dữ liệu chưa trả về danh sách phim."
        } catch (e: Exception) {
            error = "Không thể tải dữ liệu. Hãy kiểm tra kết nối mạng rồi thử lại."
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
            val data = response.getAsJsonObject("data")
            val item = data?.getAsJsonObject("item")
            val content = item?.let { jsonText(it, "content") }.orEmpty()
            detailText = content
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
                Text("VIET", color = Red, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("STREAM", color = White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(" MAX", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (tab == "Live TV") {
                Column(Modifier.padding(20.dp)) {
                    Text("TRUYỀN HÌNH TRỰC TIẾP", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Khu vực Live TV sẽ hiển thị các kênh có nguồn phát được cấp phép hoặc do bạn cung cấp.",
                        color = Gray,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Danh sách kênh sẽ được bổ sung ở bước tiếp theo.", color = Red)
                }
            } else {
                if (tab == "Trang chủ") {
                    Text(
                        "Giải trí không giới hạn",
                        color = White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp)
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
                        placeholder = { Text("Nhập tên phim cần tìm...") },
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = {
                                if (keyword.isNotBlank()) {
                                    loading = true
                                    error = ""
                                    kotlinx.coroutines.GlobalScope
                                        .launch(kotlinx.coroutines.Dispatchers.Main) {
                                            try {
                                                movies = parseMovies(
                                                    MovieService.api.search(keyword.trim())
                                                )
                                                if (movies.isEmpty()) error = "Không tìm thấy phim."
                                            } catch (_: Exception) {
                                                error = "Tìm kiếm thất bại. Hãy thử lại."
                                            }
                                            loading = false
                                        }
                                }
                            }) {
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
                        TextButton(onClick = { tab = "Tìm kiếm" }) {
                            Text("⌕ Tìm phim", color = Red)
                        }
                        Text("Phim mới cập nhật", color = Gray, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }

                when {
                    loading -> Box(
                        Modifier.fillMaxWidth().padding(35.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Red)
                    }

                    error.isNotBlank() -> Text(
                        error,
                        color = Red,
                        modifier = Modifier.padding(20.dp)
                    )

                    movies.isEmpty() -> Text(
                        "Chưa có dữ liệu phim.",
                        color = Gray,
                        modifier = Modifier.padding(20.dp)
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(movies) { movie ->
                            MovieCard(movie) { selected = movie }
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
                        "${movie.origin}  •  ${movie.year}",
                        color = Red
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(detailText, color = White, fontSize = 13.sp)
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
fun MovieCard(movie: Movie, onClick: () -> Unit) {
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
