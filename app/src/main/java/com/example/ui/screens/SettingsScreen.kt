package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatWallpaper
import com.example.data.model.DirectoryProfile
import com.example.data.model.PrivateUser
import com.example.data.model.ThemeMode
import com.example.data.model.YemenCarrier
import com.example.ui.components.UserAvatar
import com.example.ui.components.WallpaperBackground
import com.example.util.MediaAndTimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    directoryProfile: DirectoryProfile?,
    privateUser: PrivateUser?,
    currentThemeMode: ThemeMode,
    currentWallpaper: ChatWallpaper,
    onSaveProfile: (
        displayName: String,
        username: String,
        bio: String,
        avatarData: String?,
        phone: String?,
        hideLastSeen: Boolean,
        hideReadReceipts: Boolean,
        themeMode: ThemeMode,
        wallpaper: ChatWallpaper
    ) -> Unit,
    onThemeChanged: (ThemeMode) -> Unit,
    onWallpaperChanged: (ChatWallpaper) -> Unit,
    onPrivacyChanged: (hideLastSeen: Boolean, hideReadReceipts: Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(directoryProfile?.displayName ?: "") }
    var username by remember { mutableStateOf(directoryProfile?.username ?: "") }
    var bio by remember { mutableStateOf(directoryProfile?.bio ?: "") }
    var phone by remember { mutableStateOf(privateUser?.phone ?: "") }
    var avatarData by remember { mutableStateOf(directoryProfile?.avatarData) }
    var hideLastSeen by remember { mutableStateOf(directoryProfile?.hideLastSeen ?: false) }
    var hideReadReceipts by remember { mutableStateOf(directoryProfile?.hideReadReceipts ?: false) }

    LaunchedEffect(directoryProfile, privateUser) {
        directoryProfile?.let {
            if (displayName.isBlank()) displayName = it.displayName
            if (username.isBlank()) username = it.username
            if (bio.isBlank()) bio = it.bio ?: ""
            if (avatarData == null) avatarData = it.avatarData
            hideLastSeen = it.hideLastSeen
            hideReadReceipts = it.hideReadReceipts
        }
        privateUser?.let {
            if (phone.isBlank()) phone = it.phone ?: ""
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = MediaAndTimeUtils.compressImageUriToBase64(context, uri, maxDimension = 384, maxBytes = 150_000)
                if (encoded != null) avatarData = encoded
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات والملف الشخصي",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("الملف الشخصي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .testTag("pick_avatar_button")
                            ) {
                                UserAvatar(
                                    name = displayName.ifBlank { "م" },
                                    avatarBase64 = avatarData,
                                    size = 72.dp
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "تغيير الصورة",
                                        tint = Color.White,
                                        modifier = Modifier.padding(5.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = privateUser?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "اضغط على الصورة لتحديث صورتك الشخصية",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("الاسم الظاهر") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input")
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("اسم المستخدم (@username)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_username_input")
                        )
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text("الحالة / النبذة") },
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_bio_input")
                        )
                        val currentCarrier = YemenCarrier.detect(phone)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("رقم الهاتف اليمني (+967)") },
                                placeholder = { Text("77xxxxxxx / 73xxxxxxx / 71xxxxxxx") },
                                singleLine = true,
                                trailingIcon = {
                                    if (currentCarrier != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(currentCarrier.colorHex).copy(alpha = 0.15f),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = currentCarrier.brandNameArabic,
                                                color = Color(currentCarrier.colorHex),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_phone_input")
                            )
                            Text(
                                text = "الشبكات المعتمدة في اليمن: 77 (يمن موبايل)، 73 (يو)، 71 (سبأفون) 🇾🇪",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (currentCarrier != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                onSaveProfile(
                                    displayName,
                                    username,
                                    bio,
                                    avatarData,
                                    phone,
                                    hideLastSeen,
                                    hideReadReceipts,
                                    currentThemeMode,
                                    currentWallpaper
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_profile_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حفظ الملف الشخصي")
                        }
                    }
                }
            }

            // Privacy Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("الخصوصية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("إخفاء آخر ظهور وحالة الاتصال", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "لن يتمكن الآخرون من رؤية وقت نشاطك",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = hideLastSeen,
                                onCheckedChange = {
                                    hideLastSeen = it
                                    onPrivacyChanged(it, hideReadReceipts)
                                },
                                modifier = Modifier.testTag("privacy_last_seen_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("إخفاء علامات القراءة الزرقاء", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "لن يظهر للآخرين أنك قرأت رسائلهم",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = hideReadReceipts,
                                onCheckedChange = {
                                    hideReadReceipts = it
                                    onPrivacyChanged(hideLastSeen, it)
                                },
                                modifier = Modifier.testTag("privacy_read_receipts_switch")
                            )
                        }
                    }
                }
            }

            // Appearance & Wallpaper Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("المظهر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ThemeMode.entries) { mode ->
                                FilterChip(
                                    selected = currentThemeMode == mode,
                                    onClick = { onThemeChanged(mode) },
                                    label = { Text(mode.arabicLabel) },
                                    modifier = Modifier.testTag("theme_chip_${mode.wireValue}")
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("خلفية الدردشة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(ChatWallpaper.entries) { wp ->
                                val selected = wp == currentWallpaper
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { onWallpaperChanged(wp) }
                                        .testTag("wallpaper_${wp.wireValue}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 78.dp, height = 104.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                    ) {
                                        WallpaperBackground(
                                            wallpaper = wp,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (selected) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.padding(5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = wp.arabicLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sign Out
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسجيل الخروج", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
