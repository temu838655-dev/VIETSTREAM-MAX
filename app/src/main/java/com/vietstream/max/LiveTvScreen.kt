
package com.vietstream.max

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TvBlack = Color(0xFF090909)
private val TvPanel = Color(0xFF1B1B1B)
private val TvRed = Color(0xFFE50914)
private val TvWhite = Color(0xFFF5F5F5)
private val TvGray = Color(0xFFAAAAAA)

data class TvChannel(
    val name: String,
    val country: String,
    val category: String,
    val description: String,
    val url: String
)

private val officialChannels = listOf(
    TvChannel(
        "VTV1",
        "Việt Nam",
        "Tin tức",
        "Kênh thời sự và chính luận của VTV",
        "https://vtv.vn/truyen-hinh-truc-tuyen/vtv1.htm"
    ),
    TvChannel(
        "VTVgo - VTV1 đến VTV9",
        "Việt Nam",
        "Tổng hợp",
        "Nền tảng truyền hình trực tuyến chính thức của VTV",
        "https://vtvgo.vn/"
    ),
    TvChannel(
        "VTV4",
        "Việt Nam",
        "Quốc tế",
        "Kênh truyền hình đối ngoại Việt Nam",
        "https://vtv4.vtv.vn/xem-vtv4-moi-luc-moi-noi.html"
    ),
    TvChannel(
        "Vietnam Today",
        "Việt Nam",
        "Tin tức",
        "Kênh đối ngoại quốc gia bằng tiếng Anh",
        "https://vietnamtoday.vtv.vn/"
    ),
    TvChannel(
        "Al Jazeera English",
        "Quốc tế",
        "Tin tức",
        "Tin tức quốc tế trực tiếp",
        "https://www.aljazeera.com/video/live"
    ),
    TvChannel(
        "TRT World",
        "Quốc tế",
        "Tin tức",
        "Tin tức thế giới từ Thổ Nhĩ Kỳ",
        "https://www.trtworld.com/live"
    ),
    TvChannel(
        "France 24",
        "Quốc tế",
        "Tin tức",
        "Kênh tin tức quốc tế của Pháp",
        "https://www.france24.com/en/live"
    ),
    TvChannel(
        "DW News",
        "Quốc tế",
        "Tin tức",
        "Kênh tin tức quốc tế của Đức",
        "https://www.dw.com/en/live-tv/s-100825"
    ),
    TvChannel(
        "NHK World-Japan",
        "Quốc tế",
        "Tin tức",
        "Truyền hình quốc tế Nhật Bản",
        "https://www3.nhk.or.jp/nhkworld/en/live/"
    )
)

@Composable
fun LiveTvScreen() {
    val context = LocalContext.current

    var selectedRegion by remember { mutableStateOf("Tất cả") }
    var search by remember { mutableStateOf("") }

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
            .background(TvBlack)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "LIVE TV",
            color = TvRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Truyền hình thế giới",
            color = TvWhite,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Các kênh và nguồn xem chính thức",
            color = TvGray,
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
                focusedTextColor = TvWhite,
                unfocusedTextColor = TvWhite,
                focusedBorderColor = TvRed,
                unfocusedBorderColor = TvGray,
                cursorColor = TvRed
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
                        selectedContainerColor = TvRed,
                        selectedLabelColor = Color.White,
                        labelColor = TvWhite
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
                color = TvWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Text(
                "${filteredChannels.size} kênh",
                color = TvGray,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        if (filteredChannels.isEmpty()) {
            Text(
                "Không tìm thấy kênh phù hợp.",
                color = TvGray,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filteredChannels) { channel ->
                    TvChannelCard(channel = channel) {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(channel.url)
                            )
                            context.startActivity(intent)
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
            .background(
                TvPanel,
                RoundedCornerShape(12.dp)
            )
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
                color = TvRed,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                channel.name,
                color = TvWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                "${channel.country} • ${channel.category}",
                color = TvRed,
                fontSize = 11.sp
            )

            Text(
                channel.description,
                color = TvGray,
                fontSize = 11.sp,
                maxLines = 2
            )
        }

        Text(
            "▶",
            color = TvRed,
            fontSize = 20.sp
        )
    }
}
