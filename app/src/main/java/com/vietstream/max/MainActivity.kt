package com.vietstream.max

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.Crossfade
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

private val Black = Color(0xFF080808)
private val Panel = Color(0xFF191919)
private val Red = Color(0xFF2878F0)
private val White = Color(0xFFF5F5F5)
private val Gray = Color(0xFFAAAAAA)
private val BoBlue = Color(0xFF2878F0)
private val BoOrange = Color(0xFFFF8A00)
private val FocusColor = Color(0xFFFF8A00)

// Kept lightweight (no real-time backdrop blur) for Android phones and TV boxes.
@Composable
private fun BoPhimWordmark(size: Int = 22) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Bo", color = BoBlue, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp)
        Text("Phim", color = BoOrange, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp)
    }
}

// Liquid Glass surfaces: translucent layered gradients and fine highlights.
private fun Modifier.liquidGlass(
    shape: RoundedCornerShape = RoundedCornerShape(20.dp)
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF64748B).copy(alpha = 0.22f),
                Color(0xFF253044).copy(alpha = 0.16f),
                Color(0xFF101522).copy(alpha = 0.34f)
            )
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.08f),
                Color(0xFF9CCBFF).copy(alpha = 0.20f)
            )
        ),
        shape
    )

/** Try VLC for a direct media URL. Returns false if VLC cannot be launched. */
private fun openInVlc(context: Context, url: String, title: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("org.videolan.vlc")
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }
}
private val gson = Gson()

data class Movie(
    val name: String,
    val slug: String,
    val poster: String,
    val year: String,
    val origin: String,
    val description: String = "",
    val countries: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    // Dữ liệu tiếp tục xem; mặc định giúp đọc được dữ liệu thư viện cũ.
    val lastEpisodeName: String = "",
    val lastEpisodeUrl: String = "",
    val lastEpisodeIsM3u8: Boolean = false,
    val lastEpisodeTitles: List<String> = emptyList(),
    val lastEpisodeUrls: List<String> = emptyList(),
    val lastEpisodeIndex: Int = 0,
    // Nhãn chỉ được gán khi metadata API khai báo định dạng âm thanh rõ ràng.
    val audioLabels: List<String> = emptyList()
)

data class UserProfile(
    val id: String,
    val name: String,
    val avatar: String,
    val isKids: Boolean = false,
    val pin: String = "",
    val pinEnabled: Boolean = false
)

data class EpisodeSource(
    val name: String,
    val slug: String,
    val m3u8: String,
    val embed: String
)

data class EpisodeServer(val name: String, val episodes: List<EpisodeSource>)

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

private fun jsonText(obj: JsonObject, key: String): String =
    try {
        obj.get(key)?.takeIf { !it.isJsonNull }?.asString ?: ""
    } catch (_: Exception) {
        ""
    }

private fun jsonNames(obj: JsonObject, key: String): List<String> {
    return try {
        val array = obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return emptyList()
        array.mapNotNull { element ->
            try {
                val value = if (element.isJsonObject) {
                    jsonText(element.asJsonObject, "name")
                        .ifBlank { jsonText(element.asJsonObject, "slug") }
                } else if (!element.isJsonNull) element.asString else ""
                value.trim().takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        }.distinct()
    } catch (_: Exception) { emptyList() }
}

private fun scanAudioLabels(element: JsonElement?): List<String> {
    if (element == null || element.isJsonNull) return emptyList()
    val text = buildString {
        fun collect(node: JsonElement?) {
            when {
                node == null || node.isJsonNull -> Unit
                node.isJsonPrimitive -> append(' ').append(node.asString.lowercase())
                node.isJsonArray -> node.asJsonArray.forEach(::collect)
                node.isJsonObject -> node.asJsonObject.entrySet().forEach { (key, value) ->
                    append(' ').append(key.lowercase())
                    collect(value)
                }
            }
        }
        collect(element)
    }
    val labels = mutableListOf<String>()
    if (listOf("dolby atmos", "e-ac-3-joc", "eac3 joc", "ec-3-joc", "atmos").any(text::contains)) {
        labels += "DOLBY ATMOS"
    }
    if (listOf("spatial 360", "spatial audio", "360 spatial", "immersive audio").any(text::contains)) {
        labels += "SPATIAL 360°"
    }
    return labels.distinct()
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
            if (root.isBlank()) "" else if (value.startsWith("/")) "$root$value" else "$root/$value"
        }
    }
}

fun parseMovies(root: JsonObject): List<Movie> {
    val data = try { root.getAsJsonObject("data") } catch (_: Exception) { null }
    val items = when {
        root.get("items")?.isJsonArray == true -> root.getAsJsonArray("items")
        data?.get("items")?.isJsonArray == true -> data.getAsJsonArray("items")
        else -> null
    } ?: return emptyList()

    val imageRoot = jsonText(data ?: JsonObject(), "APP_DOMAIN_CDN_IMAGE")
        .ifBlank { jsonText(root, "APP_DOMAIN_CDN_IMAGE") }
        .ifBlank { jsonText(data ?: JsonObject(), "pathImage") }
        .ifBlank { jsonText(root, "pathImage") }
        .ifBlank { "https://phimimg.com" }.trimEnd('/')

    return items.mapNotNull { element ->
        try {
            val item = element.asJsonObject
            val name = jsonText(item, "name")
            val slug = jsonText(item, "slug")
            val poster = jsonText(item, "poster_url").ifBlank { jsonText(item, "thumb_url") }
            if (name.isBlank() || slug.isBlank()) null else Movie(
                name = name,
                slug = slug,
                poster = resolvePoster(poster, imageRoot),
                year = jsonText(item, "year"),
                origin = jsonText(item, "origin_name"),
                description = jsonText(item, "content"),
                countries = jsonNames(item, "country"),
                genres = jsonNames(item, "category"),
                audioLabels = scanAudioLabels(item)
            )
        } catch (_: Exception) { null }
    }
}

fun parseEpisodeServers(root: JsonObject): List<EpisodeServer> {
    val episodes = try { root.getAsJsonArray("episodes") } catch (_: Exception) { null }
        ?: return emptyList()
    return episodes.mapNotNull { element ->
        try {
            val server = element.asJsonObject
            val name = jsonText(server, "server_name").ifBlank { "Máy chủ" }
            val parsed = server.getAsJsonArray("server_data").mapNotNull { episodeElement ->
                try {
                    val e = episodeElement.asJsonObject
                    EpisodeSource(
                        jsonText(e, "name"), jsonText(e, "slug"),
                        jsonText(e, "link_m3u8"), jsonText(e, "link_embed")
                    )
                } catch (_: Exception) { null }
            }
            if (parsed.isEmpty()) null else EpisodeServer(name, parsed)
        } catch (_: Exception) { null }
    }
}

