package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DirectoryProfile
import com.example.data.model.StoryItem
import com.example.data.model.UiState
import com.example.ui.components.EmptyChatsPlaceholder
import com.example.ui.components.UserAvatar
import com.example.util.MediaAndTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val StoryColors = listOf(
    0xFF0D6E5CL,
    0xFF1E40AFL,
    0xFF9A3412L,
    0xFF6B21A8L,
    0xFF0F766EL,
    0xFF334155L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoriesScreen(
    currentUserId: String,
    currentUserProfile: DirectoryProfile?,
    storiesState: UiState<List<StoryItem>>,
    onCreateStory: (type: String, text: String, mediaData: String?, colorHex: Long) -> Unit,
    onMarkViewed: (StoryItem) -> Unit,
    onDeleteStory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showTextStoryDialog by remember { mutableStateOf(false) }
    var activeAuthorStories by remember { mutableStateOf<List<StoryItem>?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = MediaAndTimeUtils.compressImageUriToBase64(context, uri, maxDimension = 900, maxBytes = 250_000)
                if (base64 != null) {
                    onCreateStory("image", "", base64, StoryColors.first())
                }
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
                        text = "الحالات (24 ساعة)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { showTextStoryDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("add_text_story_fab")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "حالة نصية")
                }
                FloatingActionButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("add_image_story_fab")
                ) {
                    Icon(Icons.Default.Image, contentDescription = "حالة مصورة")
                }
            }
        }
    ) { innerPadding ->
        when (storiesState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(storiesState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val allStories = storiesState.data
                val myStories = remember(allStories, currentUserId) {
                    allStories.filter { it.userId == currentUserId }
                }
                val groupedOtherStories = remember(allStories, currentUserId) {
                    allStories.filter { it.userId != currentUserId }.groupBy { it.userId }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (myStories.isNotEmpty()) {
                                        activeAuthorStories = myStories
                                    } else {
                                        showTextStoryDialog = true
                                    }
                                }
                                .testTag("my_story_card"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(contentAlignment = Alignment.BottomStart) {
                                    UserAvatar(
                                        name = currentUserProfile?.displayName ?: "حالتي",
                                        avatarBase64 = currentUserProfile?.avatarData,
                                        size = 56.dp,
                                        modifier = if (myStories.isNotEmpty()) {
                                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp)
                                        } else Modifier
                                    )
                                    if (myStories.isEmpty()) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.padding(3.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "حالتي",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (myStories.isEmpty()) "اضغط لإضافة حالة نصية أو مصورة تختفي خلال 24 ساعة"
                                        else "لديك ${myStories.size} حالة نشطة • اضغط للعرض",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (groupedOtherStories.isEmpty()) {
                        item {
                            Box(modifier = Modifier.height(320.dp)) {
                                EmptyChatsPlaceholder(
                                    title = "لا توجد حالات من جهات الاتصال",
                                    subtitle = "شارك حالتك اليومية أو انتظر تحديثات أصدقائك خلال 24 ساعة."
                                )
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "تحديثات جهات الاتصال",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(groupedOtherStories.entries.toList(), key = { it.key }) { (_, userStories) ->
                            val latest = userStories.first()
                            val hasUnseen = userStories.any { !it.viewerUids.contains(currentUserId) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeAuthorStories = userStories },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        name = latest.authorName,
                                        avatarBase64 = latest.authorAvatar,
                                        size = 52.dp,
                                        modifier = Modifier
                                            .border(
                                                width = 2.5.dp,
                                                color = if (hasUnseen) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                                shape = CircleShape
                                            )
                                            .padding(3.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = latest.authorName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${userStories.size} حالة • ${MediaAndTimeUtils.formatMessageTime(latest.createdAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTextStoryDialog) {
        CreateTextStoryDialog(
            onDismiss = { showTextStoryDialog = false },
            onPublish = { text, colorHex ->
                showTextStoryDialog = false
                onCreateStory("text", text, null, colorHex)
            }
        )
    }

    activeAuthorStories?.let { stories ->
        StoryViewerDialog(
            stories = stories,
            currentUserId = currentUserId,
            onMarkViewed = onMarkViewed,
            onDelete = { storyId ->
                onDeleteStory(storyId)
                activeAuthorStories = null
            },
            onDismiss = { activeAuthorStories = null }
        )
    }
}

@Composable
private fun CreateTextStoryDialog(
    onDismiss: () -> Unit,
    onPublish: (String, Long) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(StoryColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نشر حالة نصية", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(selectedColor))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text.ifBlank { "اكتب ما يدور في بالك..." },
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 300) text = it },
                    label = { Text("نص الحالة") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story_text_input")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StoryColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 0.dp,
                                    color = if (selectedColor == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(text, selectedColor) },
                enabled = text.trim().isNotEmpty(),
                modifier = Modifier.testTag("publish_story_button")
            ) {
                Text("نشر")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun StoryViewerDialog(
    stories: List<StoryItem>,
    currentUserId: String,
    onMarkViewed: (StoryItem) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    val current = stories.getOrNull(index) ?: return

    LaunchedEffect(current.id) {
        onMarkViewed(current)
    }

    var progress by remember(current.id) { mutableStateOf(0f) }
    LaunchedEffect(current.id) {
        val steps = 50
        for (i in 1..steps) {
            delay(100L)
            progress = i / steps.toFloat()
        }
        if (index < stories.lastIndex) index++ else onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (current.type == "text") Color(current.backgroundColorHex)
                    else Color.Black
                )
                .systemBarsPadding()
        ) {
            if (current.type == "image" && !current.mediaData.isNullOrBlank()) {
                val bitmap = remember(current.mediaData) {
                    MediaAndTimeUtils.decodeBase64ToImageBitmap(current.mediaData)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "صورة الحالة",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = current.text,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Tap zones for previous/next
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable {
                            if (index < stories.lastIndex) index++ else onDismiss()
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable {
                            if (index > 0) index--
                        }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    stories.forEachIndexed { idx, _ ->
                        LinearProgressIndicator(
                            progress = {
                                when {
                                    idx < index -> 1f
                                    idx == index -> progress
                                    else -> 0f
                                }
                            },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            name = current.authorName,
                            avatarBase64 = current.authorAvatar,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = current.authorName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = MediaAndTimeUtils.formatMessageTime(current.createdAt),
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current.userId == currentUserId) {
                            IconButton(onClick = { onDelete(current.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف الحالة", tint = Color.White)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }
                }
            }

            if (current.userId == currentUserId) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${current.viewerUids.size} مشاهدة",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
