package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ChatWallpaper
import com.example.data.model.MessageStatus
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.ReadTickBlue
import com.example.util.MediaAndTimeUtils
import kotlin.math.abs

private val AvatarPalette = listOf(
    Color(0xFF0D6E5C),
    Color(0xFF1E6B7B),
    Color(0xFF8C5A2B),
    Color(0xFF4C6B42),
    Color(0xFF6B4E71),
    Color(0xFF28587B)
)

@Composable
fun UserAvatar(
    name: String,
    avatarBase64: String?,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    isGroup: Boolean = false,
    showOnlineDot: Boolean = false
) {
    val bitmap = remember(avatarBase64) {
        MediaAndTimeUtils.decodeBase64ToImageBitmap(avatarBase64)
    }
    val bgColor = remember(name) {
        AvatarPalette[abs(name.hashCode()) % AvatarPalette.size]
    }
    val initial = remember(name) {
        name.trim().firstOrNull()?.toString() ?: "ر"
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "صورة $name",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isGroup) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "أيقونة مجموعة $name",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.54f)
                )
            } else {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size((size * 0.28f).coerceAtLeast(11.dp))
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun MessageStatusTicks(
    status: String,
    tintDefault: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 16.dp
) {
    val parsed = MessageStatus.fromWire(status)
    when (parsed) {
        MessageStatus.SENT -> Icon(
            imageVector = Icons.Default.Done,
            contentDescription = "تم الإرسال",
            tint = tintDefault,
            modifier = Modifier.size(size)
        )
        MessageStatus.DELIVERED -> Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = "تم الاستلام",
            tint = tintDefault,
            modifier = Modifier.size(size)
        )
        MessageStatus.READ -> Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = "تمت القراءة",
            tint = ReadTickBlue,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun WallpaperBackground(
    wallpaper: ChatWallpaper,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = remember(wallpaper, isDark) {
        when (wallpaper) {
            ChatWallpaper.EMERALD_CLASSIC -> if (isDark) {
                listOf(Color(0xFF091412), Color(0xFF0E211D))
            } else {
                listOf(Color(0xFFEAF4F0), Color(0xFFF4FAF7))
            }
            ChatWallpaper.WARM_SAND -> if (isDark) {
                listOf(Color(0xFF1A1611), Color(0xFF241D15))
            } else {
                listOf(Color(0xFFF6EFE4), Color(0xFFFAF6EE))
            }
            ChatWallpaper.MIDNIGHT_TEAL -> if (isDark) {
                listOf(Color(0xFF08121B), Color(0xFF0E2233))
            } else {
                listOf(Color(0xFFE4EFF6), Color(0xFFF0F6FA))
            }
            ChatWallpaper.DESERT_ROSE -> if (isDark) {
                listOf(Color(0xFF1B1216), Color(0xFF271820))
            } else {
                listOf(Color(0xFFF8ECF0), Color(0xFFFBF3F6))
            }
            ChatWallpaper.MINIMAL_SLATE -> if (isDark) {
                listOf(Color(0xFF101316), Color(0xFF181C21))
            } else {
                listOf(Color(0xFFEDF0F3), Color(0xFFF6F8FA))
            }
            ChatWallpaper.BOTANICAL_MINT -> if (isDark) {
                listOf(Color(0xFF0B1914), Color(0xFF112820))
            } else {
                listOf(Color(0xFFE3F5EC), Color(0xFFF0FAF5))
            }
        }
    }

    val patternColor = if (isDark) Color.White.copy(alpha = 0.035f) else Color(0xFF0D6E5C).copy(alpha = 0.045f)

    Box(modifier = modifier.background(Brush.verticalGradient(colors))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 64.dp.toPx()
            val radius = 14.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height + step) {
                var x = if (row % 2 == 0) 0f else step / 2f
                while (x < size.width + step) {
                    drawCircle(
                        color = patternColor,
                        radius = radius,
                        center = Offset(x, y),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    x += step
                }
                y += step
                row++
            }
        }
        content()
    }
}

@Composable
fun EmptyChatsPlaceholder(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_chats),
            contentDescription = title,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(28.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