// HỒ SƠ VÀ DỮ LIỆU RIÊNG TỪNG HỒ SƠ

private const val PREFS = "vietstream_profiles"
private const val PROFILES_KEY = "profiles_json"
private const val ACTIVE_PROFILE_KEY = "active_profile_id"

private fun defaultProfiles(): List<UserProfile> = listOf(
    UserProfile("profile_1", "Hoàng", "😎"),
    UserProfile("profile_2", "Gia đình", "❤️"),
    UserProfile("profile_3", "Trẻ em", "🧸", isKids = true),
    UserProfile("profile_4", "Khách", "🎬")
)

private fun readProfiles(context: Context): List<UserProfile> {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val json = prefs.getString(PROFILES_KEY, null)
    val loaded = try {
        if (json.isNullOrBlank()) emptyList()
        else gson.fromJson<List<UserProfile>>(
            json, object : TypeToken<List<UserProfile>>() {}.type
        ) ?: emptyList()
    } catch (_: Exception) { emptyList() }

    val profiles = if (loaded.isNotEmpty()) loaded else defaultProfiles()
    if (loaded.isEmpty()) {
        prefs.edit().putString(PROFILES_KEY, gson.toJson(profiles)).apply()
    }

    // Chuyển yêu thích/lịch sử cũ sang hồ sơ đầu tiên để không mất dữ liệu cũ.
    val firstId = profiles.firstOrNull()?.id
    if (firstId != null) {
        val legacy = context.getSharedPreferences("vietstream_library", Context.MODE_PRIVATE)
        listOf("favorites" to "favorites_movies", "history" to "history_movies").forEach { (kind, oldKey) ->
            val newKey = profilePrefsKey(firstId, kind)
            if (prefs.getString(newKey, null) == null) {
                val oldJson = legacy.getString(oldKey, null)
                if (!oldJson.isNullOrBlank()) {
                    prefs.edit().putString(newKey, oldJson).apply()
                }
            }
        }
    }
    return profiles
}

private fun saveProfiles(context: Context, profiles: List<UserProfile>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(PROFILES_KEY, gson.toJson(profiles)).apply()
}

private fun profilePrefsKey(profileId: String, kind: String) = "${profileId}_$kind"

private fun readMovieList(context: Context, profileId: String, kind: String): List<Movie> {
    return try {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(profilePrefsKey(profileId, kind), null) ?: return emptyList()
        gson.fromJson<List<Movie>>(
            json, object : TypeToken<List<Movie>>() {}.type
        ) ?: emptyList()
    } catch (_: Exception) { emptyList() }
}

private fun saveMovieList(context: Context, profileId: String, kind: String, movies: List<Movie>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(profilePrefsKey(profileId, kind), gson.toJson(movies)).apply()
}

// POSTER

