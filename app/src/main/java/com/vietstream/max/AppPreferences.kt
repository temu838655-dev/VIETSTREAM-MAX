package com.vietstream.max

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Các giá trị cài đặt được lưu trong SharedPreferences.
 *
 * File này không tự thay đổi giao diện toàn ứng dụng.
 * MainActivity và trình phát sẽ đọc các giá trị này ở bước tích hợp.
 */
object AppPreferences {

    private const val PREFS_NAME = "vietstream_settings"

    const val KEY_THEME = "app_theme"
    const val KEY_ACCENT = "app_accent"
    const val KEY_LANGUAGE = "app_language"
    const val KEY_PLAYER = "playback_engine"
    const val KEY_AUDIO = "audio_preference"
    const val KEY_REFRESH_RATE = "smooth_motion"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val PLAYER_INTERNAL = "internal"
    const val PLAYER_VLC = "vlc"
    const val PLAYER_ASK = "ask"

    const val AUDIO_AUTO = "auto"
    const val AUDIO_STEREO = "stereo"
    const val AUDIO_ATMOS = "atmos"

    const val MOTION_SYSTEM = "system"
    const val MOTION_HIGH = "high"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getString(
        context: Context,
        key: String,
        defaultValue: String
    ): String {
        return prefs(context).getString(key, defaultValue) ?: defaultValue
    }

    fun setString(
        context: Context,
        key: String,
        value: String
    ) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun getBoolean(
        context: Context,
        key: String,
        defaultValue: Boolean
    ): Boolean {
        return prefs(context).getBoolean(key, defaultValue)
    }

    fun setBoolean(
        context: Context,
        key: String,
        value: Boolean
    ) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun getAccent(context: Context): Long {
        return prefs(context).getLong(KEY_ACCENT, 0xFFE50914L)
    }

    fun setAccent(context: Context, color: Long) {
        prefs(context).edit().putLong(KEY_ACCENT, color).apply()
    }

    fun accentColor(context: Context): Color {
        return Color(getAccent(context).toULong().toLong())
    }

    /**
     * Mở video bằng VLC nếu VLC đã được cài.
     *
     * Lưu ý: một số URL cần cookie, header hoặc xác thực riêng
     * có thể không phát được trong ứng dụng bên ngoài.
     */
    fun openWithVlc(
        context: Context,
        url: String,
        title: String
    ): Boolean {
        if (!url.startsWith("https://", true) &&
            !url.startsWith("http://", true)
        ) {
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("org.videolan.vlc")
        }

        return try {
            val pm = context.packageManager
            val canOpen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(
                        PackageManager.MATCH_DEFAULT_ONLY.toLong()
                    )
                ).isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(
                    intent,
                    PackageManager.MATCH_DEFAULT_ONLY
                ).isNotEmpty()
            }

            if (!canOpen) return false

            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Hiển thị danh sách trình phát ngoài có thể mở URL.
     * Trả về false nếu không có ứng dụng phù hợp.
     */
    fun openWithExternalChooser(
        context: Context,
        url: String,
        title: String
    ): Boolean {
        if (!url.startsWith("https://", true) &&
            !url.startsWith("http://", true)
        ) {
            return false
        }

        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                putExtra("title", title)
            }

            context.startActivity(
                Intent.createChooser(viewIntent, "Chọn trình phát").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}

private data class SettingChoice(
    val value: String,
    val title: String,
    val description: String
)

@Composable
fun AppSettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var theme by remember {
        mutableStateOf(
            AppPreferences.getString(
                context,
                AppPreferences.KEY_THEME,
                AppPreferences.THEME_SYSTEM
            )
        )
    }

    var accent by remember {
        mutableStateOf(AppPreferences.getAccent(context))
    }

    var language by remember {
        mutableStateOf(
            AppPreferences.getString(
                context,
                AppPreferences.KEY_LANGUAGE,
                "vi"
            )
        )
    }

    var player by remember {
        mutableStateOf(
            AppPreferences.getString(
                context,
                AppPreferences.KEY_PLAYER,
                AppPreferences.PLAYER_INTERNAL
            )
        )
    }

    var audio by remember {
        mutableStateOf(
            AppPreferences.getString(
                context,
                AppPreferences.KEY_AUDIO,
                AppPreferences.AUDIO_AUTO
            )
        )
    }

