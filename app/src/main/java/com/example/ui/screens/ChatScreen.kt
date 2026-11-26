package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ChatWallpaper
import com.example.data.model.DirectoryProfile
import com.example.data.model.MessageType
import com.example.data.model.ThemeMode
import com.example.data.model.TypingStatus
import com.example.data.model.UiState
import com.example.ui.components.MessageStatusTicks
import com.example.ui.components.UserAvatar
import com.example.ui.components.WallpaperBackground
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.ReceivedBubbleDark
import com.example.ui.theme.ReceivedBubbleLight
import com.example.ui.theme.SentBubbleDark
import com.example.ui.theme.SentBubbleLight
import com.example.util.MediaAndTimeUtils
import com.example.util.VoiceNotePlayer
import com.example.util.VoiceNoteRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: ChatThread,
    currentUserId: String,
    currentUserProfile: DirectoryProfile?,
    allProfiles: List<DirectoryProfile>,
    messagesState: UiState<List<ChatMessage>>,
    typingStatuses: List<TypingStatus>,
    wallpaper: ChatWallpaper,
    themeMode: ThemeMode,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onSendText: (String, ChatMessage?) -> Unit,
    onSendImage: (String, String, ChatMessage?) -> Unit,
    onSendAudio: (String, Long, ChatMessage?) -> Unit,
    onSendFile: (String, String, Long, String?, ChatMessage?) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onMarkRead: (List<ChatMessage>) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onUpdateGroup: (String, List<String>, List<String>) -> Unit,
    onLeaveGroup: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileMap = remember(allProfiles) { allProfiles.associateBy { it.userId } }
    val isGroup = chat.type == "group"
    val peerProfile = if (!isGroup) {
        chat.memberUids.firstOrNull { it != currentUserId }?.let { profileMap[it] }
    } else null

    val chatTitle = resolveChatTitle(chat, currentUserId, profileMap)
    val othersTyping = remember(typingStatuses, currentUserId) {
        typingStatuses.filter { it.userId != currentUserId && it.typing }
    }
    val subtitleText = when {
        othersTyping.isNotEmpty() -> {
            if (isGroup) "${othersTyping.first().displayName} يكتب الآن..."
            else "يكتب الآن..."
        }
        isGroup -> "${chat.memberUids.size} أعضاء"
        peerProfile == null -> "جهة اتصال"
        peerProfile.hideLastSeen -> "متاح"
        peerProfile.online -> "متصل الآن"
        else -> MediaAndTimeUtils.formatLastSeenArabic(peerProfile.lastSeen)
    }

    var inputText by remember(chat.id) { mutableStateOf("") }
    var replyTarget by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var fullscreenImageBase64 by remember { mutableStateOf<String?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }

    // Voice Recording & Playback
    val voiceRecorder = remember { VoiceNoteRecorder(context) }
    val voicePlayer = remember { VoiceNotePlayer(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }
    var playingMessageId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(chat.id) {
        onDispose {
            voiceRecorder.cancelRecording()
            voicePlayer.stop()
            onTypingChanged(false)
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0L
            while (isRecording) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (voiceRecorder.startRecording()) {
                isRecording = true
            } else {
                onNotify("تعذر بدء التسجيل الصوتي")
            }
        } else {
            onNotify("يرجى منح صلاحية الميكروفون لتسجيل الرسائل الصوتية")
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = MediaAndTimeUtils.compressImageUriToBase64(context, uri)
                if (base64 != null) {
                    onSendImage(base64, "", replyTarget)
                    replyTarget = null
                } else {
                    onNotify("تعذر معالجة الصورة")
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val file = MediaAndTimeUtils.readPickedFile(context, uri)
                if (file != null) {
                    onSendFile(file.name, file.mimeType, file.sizeBytes, file.base64Data, replyTarget)
                    replyTarget = null
                } else {
                    onNotify("تعذر قراءة الملف المحدد")
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(messages.size, chat.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
            onMarkRead(messages)
        }
    }

    LaunchedEffect(othersTyping.size) {
        if (othersTyping.isNotEmpty()) {
            val target = messages.size
            if (target >= 0) {
                listState.animateScrollToItem(target)
            }
        }
    }

    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                },
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isGroup) { showGroupDialog = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            name = chatTitle,
                            avatarBase64 = if (isGroup) chat.avatarData else peerProfile?.avatarData,
                            size = 42.dp,
                            isGroup = isGroup,
                            showOnlineDot = !isGroup && peerProfile?.online == true && !peerProfile.hideLastSeen
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chatTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (othersTyping.isNotEmpty() || (!isGroup && peerProfile?.online == true && !peerProfile.hideLastSeen)) {
                                    OnlineGreen
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (isGroup) {
                        IconButton(
                            onClick = { showGroupDialog = true },
                            modifier = Modifier.testTag("group_info_button")
                        ) {
                            Icon(Icons.Default.Group, contentDescription = "إدارة المجموعة")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        WallpaperBackground(
            wallpaper = wallpaper,
            isDark = isDark,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (messagesState) {
                        is UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(messagesState.message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is UiState.Success -> {
                            if (messages.isEmpty() && othersTyping.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                                    ) {
                                        Text(
                                            text = "ابدأ المحادثة بإرسال رسالة ترحيبية ✨",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(messages, key = { it.id }) { msg ->
                                        val isMine = msg.senderId == currentUserId
                                        MessageBubble(
                                            message = msg,
                                            isMine = isMine,
                                            isGroup = isGroup,
                                            isDark = isDark,
                                            isPlayingAudio = playingMessageId == msg.id,
                                            onLongPress = { selectedMessageForActions = msg },
                                            onImageClick = { msg.mediaData?.let { fullscreenImageBase64 = it } },
                                            onAudioToggle = {
                                                val b64 = msg.mediaData
                                                if (!b64.isNullOrBlank()) {
                                                    if (playingMessageId == msg.id) {
                                                        voicePlayer.stop()
                                                        playingMessageId = null
                                                    } else {
                                                        val started = voicePlayer.playBase64(msg.id, b64) {
                                                            playingMessageId = null
                                                        }
                                                        if (started) playingMessageId = msg.id
                                                        else onNotify("تعذر تشغيل الرسالة الصوتية")
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    if (othersTyping.isNotEmpty()) {
                                        item(key = "in_chat_typing_indicator") {
                                            ChatTypingBubble(
                                                typingUsers = othersTyping,
                                                profileMap = profileMap,
                                                isGroup = isGroup,
                                                isDark = isDark,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quoted Reply Banner
                AnimatedVisibility(visible = replyTarget != null) {
                    replyTarget?.let { target ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(34.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.senderName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            target.deleted -> "رسالة محذوفة"
                                            target.type == MessageType.IMAGE.wireValue -> "📷 صورة"
                                            target.type == MessageType.AUDIO.wireValue -> "🎤 رسالة صوتية"
                                            target.type == MessageType.FILE.wireValue -> "📎 ${target.mediaName ?: "ملف"}"
                                            else -> target.text
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { replyTarget = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "إلغاء الرد")
                                }
                            }
                        }
                    }
                }

                // Composer Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    if (isRecording) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "جاري التسجيل... ${MediaAndTimeUtils.formatDuration(recordingSeconds)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = {
                                    voiceRecorder.cancelRecording()
                                    isRecording = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "إلغاء التسجيل")
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    IconButton(onClick = {
                                        isRecording = false
                                        val result = voiceRecorder.stopAndEncode()
                                        if (result != null) {
                                            onSendAudio(result.first, result.second, replyTarget)
                                            replyTarget = null
                                        } else {
                                            onNotify("التسجيل قصير جداً أو يتجاوز الحد المسموح")
                                        }
                                    }) {
                                        Icon(Icons.Default.Stop, contentDescription = "إرسال التسجيل", tint = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box {
                                IconButton(
                                    onClick = { showAttachMenu = true },
                                    modifier = Modifier.testTag("attach_button")
                                ) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = "إرفاق صورة أو ملف",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAttachMenu,
                                    onDismissRequest = { showAttachMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("صورة من المعرض") },
                                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                        onClick = {
                                            showAttachMenu = false
                                            imagePickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("ملف أو مستند") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                                        onClick = {
                                            showAttachMenu = false
                                            filePickerLauncher.launch(arrayOf("*/*"))
                                        }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { newVal ->
                                    inputText = newVal
                                    onTypingChanged(newVal.isNotBlank())
                                },
                                placeholder = {
                                    Text("اكتب رسالة...", style = MaterialTheme.typography.bodyMedium)
                                },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("message_input")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (inputText.trim().isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val textToSend = inputText
                                            inputText = ""
                                            onSendText(textToSend, replyTarget)
                                            replyTarget = null
                                        },
                                        modifier = Modifier.testTag("send_message_button")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "إرسال",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                if (voiceRecorder.startRecording()) isRecording = true
                                                else onNotify("تعذر بدء التسجيل الصوتي")
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier.testTag("record_voice_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = "تسجيل رسالة صوتية",
                                            tint = MaterialTheme.colorScheme.onPrimary
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

    selectedMessageForActions?.let { msg ->
        MessageActionsSheet(
            message = msg,
            isOwnMessage = msg.senderId == currentUserId,
            onReply = { replyTarget = msg },
            onCopy = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Resalah", msg.text))
                onNotify("تم نسخ النص")
            },
            onDelete = { onDeleteMessage(msg.id) },
            onDismiss = { selectedMessageForActions = null }
        )
    }

    fullscreenImageBase64?.let { b64 ->
        FullscreenImageDialog(base64 = b64, onDismiss = { fullscreenImageBase64 = null })
    }

    if (showGroupDialog && isGroup) {
        GroupInfoDialog(
            chat = chat,
            currentUserId = currentUserId,
            allProfiles = allProfiles,
            onUpdateGroup = onUpdateGroup,
            onLeaveGroup = {
                showGroupDialog = false
                onLeaveGroup()
            },
            onDismiss = { showGroupDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isGroup: Boolean,
    isDark: Boolean,
    isPlayingAudio: Boolean,
    onLongPress: () -> Unit,
    onImageClick: () -> Unit,
    onAudioToggle: () -> Unit
) {
    val bubbleColor = when {
        isMine && isDark -> SentBubbleDark
        isMine -> SentBubbleLight
        isDark -> ReceivedBubbleDark
        else -> ReceivedBubbleLight
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = {
                        if (message.type == MessageType.IMAGE.wireValue && !message.deleted) onImageClick()
                        else if (message.type == MessageType.AUDIO.wireValue && !message.deleted) onAudioToggle()
                    },
                    onLongClick = onLongPress
                )
                .testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isGroup && !isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (!message.replyToPreview.isNullOrBlank() && !message.deleted) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.replyToSenderName ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = message.replyToPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (message.deleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تم حذف هذه الرسالة",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    when (MessageType.fromWire(message.type)) {
                        MessageType.IMAGE -> {
                            val bmp = remember(message.mediaData) {
                                MediaAndTimeUtils.decodeBase64ToImageBitmap(message.mediaData)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "صورة مرسلة",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (message.text.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        MessageType.AUDIO -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    IconButton(onClick = onAudioToggle) {
                                        Icon(
                                            imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlayingAudio) "إيقاف" else "تشغيل",
                                            tint = Color.White
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = MediaAndTimeUtils.formatDuration(message.audioDurationSec ?: 1L),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        MessageType.FILE -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = message.mediaName ?: "ملف مرفق",
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = MediaAndTimeUtils.formatFileSize(message.mediaSizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        MessageType.TEXT -> {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = MediaAndTimeUtils.formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMine && !message.deleted) {
                        MessageStatusTicks(status = message.status, size = 15.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun BouncingDotsIndicator(
    color: Color = MaterialTheme.colorScheme.primary,
    dotSize: androidx.compose.ui.unit.Dp = 7.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_dots")

    val dotOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0f at 0
                -5f at 200 using FastOutSlowInEasing
                0f at 400
                0f at 900
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(0)
        ),
        label = "dot_1"
    )

    val dotOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0f at 0
                -5f at 200 using FastOutSlowInEasing
                0f at 400
                0f at 900
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(150)
        ),
        label = "dot_2"
    )

    val dotOffset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0f at 0
                -5f at 200 using FastOutSlowInEasing
                0f at 400
                0f at 900
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(300)
        ),
        label = "dot_3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(y = dotOffset1.dp)
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .offset(y = dotOffset2.dp)
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .offset(y = dotOffset3.dp)
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun ChatTypingBubble(
    typingUsers: List<TypingStatus>,
    profileMap: Map<String, DirectoryProfile>,
    isGroup: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isDark) ReceivedBubbleDark else ReceivedBubbleLight
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 4.dp,
        bottomEnd = 18.dp
    )
    val names = typingUsers.joinToString(separator = "، ") { it.displayName }
    val firstUser = typingUsers.firstOrNull()
    val avatar = firstUser?.let { profileMap[it.userId]?.avatarData }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("in_chat_typing_indicator"),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (firstUser != null) {
                    UserAvatar(
                        name = firstUser.displayName,
                        avatarBase64 = avatar,
                        size = 28.dp
                    )
                }
                Column {
                    if (isGroup && firstUser != null) {
                        Text(
                            text = names,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BouncingDotsIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            dotSize = 7.dp
                        )
                        Text(
                            text = if (typingUsers.size > 1) "يكتبون الآن..." else "يكتب الآن...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