@Composable
fun MoviePoster(url: String, name: String, modifier: Modifier = Modifier) {
    var imageFailed by remember(url) { mutableStateOf(false) }
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        if (url.isNotBlank() && !imageFailed) {
            AsyncImage(model = url, contentDescription = name, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, onError = { imageFailed = true })
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("▶", color = MaterialTheme.colorScheme.primary, fontSize = 30.sp)
                Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// MÀN HÌNH HỒ SƠ

@Composable
private fun ProfilePickerScreen(
    profiles: List<UserProfile>,
    onSelect: (UserProfile) -> Unit,
    onManage: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoPhimWordmark(25)
        }
        Text("Ai đang xem?", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            profiles.chunked(2).forEach { rowProfiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
                    rowProfiles.forEach { profile ->
                        var profileFocused by remember(profile.id) { mutableStateOf(false) }
                        Column(
                            Modifier.width(125.dp).clip(RoundedCornerShape(12.dp))
                                .onFocusChanged { profileFocused = it.isFocused }
                                .clickable { onSelect(profile) }.focusable().padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(94.dp).clip(RoundedCornerShape(14.dp))
                                    .background(profileColor(profile.id))
                                    .border(if (profileFocused) 3.dp else 2.dp, if (profileFocused) MaterialTheme.colorScheme.primary else Color(0xFF444444), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(profile.avatar, fontSize = 42.sp)
                                if (profile.pinEnabled) {
                                    Text("🔒", fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp))
                                }
                            }
                            Text(profile.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (profile.isKids) Text("TRẺ EM", color = Color(0xFF79C8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onManage) { Text("Quản lý hồ sơ", color = MaterialTheme.colorScheme.onSurface) }
    }
}

private fun profileColor(id: String): Color = when (id) {
    "profile_1" -> Color(0xFF5A2028)
    "profile_2" -> Color(0xFF234B62)
    "profile_3" -> Color(0xFF385B32)
    else -> Color(0xFF604B7A)
}

@Composable
private fun PinDialog(
    profile: UserProfile,
    title: String,
    confirmText: String,
    externalError: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember(profile.id, title) { mutableStateOf("") }
    var error by remember(profile.id, title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text("${profile.avatar}  ${profile.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all(Char::isDigit)) {
                            pin = it
                            error = ""
                        }
                    },
                    label = { Text("Mã PIN 4 chữ số") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                if (externalError.isNotBlank()) Text(externalError, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pin.length != 4) error = "Hãy nhập đủ 4 chữ số."
                else onConfirm(pin)
            }) { Text(confirmText, color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy", color = MaterialTheme.colorScheme.onSurface) }
        }
    )
}

@Composable
private fun ProfileManager(
    profiles: List<UserProfile>,
    onSave: (List<UserProfile>) -> Unit,
    onClose: () -> Unit
) {
    var editing by remember { mutableStateOf<UserProfile?>(null) }
    var editName by remember { mutableStateOf("") }
    var editAvatar by remember { mutableStateOf("") }
    var editKids by remember { mutableStateOf(false) }
    var editPinEnabled by remember { mutableStateOf(false) }
    var editPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<UserProfile?>(null) }
    var pendingPin by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("QUẢN LÝ HỒ SƠ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Hồ sơ của bạn", color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("Mỗi hồ sơ có danh sách yêu thích và lịch sử xem riêng.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        profiles.forEach { profile ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        editing = profile
                        editName = profile.name
                        editAvatar = profile.avatar
                        editKids = profile.isKids
                        editPinEnabled = profile.pinEnabled
                        editPin = ""
                    }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(profileColor(profile.id)), contentAlignment = Alignment.Center) {
                    Text(profile.avatar, fontSize = 25.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text(if (profile.isKids) "Hồ sơ trẻ em" else if (profile.pinEnabled) "Đã bật khóa PIN" else "Không khóa PIN", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("Sửa ›", color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Hoàn tất") }
    }

    editing?.let { profile ->
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Sửa hồ sơ", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it.take(24) }, label = { Text("Tên hồ sơ") }, singleLine = true)
                    OutlinedTextField(value = editAvatar, onValueChange = { editAvatar = it.take(4) }, label = { Text("Biểu tượng / emoji") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editKids, onCheckedChange = { editKids = it })
                        Text("Hồ sơ trẻ em", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editPinEnabled, onCheckedChange = { editPinEnabled = it })
                        Text("Yêu cầu PIN khi mở hồ sơ", color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (editPinEnabled) {
                        OutlinedTextField(
                            value = editPin,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) editPin = it },
                            label = { Text(if (profile.pinEnabled) "PIN mới (để trống nếu giữ PIN cũ)" else "Đặt PIN 4 chữ số") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Text("Nếu tắt khóa, hồ sơ sẽ mở trực tiếp.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = editName.trim()
                    if (name.isBlank()) return@TextButton
                    if (editPinEnabled && !profile.pinEnabled && editPin.length != 4) {
                        showPin = true
                        return@TextButton
                    }
                    if (editPinEnabled && editPin.isNotEmpty() && editPin.length != 4) {
                        showPin = true
                        return@TextButton
                    }
                    val updatedProfile = profile.copy(
                        name = name,
                        avatar = editAvatar.ifBlank { "🎬" },
                        isKids = editKids,
                        pinEnabled = editPinEnabled,
                        pin = if (!editPinEnabled) "" else if (editPin.length == 4) editPin else profile.pin
                    )
                    onSave(profiles.map { if (it.id == profile.id) updatedProfile else it })
                    editing = null
                }) { Text("Lưu", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Hủy", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }
    if (showPin) {
        AlertDialog(
            onDismissRequest = { showPin = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Cần mã PIN hợp lệ", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Khi bật khóa mới, hãy nhập đủ 4 chữ số rồi nhấn Lưu.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton(onClick = { showPin = false }) { Text("Đã hiểu", color = MaterialTheme.colorScheme.primary) } }
        )
    }
}

// MAIN ACTIVITY

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        setContent {
            var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
            var updateDismissed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                updateInfo = UpdateChecker.check(this@MainActivity)
            }
            var appSettings by remember { mutableStateOf(AppSettings.read(this@MainActivity)) }
            val darkTheme = when (appSettings.theme) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            val accent = Color(appSettings.accentArgb.toInt())
            val scheme = if (darkTheme) {
                darkColorScheme(primary = accent, secondary = accent, background = Black, surface = Panel, onBackground = White, onSurface = White)
            } else {
                lightColorScheme(primary = accent, secondary = accent, background = Color(0xFFF5F5F5), surface = Color.White, onBackground = Color(0xFF171717), onSurface = Color(0xFF171717))
            }
            MaterialTheme(colorScheme = scheme) {
                VietStreamApp(
                    isTv = isTv,
                    appSettings = appSettings,
                    onSettingsChanged = { appSettings = AppSettings.read(this@MainActivity) }
                )
                val availableUpdate = updateInfo
                if (availableUpdate != null && !updateDismissed) {
                    AlertDialog(
                        onDismissRequest = { updateDismissed = true },
                        title = { Text("Có bản Bo Phim mới") },
                        text = { Text("Phiên bản ${availableUpdate.version} đã sẵn sàng. Bạn có muốn tải về không?") },
                        confirmButton = {
                            TextButton(onClick = {
                                updateDismissed = true
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(availableUpdate.downloadUrl)))
                            }) { Text("Tải bản mới") }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateDismissed = true }) { Text("Để sau") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VietStreamApp(
    isTv: Boolean = false,
    appSettings: AppSettings.Settings,
    onSettingsChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(readProfiles(context)) }
    var profilesEnabled by remember {
        mutableStateOf(AppPreferences.getBoolean(context, AppPreferences.KEY_PROFILES_ENABLED, false))
    }
    var activeProfile by remember {
        mutableStateOf<UserProfile?>(if (profilesEnabled) null else profiles.firstOrNull())
    }
    var needsProfilePicker by remember { mutableStateOf(profilesEnabled) }
    var managingProfiles by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<UserProfile?>(null) }
    var pinError by remember { mutableStateOf("") }

    var tab by remember { mutableStateOf("Trang chủ") }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var updatedMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var selected by remember { mutableStateOf<Movie?>(null) }
    var keyword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var canLoadMore by remember { mutableStateOf(false) }
    val movieListState = rememberLazyListState()
    var error by remember { mutableStateOf("") }
    var detailText by remember { mutableStateOf("") }
    var episodeServers by remember { mutableStateOf<List<EpisodeServer>>(emptyList()) }
    var filterCountry by remember { mutableStateOf("Tất cả") }
    var filterYear by remember { mutableStateOf("Tất cả") }
    var filterGenre by remember { mutableStateOf("Tất cả") }
    val profile = activeProfile
    val favorites = remember(profile?.id) {
        mutableStateOf(if (profile == null) emptyList() else readMovieList(context, profile.id, "favorites"))
    }
    val history = remember(profile?.id) {
        mutableStateOf(if (profile == null) emptyList() else readMovieList(context, profile.id, "history"))
    }
    val favoriteList = favorites.value
    val historyList = history.value

    fun updateFavorites(updated: List<Movie>) {
        favorites.value = updated
        if (profile != null) saveMovieList(context, profile.id, "favorites", updated)
    }

    fun updateHistory(updated: List<Movie>) {
        history.value = updated
        if (profile != null) saveMovieList(context, profile.id, "history", updated)
    }

    fun toggleFavorite(movie: Movie) {
        updateFavorites(
            if (favoriteList.any { it.slug == movie.slug }) favoriteList.filterNot { it.slug == movie.slug }
            else listOf(movie) + favoriteList
        )
    }

    fun addToHistory(movie: Movie) {
        updateHistory((listOf(movie) + historyList.filter { it.slug != movie.slug }).take(100))
    }

    fun runSearch() {
        if (keyword.isBlank()) return
        scope.launch {
            loading = true
            error = ""
            try {
                var results = parseMovies(MovieService.api.search(keyword.trim()))
                // Hồ sơ trẻ em chỉ tìm trong danh mục hoạt hình.
                if (profile?.isKids == true) {
                    results = results.filter { movie ->
                        movie.genres.orEmpty().any { it.contains("hoạt hình", true) || it.contains("animation", true) || it.contains("cartoon", true) }
                    }
                }
                movies = results
                if (movies.isEmpty()) error = "Không tìm thấy phim phù hợp."
            } catch (e: Exception) {
                error = "Tìm kiếm thất bại: ${e.message ?: "Lỗi kết nối"}"
            } finally {
                loading = false
            }
        }
    }

    fun selectProfile(target: UserProfile) {
        if (target.pinEnabled) {
            pinTarget = target
            pinError = ""
        } else {
            activeProfile = target
            needsProfilePicker = false
            managingProfiles = false
            tab = "Trang chủ"
        }
    }

    suspend fun loadMovies(section: String) {
        loading = true
        loadingMore = false
        currentPage = 1
        canLoadMore = false
        error = ""
        try {
            movies = when (section) {
                "Phim lẻ" -> parseMovies(MovieService.api.list("phim-le", 1)).also { canLoadMore = it.isNotEmpty() }
                "Phim bộ" -> parseMovies(MovieService.api.list("phim-bo", 1)).also { canLoadMore = it.isNotEmpty() }
                "Hoạt hình" -> parseMovies(MovieService.api.list("hoat-hinh", 1)).also { canLoadMore = it.isNotEmpty() }
                "Trang chủ" -> {
                    if (profile?.isKids == true) {
                        updatedMovies = emptyList()
                        parseMovies(MovieService.api.list("hoat-hinh"))
                    } else {
                        val homeMovies = parseMovies(MovieService.api.home())
                        // Lấy đúng danh sách phim mới cập nhật từ API riêng.
                        updatedMovies = try {
                            parseMovies(MovieService.api.list("phim-moi-cap-nhat", 1))
                        } catch (_: Exception) {
                            emptyList()
                        }
                        canLoadMore = updatedMovies.isNotEmpty()
                        homeMovies
                    }
                }
                "Yêu thích" -> favoriteList
                "Lịch sử" -> historyList
                else -> parseMovies(MovieService.api.home())
            }
            if (movies.isEmpty()) {
                error = when (section) {
                    "Yêu thích" -> "Hồ sơ này chưa có phim yêu thích."
                    "Lịch sử" -> "Hồ sơ này chưa xem phim nào."
                    else -> "Chưa có dữ liệu cho danh mục này."
                }
            }
        } catch (e: Exception) {
            error = "Không thể tải phim: ${e.message ?: "Lỗi kết nối"}"
        } finally {
            loading = false
        }
    }

    suspend fun loadNextMoviePage(section: String) {
        if (loading || loadingMore || !canLoadMore) return
        val apiType = when (section) {
            "Phim lẻ" -> "phim-le"
            "Phim bộ" -> "phim-bo"
            "Hoạt hình" -> "hoat-hinh"
            "Trang chủ" -> "phim-moi-cap-nhat"
            else -> return
        }
        loadingMore = true
        try {
            val nextPage = currentPage + 1
            val nextMovies = parseMovies(MovieService.api.list(apiType, nextPage))
            val existingSlugs = if (section == "Trang chủ") updatedMovies.map { it.slug }.toSet()
                else movies.map { it.slug }.toSet()
            val uniqueMovies = nextMovies.filterNot { it.slug in existingSlugs }
            if (uniqueMovies.isEmpty()) {
                canLoadMore = false
            } else {
                currentPage = nextPage
                if (section == "Trang chủ") {
                    updatedMovies = updatedMovies + uniqueMovies
                    movies = (movies + uniqueMovies).distinctBy { it.slug }
                } else {
                    movies = movies + uniqueMovies
                }
            }
        } catch (_: Exception) {
            // Keep the already-loaded content visible; retry on the next scroll.
        } finally {
            loadingMore = false
        }
    }

    LaunchedEffect(tab, movieListState, movies, updatedMovies, loading, loadingMore, canLoadMore) {
        if (tab !in listOf("Trang chủ", "Phim lẻ", "Phim bộ", "Hoạt hình") || loading || loadingMore || !canLoadMore) return@LaunchedEffect
        val layout = movieListState.layoutInfo
        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 5) {
            loadNextMoviePage(tab)
        }
    }

    LaunchedEffect(tab, profile?.id, needsProfilePicker, managingProfiles) {
        if (profile != null && !needsProfilePicker && !managingProfiles &&
            tab != "Tìm kiếm" && tab != "Cài đặt") {
            loadMovies(tab)
        }
    }

    LaunchedEffect(favoriteList, historyList, tab, profile?.id) {
        when (tab) {
            "Yêu thích" -> {
                movies = favoriteList
                error = if (favoriteList.isEmpty()) "Hồ sơ này chưa có phim yêu thích." else ""
            }
            "Lịch sử" -> {
                movies = historyList
                error = if (historyList.isEmpty()) "Hồ sơ này chưa xem phim nào." else ""
            }
        }
    }

    LaunchedEffect(selected?.slug) {
        val movie = selected ?: return@LaunchedEffect
        detailText = "Đang tải thông tin phim..."
        episodeServers = emptyList()
        try {
            val response = MovieService.api.detail(movie.slug)
            val info = try { response.getAsJsonObject("movie") } catch (_: Exception) { null }
            detailText = info?.let { jsonText(it, "content") }.orEmpty()
                .replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").trim()
                .ifBlank { movie.description.ifBlank { "Chưa có giới thiệu." } }
            episodeServers = parseEpisodeServers(response)
            if (episodeServers.isEmpty()) detailText += "\n\nChưa có máy chủ hoặc tập phim khả dụng."
        } catch (e: Exception) {
            detailText = "Không tải được chi tiết phim: ${e.message ?: "Lỗi kết nối"}"
        }
    }

    BackHandler(enabled = selected != null || tab != "Trang chủ") {
        if (selected != null) selected = null else tab = "Trang chủ"
    }

    val tabs = if (profile?.isKids == true) {
        listOf("Trang chủ", "Hoạt hình", "Cài đặt")
    } else {
        listOf("Trang chủ", "Phim lẻ", "Phim bộ", "Hoạt hình", "Cài đặt")
    }

    if (needsProfilePicker) {
        if (managingProfiles) {
            ProfileManager(
                profiles = profiles,
                onSave = {
                    profiles = it
                    saveProfiles(context, it)
                },
                onClose = { managingProfiles = false }
            )
        } else {
            ProfilePickerScreen(
                profiles = profiles,
                onSelect = { selectProfile(it) },
                onManage = { managingProfiles = true }
            )
        }
        pinTarget?.let { target ->
            PinDialog(
                profile = target,
                title = "Nhập PIN hồ sơ",
                confirmText = "Mở hồ sơ",
                externalError = pinError,
                onDismiss = { pinTarget = null; pinError = "" },
                onConfirm = { entered ->
                    if (entered == target.pin) {
                        activeProfile = target
                        needsProfilePicker = false
                        managingProfiles = false
                        pinTarget = null
                        pinError = ""
                        tab = "Trang chủ"
                    } else {
                        pinError = "Mã PIN không đúng."
                        Toast.makeText(context, pinError, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFF080D18),
        bottomBar = {
            if (!isTv) {
                NavigationBar(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        .height(86.dp)
                        .liquidGlass(RoundedCornerShape(28.dp)),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
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
                                        else -> "⚙"
                                    },
                                    color = if (tab == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            label = { Text(item, fontSize = 11.sp, fontWeight = if (tab == item) FontWeight.Bold else FontWeight.Medium, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color(0xFF382020)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF111B2B), Color(0xFF080D18), Color(0xFF05070D))
            )
        ).padding(padding)) {
            if (isTv) {
                Column(Modifier.width(190.dp).fillMaxHeight().padding(8.dp)
                    .liquidGlass(RoundedCornerShape(24.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoPhimWordmark(19)
                    tabs.forEach { item -> TvNavigationItem(item, tab == item) { tab = item } }
                    if (profile?.isKids != true) {
                        TvNavigationItem("Tìm kiếm", tab == "Tìm kiếm") { tab = "Tìm kiếm" }
                        TvNavigationItem("Yêu thích", tab == "Yêu thích") { tab = "Yêu thích" }
                        TvNavigationItem("Lịch sử", tab == "Lịch sử") { tab = "Lịch sử" }
                    }
                }
            }

            Column(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF101827), Color(0xFF080D18)))
            )) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = if (isTv) 28.dp else 18.dp, vertical = 12.dp)
                        .liquidGlass(RoundedCornerShape(22.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoPhimWordmark(if (isTv) 27 else 23)
                    Spacer(Modifier.weight(1f))
                    if (profilesEnabled) {
                        TextButton(onClick = { needsProfilePicker = true; selected = null }) {
                            Text("${profile?.avatar ?: "👤"} ${profile?.name ?: "Hồ sơ"}", color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        }
                    }
                    if (!isTv && profile?.isKids != true) {
                        TextButton(onClick = { tab = "Tìm kiếm" }) { Text("⌕", color = MaterialTheme.colorScheme.onSurface) }
                    }
                }

                when (tab) {
                    "Cài đặt" -> SettingsScreen(
                        isTv = isTv,
                        isKids = profile?.isKids == true,
                        profilesEnabled = profilesEnabled,
                        onProfilesEnabledChanged = { enabled ->
                            profilesEnabled = enabled
                            AppPreferences.setBoolean(context, AppPreferences.KEY_PROFILES_ENABLED, enabled)
                            if (enabled) {
                                activeProfile = null
                                needsProfilePicker = true
                            } else {
                                activeProfile = profiles.firstOrNull()
                                needsProfilePicker = false
                                managingProfiles = false
                                pinTarget = null
                            }
                        },
                        favoriteCount = favoriteList.size,
                        historyCount = historyList.size,
                        appSettings = appSettings,
                        onSettingsChanged = onSettingsChanged,
                        onNavigate = { tab = it },
                        onClearFavorites = { updateFavorites(emptyList()) },
                        onClearHistory = { updateHistory(emptyList()) }
                    )
                    else -> {
                        val visibleMovies = movies.filter { movie ->
                            (filterCountry == "Tất cả" || movie.countries.orEmpty().any { it.equals(filterCountry, true) }) &&
                                (filterYear == "Tất cả" || movie.year == filterYear) &&
                                (filterGenre == "Tất cả" || movie.genres.orEmpty().any { it.equals(filterGenre, true) })
                        }
                        // Poster dùng lưới dọc; không cần vuốt ngang để khám phá phim.

                        // Toàn bộ nội dung phim, bộ lọc và poster dùng chung một vùng cuộn.
                        LazyColumn(
                            state = movieListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (tab == "Tìm kiếm") {
                                item(key = "search_field") {
                                    OutlinedTextField(
                                        value = keyword,
                                        onValueChange = { keyword = it },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                                        placeholder = { Text("Nhập tên phim...") },
                                        trailingIcon = {
                                            TextButton(onClick = { runSearch() }) { Text("Tìm", color = MaterialTheme.colorScheme.primary) }
                                        }
                                    )
                                }
                            }

                            if (tab == "Yêu thích" || tab == "Lịch sử") {
                                item(key = "list_title") {
                                    Text(tab, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }

                            if (tab == "Trang chủ" && movies.isNotEmpty()) {
                                item(key = "hero_banner") {
                                    // Ưu tiên danh sách phim mới cập nhật; chỉ dự phòng khi API không có dữ liệu.
                                    val heroSource = updatedMovies.ifEmpty { movies }
                                    val heroCandidates = heroSource.take(8)
                                    var heroIndex by remember(heroSource) { mutableIntStateOf(0) }
                                    LaunchedEffect(heroCandidates, tab) {
                                        if (heroCandidates.size > 1) {
                                            while (true) {
                                                delay(5000)
                                                heroIndex = (heroIndex + 1) % heroCandidates.size
                                            }
                                        }
                                    }
                                    val hero = heroCandidates.getOrNull(heroIndex) ?: movies.first()
                                    Column(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Crossfade(targetState = hero, label = "home_hero_carousel") { currentHero ->
                                            Box(
                                                Modifier.fillMaxWidth().height(if (isTv) 360.dp else 270.dp)
                                                    .liquidGlass(RoundedCornerShape(20.dp))
                                                    .clickable { selected = currentHero }
                                            ) {
                                                MoviePoster(currentHero.poster, currentHero.name, Modifier.fillMaxSize())
                                                Box(Modifier.fillMaxSize().background(
                                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.96f)))
                                                ))
                                                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                                    Text("BO PHIM  •  ĐỀ XUẤT MỚI", color = BoOrange, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                                    Text(currentHero.name, color = Color.White, fontSize = if (isTv) 30.sp else 22.sp, fontWeight = FontWeight.Black, maxLines = 2)
                                                    Text("${currentHero.year} • ${currentHero.origin}", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp)
                                                    Spacer(Modifier.height(8.dp))
                                                    Button(onClick = { selected = currentHero }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                                        Text("▶  Xem ngay")
                                                    }
                                                }
                                            }
                                        }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            heroCandidates.forEachIndexed { index, _ ->
                                                Box(
                                                    Modifier.padding(horizontal = 3.dp).size(if (index == heroIndex) 18.dp else 6.dp, 6.dp)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(if (index == heroIndex) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f))
                                                        .clickable { heroIndex = index }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "movie_section_title") {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(
                                            when (tab) {
                                                "Trang chủ" -> "Mới cập nhật"
                                                "Tìm kiếm" -> "Kết quả tìm kiếm"
                                                else -> tab
                                            },
                                            color = MaterialTheme.colorScheme.onSurface, fontSize = if (isTv) 25.sp else 20.sp, fontWeight = FontWeight.Bold
                                        )
                                        if (tab == "Trang chủ") Text(
                                            "Những bộ phim vừa có tập mới",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                }
                            }

                            if (movies.isNotEmpty()) {
                                item(key = "movie_filters") {
                                    MovieFilters(
                                        movies = movies, country = filterCountry, year = filterYear, genre = filterGenre,
                                        onCountry = { filterCountry = it }, onYear = { filterYear = it }, onGenre = { filterGenre = it },
                                        onReset = { filterCountry = "Tất cả"; filterYear = "Tất cả"; filterGenre = "Tất cả" }
                                    )
                                }
                            }

                            when {
                                loading -> item(key = "loading_state") {
                                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                error.isNotBlank() -> item(key = "error_state") {
                                    Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                                        Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                movies.isEmpty() -> item(key = "empty_state") {
                                    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                                        Text("Chưa có dữ liệu phim.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                visibleMovies.isEmpty() -> item(key = "filtered_empty_state") {
                                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                        Text("Không có phim phù hợp với bộ lọc.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                else -> {
                                    val railMovies = if (tab == "Trang chủ" && updatedMovies.isNotEmpty()) updatedMovies else visibleMovies
                                    lazyItems(items = railMovies.chunked(2), key = { row -> row.firstOrNull()?.slug ?: "empty_row" }) { row ->
                                        Row(Modifier.fillMaxWidth().padding(horizontal = if (isTv) 22.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            row.forEach { movie ->
                                                Box(Modifier.weight(1f)) {
                                                    MovieCard(movie, favoriteList.any { it.slug == movie.slug }, isTv) { selected = movie }
                                                }
                                            }
                                            if (row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            if (tab == "Trang chủ" && movies.isNotEmpty()) {
                                    item(key = "recommended_section_title") {
                                        Column(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp)) {
                                            Text(
                                                "Khám phá thêm",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = if (isTv) 25.sp else 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Các lựa chọn khác dành cho bạn",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    item(key = "recommended_movie_grid_title") {
                                        Text("Vuốt lên để khám phá thêm", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                    }
                                    lazyItems(items = visibleMovies.chunked(2), key = { row -> "discover_" + (row.firstOrNull()?.slug ?: "empty") }) { row ->
                                        Row(Modifier.fillMaxWidth().padding(horizontal = if (isTv) 22.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            row.forEach { movie ->
                                                Box(Modifier.weight(1f)) {
                                                    MovieCard(movie, favoriteList.any { it.slug == movie.slug }, isTv) { selected = movie }
                                                }
                                            }
                                            if (row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                            }

                            if (loadingMore) {
                                item(key = "loading_more_movies") {
                                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                    }
                                }
                            }

                            if (tab == "Tìm kiếm") {
                                item(key = "search_shortcuts") {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { tab = "Yêu thích" }) { Text("♥ Yêu thích", color = MaterialTheme.colorScheme.primary) }
                                        TextButton(onClick = { tab = "Lịch sử" }) { Text("◷ Lịch sử", color = MaterialTheme.colorScheme.onSurface) }
                                    }
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
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(movie.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = if (isTv) 650.dp else 520.dp).verticalScroll(rememberScrollState())) {
                    MoviePoster(movie.poster, movie.name, Modifier.fillMaxWidth().height(if (isTv) 240.dp else 180.dp).clip(RoundedCornerShape(10.dp)))
                    Text("${movie.origin} • ${movie.year}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                    Text(detailText, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, lineHeight = 22.sp)
                    if (movie.lastEpisodeUrl.orEmpty().startsWith("http", true)) {
                        Button(
                            onClick = {
                                addToHistory(movie)
                                if (movie.lastEpisodeIsM3u8 && movie.lastEpisodeUrls.orEmpty().isNotEmpty()) {
                                    context.startActivity(Intent(context, InAppPlayerActivity::class.java).apply {
                                        putExtra("video_title", movie.lastEpisodeTitles.orEmpty().getOrNull(movie.lastEpisodeIndex) ?: "${movie.name} - ${movie.lastEpisodeName.orEmpty()}")
                                        putStringArrayListExtra("episode_titles", ArrayList(movie.lastEpisodeTitles.orEmpty()))
                                        putStringArrayListExtra("episode_urls", ArrayList(movie.lastEpisodeUrls.orEmpty()))
                                        putExtra("episode_index", movie.lastEpisodeIndex.coerceIn(0, movie.lastEpisodeUrls.orEmpty().lastIndex))
                                    })
                                } else {
                                    context.startActivity(Intent(context, InAppWebActivity::class.java).apply {
                                        putExtra("web_title", "${movie.name} - ${movie.lastEpisodeName.orEmpty()}")
                                        putExtra("web_url", movie.lastEpisodeUrl)
                                    })
                                }
                                selected = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("▶  Tiếp tục xem ${movie.lastEpisodeName.orEmpty()}".take(80), color = MaterialTheme.colorScheme.onSurface) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { toggleFavorite(movie) }) {
                            Text(if (favoriteList.any { it.slug == movie.slug }) "♥ Đã yêu thích" else "♡ Yêu thích", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { addToHistory(movie); selected = null; tab = "Lịch sử" }) {
                            Text("◷ Lịch sử xem", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Text("CHỌN MÁY CHỦ VÀ TẬP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
                    if (episodeServers.isEmpty()) Text("Chưa có tập phim khả dụng.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    episodeServers.forEach { server ->
                        Text(server.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                        server.episodes.forEach { episode ->
                            Row(                                Modifier.fillMaxWidth().padding(vertical = 3.dp).liquidGlass(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val videoUrl = episode.m3u8.trim()
                                        val embedUrl = episode.embed.trim()
                                        val validM3u8 = videoUrl.startsWith("https://", true) || videoUrl.startsWith("http://", true)
                                        val validEmbed = embedUrl.startsWith("https://", true) || embedUrl.startsWith("http://", true)
                                        when {
                                            validM3u8 -> {
                                                val playlist = server.episodes.filter {
                                                    it.m3u8.startsWith("https://", true) || it.m3u8.startsWith("http://", true)
                                                }
                                                val index = playlist.indexOfFirst { it.slug == episode.slug }.coerceAtLeast(0)
                                                val titles = playlist.map { "${movie.name} - ${it.name}" }
                                                val urls = playlist.map { it.m3u8.trim() }
                                                addToHistory(movie.copy(
                                                    lastEpisodeName = episode.name,
                                                    lastEpisodeUrl = videoUrl,
                                                    lastEpisodeIsM3u8 = true,
                                                    lastEpisodeTitles = titles,
                                                    lastEpisodeUrls = urls,
                                                    lastEpisodeIndex = index
                                                ))
                                                val episodeTitle = "${movie.name} - ${episode.name}"
                                                val useVlc = appSettings.player == AppSettings.PLAYER_VLC
                                                val openedExternally = useVlc && openInVlc(context, videoUrl, episodeTitle)
                                                if (useVlc && !openedExternally) {
                                                    Toast.makeText(context, "Không mở được VLC. Hãy cài VLC; app sẽ mở trình phát tích hợp.", Toast.LENGTH_LONG).show()
                                                }
                                                if (!openedExternally) {
                                                    context.startActivity(Intent(context, InAppPlayerActivity::class.java).apply {
                                                        putExtra("video_title", episodeTitle)
                                                        putExtra("video_url", videoUrl)
                                                        putStringArrayListExtra("episode_titles", ArrayList(titles))
                                                        putStringArrayListExtra("episode_urls", ArrayList(urls))
                                                        putExtra("episode_index", index)
                                                    })
                                                }
                                                selected = null
                                            }
                                            validEmbed -> {
                                                addToHistory(movie.copy(
                                                    lastEpisodeName = episode.name,
                                                    lastEpisodeUrl = embedUrl,
                                                    lastEpisodeIsM3u8 = false,
                                                    lastEpisodeTitles = emptyList(),
                                                    lastEpisodeUrls = emptyList(),
                                                    lastEpisodeIndex = 0
                                                ))
                                                context.startActivity(Intent(context, InAppWebActivity::class.java).apply {
                                                    putExtra("web_title", "${movie.name} - ${episode.name}")
                                                    putExtra("web_url", embedUrl)
                                                })
                                                selected = null
                                            }
                                            else -> Toast.makeText(context, "Tập này chưa có đường dẫn phát.", Toast.LENGTH_SHORT).show()
                                        }
                                    }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("▶", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(episode.name.ifBlank { "Tập phim" }, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Text("Xem", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Đóng", color = MaterialTheme.colorScheme.primary) } }
        )
    }
}

// BỘ LỌC PHIM

@Composable
private fun MovieFilterRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    if (options.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option, fontSize = 13.sp, maxLines = 1) })
            }
        }
    }
}

@Composable
private fun MovieFilters(
    movies: List<Movie>, country: String, year: String, genre: String,
    onCountry: (String) -> Unit, onYear: (String) -> Unit, onGenre: (String) -> Unit, onReset: () -> Unit
) {
    val countries = listOf("Tất cả") + movies.flatMap { it.countries.orEmpty() }.distinct().sorted()
    val years = listOf("Tất cả") + movies.map { it.year }.filter { it.isNotBlank() }.distinct().sortedDescending()
    val genres = listOf("Tất cả") + movies.flatMap { it.genres.orEmpty() }.distinct().sorted()
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LỌC PHIM", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("Xóa bộ lọc", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp) }
        }
        MovieFilterRow("Quốc gia", countries, country, onCountry)
        MovieFilterRow("Năm sản xuất", years, year, onYear)
        MovieFilterRow("Thể loại", genres, genre, onGenre)
    }
}

// CÀI ĐẶT

@Composable
private fun SettingsScreen(
    isTv: Boolean, isKids: Boolean = false,
    profilesEnabled: Boolean,
    onProfilesEnabledChanged: (Boolean) -> Unit,
    favoriteCount: Int, historyCount: Int,
    appSettings: AppSettings.Settings,
    onSettingsChanged: () -> Unit,
    onNavigate: (String) -> Unit, onClearFavorites: () -> Unit, onClearHistory: () -> Unit
) {
    val settingsContext = LocalContext.current
    var languageExpanded by remember { mutableStateOf(false) }
    var showClearFavorites by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal = if (isTv) 28.dp else 18.dp)) {
        Text("CÀI ĐẶT", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Thiết lập ứng dụng", color = MaterialTheme.colorScheme.onSurface, fontSize = if (isTv) 30.sp else 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        BoPhimWordmark(16)

        Spacer(Modifier.height(20.dp))
        Text("HỒ SƠ NGƯỜI DÙNG", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Bật tính năng hồ sơ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("Tắt: vào thẳng Trang chủ. Bật: chọn hồ sơ khi mở ứng dụng.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Switch(checked = profilesEnabled, onCheckedChange = onProfilesEnabledChanged)
        }

        Spacer(Modifier.height(22.dp))
        Text("GIAO DIỆN", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Chế độ hiển thị", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AppSettings.THEME_SYSTEM to "Theo hệ thống", AppSettings.THEME_DARK to "Tối", AppSettings.THEME_LIGHT to "Sáng").forEach { (value, label) ->
                FilterChip(selected = appSettings.theme == value, onClick = { AppSettings.saveTheme(settingsContext, value); onSettingsChanged() }, label = { Text(label, fontSize = 11.sp) })
            }
        }

        Text("Màu chủ đề", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        val accents = listOf("Đỏ" to 0xFFE50914L, "Xanh dương" to 0xFF2196F3L, "Xanh lá" to 0xFF4CAF50L, "Tím" to 0xFF9C27B0L, "Cam" to 0xFFFF9800L)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accents.forEach { (label, argb) ->
                FilterChip(selected = appSettings.accentArgb == argb, onClick = { AppSettings.saveAccent(settingsContext, argb); onSettingsChanged() }, label = { Text(label, fontSize = 11.sp) }, leadingIcon = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color(argb.toInt()))) })
            }
        }

        Text("Ngôn ngữ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        val languages = listOf("vi" to "Tiếng Việt", "en" to "English", "zh" to "中文", "ja" to "日本語", "ko" to "한국어", "th" to "ไทย", "fr" to "Français", "es" to "Español")
        Box {
            OutlinedButton(onClick = { languageExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(languages.firstOrNull { it.first == appSettings.language }?.second ?: "Tiếng Việt")
            }
            DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                languages.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        AppSettings.saveLanguage(settingsContext, code)
                        languageExpanded = false
                        onSettingsChanged()
                    })
                }
            }
        }
        Text("Lựa chọn ngôn ngữ đã được lưu. Muốn dịch toàn bộ giao diện cần bổ sung bản dịch cho từng màn hình.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(18.dp))
        Text("TRÌNH PHÁT VÀ ÂM THANH", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Trình phát mặc định", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = appSettings.player == AppSettings.PLAYER_INTERNAL, onClick = { AppSettings.savePlayer(settingsContext, AppSettings.PLAYER_INTERNAL); onSettingsChanged() }, label = { Text("Tích hợp") })
            FilterChip(selected = appSettings.player == AppSettings.PLAYER_VLC, onClick = { AppSettings.savePlayer(settingsContext, AppSettings.PLAYER_VLC); onSettingsChanged() }, label = { Text("VLC") })
        }
        Text("VLC cần được cài trên thiết bị; một số đường dẫn có yêu cầu xác thực có thể không phát được bên ngoài app.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        val playbackPrefs = remember { settingsContext.getSharedPreferences("vietstream_settings", Context.MODE_PRIVATE) }
        val audioSupport = remember(settingsContext) {
            try {
                AudioSupport.detect(settingsContext)
            } catch (_: Throwable) {
                AudioSupport(false, false, false)
            }
        }
        var subtitleSize by remember { mutableIntStateOf(playbackPrefs.getInt("subtitle_size", 20)) }
        var subtitleColor by remember { mutableIntStateOf(playbackPrefs.getInt("subtitle_color", android.graphics.Color.WHITE)) }
        Spacer(Modifier.height(16.dp))
        Text("PHỤ ĐỀ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Cỡ chữ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(14, 16, 18, 20, 24, 28, 32).forEach { size ->
                FilterChip(selected = subtitleSize == size, onClick = {
                    subtitleSize = size
                    playbackPrefs.edit().putInt("subtitle_size", size).apply()
                }, label = { Text("${size}sp") })
            }
        }
        Text("Màu chữ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        val subtitleColors = listOf(
            "Trắng" to android.graphics.Color.WHITE, "Vàng" to android.graphics.Color.YELLOW,
            "Xanh lá" to android.graphics.Color.GREEN, "Xanh ngọc" to android.graphics.Color.CYAN,
            "Đỏ" to android.graphics.Color.RED, "Hồng tím" to android.graphics.Color.MAGENTA
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            subtitleColors.forEach { (label, color) ->
                FilterChip(selected = subtitleColor == color, onClick = {
                    subtitleColor = color
                    playbackPrefs.edit().putInt("subtitle_color", color).apply()
                }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        Text("Áp dụng cho track phụ đề rời được trình phát tích hợp giải mã. Phụ đề cháy sẵn trong hình hoặc phát qua trang nhúng không thể đổi kiểu chữ.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))

        Text("Ưu tiên âm thanh", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AppSettings.AUDIO_AUTO to "Tự động", AppSettings.AUDIO_STEREO to "Stereo", AppSettings.AUDIO_DOLBY to "Dolby Atmos", AppSettings.AUDIO_SPATIAL to "Spatial 360°").forEach { (value, label) ->
                FilterChip(selected = appSettings.audioMode == value, onClick = { AppSettings.saveAudioMode(settingsContext, value); onSettingsChanged() }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        Text("Trạng thái thiết bị: Dolby Atmos " + (if (audioSupport.dolbyAtmos) "có khả năng hỗ trợ" else "chưa phát hiện hỗ trợ") + " • Spatial Audio " + (if (audioSupport.spatialReady) "đang sẵn sàng" else "không khả dụng/chưa bật") + ".", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Atmos cần nguồn phim có track Atmos và thiết bị/đường âm thanh tương thích. Spatial 360° dùng spatializer của Android cho âm thanh đa kênh; Stereo không thể biến thành Atmos gốc.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(24.dp))
        Text("THƯ VIỆN CỦA BẠN", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (!isKids) {
            SettingsItem("Phim yêu thích", "$favoriteCount phim đã lưu") { onNavigate("Yêu thích") }
            SettingsItem("Lịch sử xem", "$historyCount phim trong lịch sử") { onNavigate("Lịch sử") }
        }
        Spacer(Modifier.height(20.dp))
        Text("KHÁM PHÁ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (!isKids) {
            SettingsItem("Tìm kiếm phim", "Tìm phim theo tên") { onNavigate("Tìm kiếm") }
            SettingsItem("Phim lẻ", "Khám phá phim lẻ") { onNavigate("Phim lẻ") }
            SettingsItem("Phim bộ", "Khám phá phim bộ") { onNavigate("Phim bộ") }
        }
        SettingsItem("Phim hoạt hình", "Khám phá phim hoạt hình") { onNavigate("Hoạt hình") }
        Spacer(Modifier.height(20.dp))
        Text("QUẢN LÝ DỮ LIỆU", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        SettingsItem("Xóa danh sách yêu thích", "Xóa toàn bộ phim đã lưu") { showClearFavorites = true }
        SettingsItem("Xóa lịch sử xem", "Xóa toàn bộ lịch sử trên thiết bị") { showClearHistory = true }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
        Text("BO PHIM • Phiên bản 1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(vertical = 18.dp))
    }
    if (showClearFavorites) AlertDialog(
        onDismissRequest = { showClearFavorites = false }, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Xóa phim yêu thích?", color = MaterialTheme.colorScheme.onSurface) },
        text = { Text("Toàn bộ danh sách phim yêu thích của hồ sơ hiện tại sẽ bị xóa.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = { onClearFavorites(); showClearFavorites = false }) { Text("Xóa", color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = { showClearFavorites = false }) { Text("Hủy", color = MaterialTheme.colorScheme.onSurface) } }
    )
    if (showClearHistory) AlertDialog(
        onDismissRequest = { showClearHistory = false }, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Xóa lịch sử xem?", color = MaterialTheme.colorScheme.onSurface) },
        text = { Text("Toàn bộ lịch sử xem của hồ sơ hiện tại sẽ bị xóa.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = { onClearHistory(); showClearHistory = false }) { Text("Xóa", color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text("Hủy", color = MaterialTheme.colorScheme.onSurface) } }
    )
}

@Composable
private fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
            .then(if (focused) Modifier.background(Color(0xFF25364D), RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            else Modifier.liquidGlass(RoundedCornerShape(16.dp)))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
    }
}

@Composable
private fun TvNavigationItem(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF382020) else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable()
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = if (selected || focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun MovieCard(movie: Movie, favorite: Boolean = false, isTv: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (focused) Modifier.border(2.dp, BoOrange, RoundedCornerShape(12.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(10.dp))
        ) {
            MoviePoster(movie.poster, movie.name, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.94f)
                        )
                    )
                )
            )
            if (movie.year.isNotBlank()) {
                Text(
                    movie.year,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (favorite) {
                Text("♥", color = BoOrange, fontSize = 19.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 9.dp, vertical = 10.dp)) {
                Text(
                    movie.name,
                    color = Color.White,
                    fontSize = if (isTv) 19.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (isTv) 21.sp else 19.sp
                )
                if (movie.audioLabels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        movie.audioLabels.forEach { audioLabel ->
                            Text(
                                audioLabel,
                                color = Color.White,
                                fontSize = if (isTv) 9.sp else 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .background(
                                        if (audioLabel == "DOLBY ATMOS") Color(0xFF292929) else Color(0xFF174A70),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                if (movie.origin.isNotBlank()) {
                    Text(
                        movie.origin,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (focused) {
                Box(
                    Modifier.align(Alignment.Center).size(if (isTv) 52.dp else 42.dp)
                        .clip(CircleShape)
                        .background(BoOrange.copy(alpha = 0.94f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = if (isTv) 23.sp else 19.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}