    var motion by remember {
        mutableStateOf(
            AppPreferences.getString(
                context,
                AppPreferences.KEY_REFRESH_RATE,
                AppPreferences.MOTION_SYSTEM
            )
        )
    }

    var dialogKey by remember { mutableStateOf<String?>(null) }

    val themes = listOf(
        SettingChoice(
            AppPreferences.THEME_SYSTEM,
            "Tự động theo hệ thống",
            "Dùng chế độ sáng hoặc tối của Android"
        ),
        SettingChoice(
            AppPreferences.THEME_LIGHT,
            "Sáng",
            "Giao diện nền sáng"
        ),
        SettingChoice(
            AppPreferences.THEME_DARK,
            "Tối",
            "Giao diện tối dành cho xem phim"
        )
    )

    val accents = listOf(
        0xFFE50914L to "Đỏ VIETSTREAM",
        0xFF2196F3L to "Xanh dương",
        0xFF4CAF50L to "Xanh lá",
        0xFFFF9800L to "Cam",
        0xFF9C27B0L to "Tím",
        0xFFFFFFFFL to "Trắng"
    )

    val languages = listOf(
        SettingChoice("vi", "Tiếng Việt", "Vietnamese"),
        SettingChoice("en", "English", "English"),
        SettingChoice("zh", "中文", "Chinese"),
        SettingChoice("ja", "日本語", "Japanese"),
        SettingChoice("ko", "한국어", "Korean"),
        SettingChoice("th", "ภาษาไทย", "Thai"),
        SettingChoice("id", "Bahasa Indonesia", "Indonesian"),
        SettingChoice("fr", "Français", "French"),
        SettingChoice("es", "Español", "Spanish")
    )

    val players = listOf(
        SettingChoice(
            AppPreferences.PLAYER_INTERNAL,
            "VIETSTREAM MAX",
            "Dùng trình phát tích hợp"
        ),
        SettingChoice(
            AppPreferences.PLAYER_VLC,
            "VLC",
            "Mở bằng VLC nếu đã cài"
        ),
        SettingChoice(
            AppPreferences.PLAYER_ASK,
            "Hỏi mỗi lần",
            "Hiện lựa chọn trình phát khi mở video"
        )
    )

    val audioChoices = listOf(
        SettingChoice(
            AppPreferences.AUDIO_AUTO,
            "Tự động",
            "Ưu tiên track âm thanh phù hợp"
        ),
        SettingChoice(
            AppPreferences.AUDIO_STEREO,
            "Stereo",
            "Ưu tiên âm thanh hai kênh nếu có"
        ),
        SettingChoice(
            AppPreferences.AUDIO_ATMOS,
            "Dolby Atmos",
            "Chỉ hoạt động nếu nguồn và thiết bị hỗ trợ"
        )
    )

    val motionChoices = listOf(
        SettingChoice(
            AppPreferences.MOTION_SYSTEM,
            "Theo hệ thống",
            "Không ép tần số quét của thiết bị"
        ),
        SettingChoice(
            AppPreferences.MOTION_HIGH,
            "Ưu tiên chuyển động mượt",
            "Cho phép ứng dụng yêu cầu tần số quét cao khi khả dụng"
        )
    )

