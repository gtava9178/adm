package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatThread
import com.example.data.model.DirectoryProfile
import com.example.data.model.UiState
import com.example.data.model.YemenCarrier
import com.example.ui.components.EmptyChatsPlaceholder
import com.example.ui.components.MessageStatusTicks
import com.example.ui.components.UserAvatar
import com.example.ui.theme.UnreadBadgeEmerald
import com.example.util.MediaAndTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    currentUserId: String,
    chatsState: UiState<List<ChatThread>>,
    directoryState: UiState<List<DirectoryProfile>>,
    searchQuery: String,
    selectedChatId: String?,
    onSearchChange: (String) -> Unit,
    onChatSelected: (String) -> Unit,
    onStartDirectChat: (String) -> Unit,
    onCreateGroup: (String, List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewActionSheet by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val allProfiles = (directoryState as? UiState.Success)?.data ?: emptyList()
    val profileMap = remember(allProfiles) { allProfiles.associateBy { it.userId } }
    val otherContacts = remember(allProfiles, currentUserId) {
        allProfiles.filter { it.userId != currentUserId }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "رسالة",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.testTag("top_create_group_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "إنشاء مجموعة جديدة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewActionSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "محادثة جديدة"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            text = "ابحث بالاسم أو رقم الهاتف اليمني (77 / 73 / 71)...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("chats_search_input")
                )
            }

            when (chatsState) {
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
                        Text(
                            text = chatsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is UiState.Success -> {
                    val query = searchQuery.trim().lowercase()
                    val digitsQuery = query.filter { it.isDigit() }
                    val filteredChats = remember(chatsState.data, query, profileMap) {
                        if (query.isEmpty()) chatsState.data
                        else chatsState.data.filter { chat ->
                            val displayName = resolveChatTitle(chat, currentUserId, profileMap)
                            displayName.lowercase().contains(query) ||
                                (chat.lastMessageText?.lowercase()?.contains(query) == true)
                        }
                    }

                    val matchingContacts = remember(otherContacts, query, digitsQuery) {
                        if (query.isEmpty()) emptyList()
                        else otherContacts.filter {
                            it.displayName.lowercase().contains(query) ||
                                it.username.lowercase().contains(query) ||
                                (digitsQuery.isNotEmpty() && it.phone?.filter { ch -> ch.isDigit() }?.contains(digitsQuery) == true) ||
                                (it.phone != null && it.phone.lowercase().contains(query))
                        }
                    }

                    if (filteredChats.isEmpty() && matchingContacts.isEmpty()) {
                        EmptyChatsPlaceholder(
                            title = if (query.isEmpty()) "لا توجد محادثات بعد" else "لا توجد نتائج مطابقة",
                            subtitle = if (query.isEmpty()) {
                                "اضغط على زر (+) لبدء محادثة فورية مع جهة اتصال أو إنشاء مجموعة جديدة."
                            } else {
                                "جرّب البحث باسم آخر أو اسم المستخدم."
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            if (filteredChats.isNotEmpty()) {
                                items(filteredChats, key = { it.id }) { chat ->
                                    ChatRowItem(
                                        chat = chat,
                                        currentUserId = currentUserId,
                                        profileMap = profileMap,
                                        isSelected = chat.id == selectedChatId,
                                        onClick = { onChatSelected(chat.id) }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 84.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                }
                            }

                            if (matchingContacts.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "جهات اتصال على رسالة",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                    )
                                }
                                items(matchingContacts, key = { "contact_${it.userId}" }) { contact ->
                                    ContactDirectoryRow(
                                        profile = contact,
                                        onClick = { onStartDirectChat(contact.userId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewActionSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "بدء محادثة جديدة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            showNewActionSheet = false
                            showCreateGroupDialog = true
                        },
                        modifier = Modifier.testTag("sheet_new_group_btn")
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مجموعة جديدة")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (otherContacts.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PersonSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "لا يوجد مستخدمون آخرون مسجلون حتى الآن",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(340.dp)) {
                        items(otherContacts, key = { it.userId }) { contact ->
                            ContactDirectoryRow(
                                profile = contact,
                                onClick = {
                                    showNewActionSheet = false
                                    onStartDirectChat(contact.userId)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = otherContacts,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { title, selectedUids ->
                showCreateGroupDialog = false
                onCreateGroup(title, selectedUids)
            }
        )
    }
}

@Composable
private fun ChatRowItem(
    chat: ChatThread,
    currentUserId: String,
    profileMap: Map<String, DirectoryProfile>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isGroup = chat.type == "group"
    val peerProfile = if (!isGroup) {
        val peerUid = chat.memberUids.firstOrNull { it != currentUserId }
        peerUid?.let { profileMap[it] }
    } else null

    val title = resolveChatTitle(chat, currentUserId, profileMap)
    val avatar = if (isGroup) chat.avatarData else peerProfile?.avatarData
    val showOnline = !isGroup && peerProfile?.online == true && !peerProfile.hideLastSeen
    val unread = (chat.unreadCounts[currentUserId] ?: 0L).toInt()
    val timeText = MediaAndTimeUtils.formatMessageTime(chat.lastMessageAt ?: chat.updatedAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_item_${chat.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            name = title,
            avatarBase64 = avatar,
            size = 54.dp,
            isGroup = isGroup,
            showOnlineDot = showOnline
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chat.lastMessageSenderId == currentUserId &&
                    chat.lastMessageStatus != null &&
                    chat.lastMessageStatus != "none"
                ) {
                    MessageStatusTicks(status = chat.lastMessageStatus, size = 16.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = chat.lastMessageText?.ifBlank { "ابدأ المحادثة الآن..." } ?: "ابدأ المحادثة الآن...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(UnreadBadgeEmerald)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unread > 99) "+99" else unread.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactDirectoryRow(
    profile: DirectoryProfile,
    onClick: () -> Unit
) {
    val carrier = profile.phone?.let { YemenCarrier.detect(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("contact_item_${profile.userId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            name = profile.displayName,
            avatarBase64 = profile.avatarData,
            size = 46.dp,
            showOnlineDot = profile.online && !profile.hideLastSeen
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (carrier != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(carrier.colorHex).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = carrier.brandNameArabic,
                            color = Color(carrier.colorHex),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            val phoneSubtitle = if (!profile.phone.isNullOrBlank()) {
                val formatted = YemenCarrier.formatYemen(profile.phone)
                "🇾🇪 $formatted • @${profile.username}"
            } else {
                "@${profile.username} • ${profile.bio ?: "متاح"}"
            }
            Text(
                text = phoneSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = "بدء محادثة",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CreateGroupDialog(
    contacts: List<DirectoryProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء مجموعة جديدة", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("اسم المجموعة") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_name_input")
                )
                Text(
                    text = "اختر الأعضاء (${selectedUids.size}):",
                    style = MaterialTheme.typography.labelLarge
                )
                if (contacts.isEmpty()) {
                    Text(
                        text = "يمكنك إنشاء المجموعة الآن وإضافة أعضاء لاحقاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(210.dp)) {
                        items(contacts, key = { it.userId }) { user ->
                            val isChecked = selectedUids.contains(user.userId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedUids.remove(user.userId)
                                        else selectedUids.add(user.userId)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked && !isChecked) selectedUids.add(user.userId)
                                        else if (!checked) selectedUids.remove(user.userId)
                                    }
                                )
                                UserAvatar(
                                    name = user.displayName,
                                    avatarBase64 = user.avatarData,
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(groupName, selectedUids.toList()) },
                enabled = groupName.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_create_group_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

fun resolveChatTitle(
    chat: ChatThread,
    currentUserId: String,
    profileMap: Map<String, DirectoryProfile>
): String {
    if (chat.type == "group") return chat.title.ifBlank { "مجموعة" }
    val peerUid = chat.memberUids.firstOrNull { it != currentUserId }
    return peerUid?.let { profileMap[it]?.displayName }?.ifBlank { null } ?: "محادثة خاصة"
}