    fun saveChoice(key: String, value: String) {
        AppPreferences.setString(context, key, value)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF080808))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "CÀI ĐẶT VIETSTREAM MAX",
            color = Color(0xFFE50914),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            "Giao diện",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        SettingRow(
            title = "Chế độ sáng / tối",
            subtitle = themes.first { it.value == theme }.title
        ) {
            dialogKey = "theme"
        }

        SettingRow(
            title = "Màu chủ đề",
            subtitle = accents.firstOrNull { it.first == accent }?.second
                ?: "Màu tùy chỉnh"
        ) {
            dialogKey = "accent"
        }

        HorizontalDivider(color = Color.DarkGray)

        Text(
            "Ngôn ngữ",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        SettingRow(
            title = "Ngôn ngữ giao diện",
            subtitle = languages.first { it.value == language }.title
        ) {
            dialogKey = "language"
        }

        Text(
            "Lưu ý: danh sách ngôn ngữ đã được khai báo ở đây. " +
                "Để toàn bộ giao diện thực sự dịch theo lựa chọn, " +
                "các nhãn trong ứng dụng cần được chuyển sang string resources.",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp
        )

        HorizontalDivider(color = Color.DarkGray)

        Text(
            "Phát video",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        SettingRow(
            title = "Trình phát mặc định",
            subtitle = players.first { it.value == player }.title
        ) {
            dialogKey = "player"
        }

        SettingRow(
            title = "Ưu tiên âm thanh",
            subtitle = audioChoices.first { it.value == audio }.title
        ) {
            dialogKey = "audio"
        }

        Text(
            "Stereo và Dolby Atmos phụ thuộc vào track âm thanh của video, " +
                "bộ giải mã, thiết bị và đường xuất âm. Tùy chọn này chưa tự biến " +
                "nguồn stereo thành Dolby Atmos.",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp
        )

        HorizontalDivider(color = Color.DarkGray)

        Text(
            "Hiệu năng",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        SettingRow(
            title = "Chuyển động giao diện",
            subtitle = motionChoices.first { it.value == motion }.title
        ) {
            dialogKey = "motion"
        }
    }

    val choices = when (dialogKey) {
        "theme" -> themes
        "language" -> languages
        "player" -> players
        "audio" -> audioChoices
        "motion" -> motionChoices
        else -> emptyList()
    }

    if (dialogKey == "accent") {
        AlertDialog(
            onDismissRequest = { dialogKey = null },
            title = { Text("Chọn màu chủ đề") },
            text = {
                Column {
                    accents.forEach { (color, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    accent = color
                                    AppPreferences.setAccent(context, color)
                                    dialogKey = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = accent == color,
                                onClick = {
                                    accent = color
                                    AppPreferences.setAccent(context, color)
                                    dialogKey = null
                                }
                            )
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(name, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .background(
                                        Color(color.toULong().toLong()),
                                        RoundedCornerShape(50)
                                    )
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogKey = null }) {
                    Text("Đóng")
                }
            }
        )
    } else if (dialogKey != null) {
        AlertDialog(
            onDismissRequest = { dialogKey = null },
            title = {
                Text(
                    when (dialogKey) {
                        "theme" -> "Chế độ giao diện"
                        "language" -> "Ngôn ngữ"
                        "player" -> "Trình phát video"
                        "audio" -> "Ưu tiên âm thanh"
                        "motion" -> "Chuyển động giao diện"
                        else -> "Cài đặt"
                    }
                )
            },
            text = {
                Column {
                    choices.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (dialogKey) {
                                        "theme" -> {
                                            theme = choice.value
                                            saveChoice(AppPreferences.KEY_THEME, theme)
                                        }
                                        "language" -> {
                                            language = choice.value
                                            saveChoice(AppPreferences.KEY_LANGUAGE, language)
                                        }
                                        "player" -> {
                                            player = choice.value
                                            saveChoice(AppPreferences.KEY_PLAYER, player)
                                        }
                                        "audio" -> {
                                            audio = choice.value
                                            saveChoice(AppPreferences.KEY_AUDIO, audio)
                                        }
                                        "motion" -> {
                                            motion = choice.value
                                            saveChoice(AppPreferences.KEY_REFRESH_RATE, motion)
                                        }
                                    }
                                    dialogKey = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = when (dialogKey) {
                                    "theme" -> theme == choice.value
                                    "language" -> language == choice.value
                                    "player" -> player == choice.value
                                    "audio" -> audio == choice.value
                                    "motion" -> motion == choice.value
                                    else -> false
                                },
                                onClick = {
                                    when (dialogKey) {
                                        "theme" -> {
                                            theme = choice.value
                                            saveChoice(AppPreferences.KEY_THEME, theme)
                                        }
                                        "language" -> {
                                            language = choice.value
                                            saveChoice(AppPreferences.KEY_LANGUAGE, language)
                                        }
                                        "player" -> {
                                            player = choice.value
                                            saveChoice(AppPreferences.KEY_PLAYER, player)
                                        }
                                        "audio" -> {
                                            audio = choice.value
                                            saveChoice(AppPreferences.KEY_AUDIO, audio)
                                        }
                                        "motion" -> {
                                            motion = choice.value
                                            saveChoice(AppPreferences.KEY_REFRESH_RATE, motion)
                                        }
                                    }
                                    dialogKey = null
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    choice.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    choice.description,
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogKey = null }) {
                    Text("Đóng")
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF191919),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp
            )
        }
        Text(
            "›",
            color = Color(0xFFE50914),
            fontSize = 24.sp
        )
    }
}
