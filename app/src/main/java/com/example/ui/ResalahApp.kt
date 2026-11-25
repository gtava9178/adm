package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.auth.authStateFlow
import com.example.data.auth.signOut
import com.example.data.model.ChatWallpaper
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.preferences.UserPreferencesStore
import com.example.data.repository.ResalahRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.components.EmptyChatsPlaceholder
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StoriesScreen
import com.example.ui.theme.ResalahTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class MainTab(val label: String) {
    CHATS("المحادثات"),
    STORIES("الحالات"),
    SETTINGS("الإعدادات")
}

@Composable
fun ResalahApp(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val prefsStore = remember(context) { UserPreferencesStore(context.applicationContext) }
    val localThemeMode by prefsStore.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val localWallpaper by prefsStore.wallpaperFlow.collectAsStateWithLifecycle(initialValue = ChatWallpaper.EMERALD_CLASSIC)

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ResalahTheme(themeMode = localThemeMode) {
            val user = currentUser
            if (user == null) {
                AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
            } else {
                key(user.uid) {
                    AuthenticatedShell(
                        currentUserId = user.uid,
                        firebaseUser = user,
                        prefsStore = prefsStore,
                        localThemeMode = localThemeMode,
                        localWallpaper = localWallpaper
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    firebaseUser: FirebaseUser,
    prefsStore: UserPreferencesStore,
    localThemeMode: ThemeMode,
    localWallpaper: ChatWallpaper
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(
                    repository = ResalahRepository(db),
                    prefsStore = prefsStore,
                    currentUserId = currentUserId,
                    firebaseUser = firebaseUser
                )
            }
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setOnline(true)
                Lifecycle.Event.ON_STOP -> viewModel.setOnline(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val privateUserState by viewModel.privateUserState.collectAsStateWithLifecycle()
    val directoryState by viewModel.directoryState.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val storiesState by viewModel.storiesState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val typingStatuses by viewModel.typingState.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val privateUser = (privateUserState as? UiState.Success)?.data
    val allProfiles = (directoryState as? UiState.Success)?.data ?: emptyList()
    val myProfile = remember(allProfiles, currentUserId) {
        allProfiles.firstOrNull { it.userId == currentUserId }
    }
    val allChats = (chatsState as? UiState.Success)?.data ?: emptyList()
    val selectedChat = remember(allChats, selectedChatId) {
        allChats.firstOrNull { it.id == selectedChatId }
    }

    val activeTheme = privateUser?.let { ThemeMode.fromWire(it.themeMode) } ?: localThemeMode
    val activeWallpaper = privateUser?.let { ChatWallpaper.fromWire(it.chatWallpaper) } ?: localWallpaper

    LaunchedEffect(privateUser?.themeMode, privateUser?.chatWallpaper) {
        privateUser?.let {
            prefsStore.setThemeMode(ThemeMode.fromWire(it.themeMode))
            prefsStore.setChatWallpaper(ChatWallpaper.fromWire(it.chatWallpaper))
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var currentTab by remember { mutableStateOf(MainTab.CHATS) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 760.dp

        if (isExpanded) {
            // Adaptive Tablet / Desktop List-Detail + NavigationRail
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = { TabIcon(tab, selected) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    when (currentTab) {
                        MainTab.CHATS -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                ChatsListScreen(
                                    currentUserId = currentUserId,
                                    chatsState = chatsState,
                                    directoryState = directoryState,
                                    searchQuery = searchQuery,
                                    selectedChatId = selectedChatId,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onChatSelected = { viewModel.selectChat(it) },
                                    onStartDirectChat = { peer -> viewModel.startDirectChat(peer) {} },
                                    onCreateGroup = { title, members -> viewModel.createGroupChat(title, members) {} },
                                    modifier = Modifier
                                        .width(360.dp)
                                        .fillMaxHeight()
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (selectedChat != null) {
                                        ChatScreen(
                                            chat = selectedChat,
                                            currentUserId = currentUserId,
                                            currentUserProfile = myProfile,
                                            allProfiles = allProfiles,
                                            messagesState = messagesState,
                                            typingStatuses = typingStatuses,
                                            wallpaper = activeWallpaper,
                                            themeMode = activeTheme,
                                            showBackButton = false,
                                            onBack = { viewModel.selectChat(null) },
                                            onSendText = { txt, reply ->
                                                viewModel.sendTextMessage(selectedChat, myProfile?.displayName ?: "مستخدم", txt, reply)
                                            },
                                            onSendImage = { b64, cap, reply ->
                                                viewModel.sendImageMessage(selectedChat, myProfile?.displayName ?: "مستخدم", b64, cap, reply)
                                            },
                                            onSendAudio = { b64, dur, reply ->
                                                viewModel.sendAudioMessage(selectedChat, myProfile?.displayName ?: "مستخدم", b64, dur, reply)
                                            },
                                            onSendFile = { name, mime, size, b64, reply ->
                                                viewModel.sendFileMessage(selectedChat, myProfile?.displayName ?: "مستخدم", name, mime, size, b64, reply)
                                            },
                                            onTypingChanged = { typing ->
                                                viewModel.onTypingChanged(selectedChat, myProfile?.displayName ?: "مستخدم", typing)
                                            },
                                            onMarkRead = { msgs ->
                                                viewModel.markChatRead(selectedChat, msgs, myProfile?.hideReadReceipts ?: false)
                                            },
                                            onDeleteMessage = { msgId -> viewModel.deleteMessage(selectedChat.id, msgId) },
                                            onUpdateGroup = { t, m, a -> viewModel.updateGroup(selectedChat.id, t, m, a) },
                                            onLeaveGroup = { viewModel.leaveGroup(selectedChat) {} },
                                            onNotify = viewModel::emitMessage
                                        )
                                    } else {
                                        EmptyChatsPlaceholder(
                                            title = "اختر محادثة للبدء",
                                            subtitle = "حدد إحدى المحادثات من القائمة الجانبية أو ابدأ محادثة جديدة."
                                        )
                                    }
                                }
                            }
                        }
                        MainTab.STORIES -> {
                            StoriesScreen(
                                currentUserId = currentUserId,
                                currentUserProfile = myProfile,
                                storiesState = storiesState,
                                onCreateStory = { type, text, media, hex ->
                                    viewModel.createStory(
                                        authorName = myProfile?.displayName ?: "مستخدم",
                                        authorAvatar = myProfile?.avatarData,
                                        type = type,
                                        text = text,
                                        mediaData = media,
                                        backgroundColorHex = hex
                                    ) {}
                                },
                                onMarkViewed = viewModel::markStoryViewed,
                                onDeleteStory = { id -> viewModel.deleteStory(id) {} },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        MainTab.SETTINGS -> {
                            SettingsScreen(
                                directoryProfile = myProfile,
                                privateUser = privateUser,
                                currentThemeMode = activeTheme,
                                currentWallpaper = activeWallpaper,
                                onSaveProfile = viewModel::saveProfile,
                                onThemeChanged = { mode -> viewModel.updateThemeMode(mode, activeWallpaper) },
                                onWallpaperChanged = { wp -> viewModel.updateWallpaper(wp, activeTheme) },
                                onPrivacyChanged = viewModel::updatePrivacy,
                                onSignOut = {
                                    signOut(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        beforeSignOut = { viewModel.goOfflineBeforeSignOut() }
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        } else {
            // Compact Phone Layout
            if (selectedChat != null) {
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
                    ChatScreen(
                        chat = selectedChat,
                        currentUserId = currentUserId,
                        currentUserProfile = myProfile,
                        allProfiles = allProfiles,
                        messagesState = messagesState,
                        typingStatuses = typingStatuses,
                        wallpaper = activeWallpaper,
                        themeMode = activeTheme,
                        showBackButton = true,
                        onBack = { viewModel.selectChat(null) },
                        onSendText = { txt, reply ->
                            viewModel.sendTextMessage(selectedChat, myProfile?.displayName ?: "مستخدم", txt, reply)
                        },
                        onSendImage = { b64, cap, reply ->
                            viewModel.sendImageMessage(selectedChat, myProfile?.displayName ?: "مستخدم", b64, cap, reply)
                        },
                        onSendAudio = { b64, dur, reply ->
                            viewModel.sendAudioMessage(selectedChat, myProfile?.displayName ?: "مستخدم", b64, dur, reply)
                        },
                        onSendFile = { name, mime, size, b64, reply ->
                            viewModel.sendFileMessage(selectedChat, myProfile?.displayName ?: "مستخدم", name, mime, size, b64, reply)
                        },
                        onTypingChanged = { typing ->
                            viewModel.onTypingChanged(selectedChat, myProfile?.displayName ?: "مستخدم", typing)
                        },
                        onMarkRead = { msgs ->
                            viewModel.markChatRead(selectedChat, msgs, myProfile?.hideReadReceipts ?: false)
                        },
                        onDeleteMessage = { msgId -> viewModel.deleteMessage(selectedChat.id, msgId) },
                        onUpdateGroup = { t, m, a -> viewModel.updateGroup(selectedChat.id, t, m, a) },
                        onLeaveGroup = { viewModel.leaveGroup(selectedChat) {} },
                        onNotify = viewModel::emitMessage,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            MainTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = { TabIcon(tab, selected) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("bottom_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            MainTab.CHATS -> ChatsListScreen(
                                currentUserId = currentUserId,
                                chatsState = chatsState,
                                directoryState = directoryState,
                                searchQuery = searchQuery,
                                selectedChatId = selectedChatId,
                                onSearchChange = viewModel::updateSearchQuery,
                                onChatSelected = { viewModel.selectChat(it) },
                                onStartDirectChat = { peer -> viewModel.startDirectChat(peer) {} },
                                onCreateGroup = { title, members -> viewModel.createGroupChat(title, members) {} }
                            )
                            MainTab.STORIES -> StoriesScreen(
                                currentUserId = currentUserId,
                                currentUserProfile = myProfile,
                                storiesState = storiesState,
                                onCreateStory = { type, text, media, hex ->
                                    viewModel.createStory(
                                        authorName = myProfile?.displayName ?: "مستخدم",
                                        authorAvatar = myProfile?.avatarData,
                                        type = type,
                                        text = text,
                                        mediaData = media,
                                        backgroundColorHex = hex
                                    ) {}
                                },
                                onMarkViewed = viewModel::markStoryViewed,
                                onDeleteStory = { id -> viewModel.deleteStory(id) {} }
                            )
                            MainTab.SETTINGS -> SettingsScreen(
                                directoryProfile = myProfile,
                                privateUser = privateUser,
                                currentThemeMode = activeTheme,
                                currentWallpaper = activeWallpaper,
                                onSaveProfile = viewModel::saveProfile,
                                onThemeChanged = { mode -> viewModel.updateThemeMode(mode, activeWallpaper) },
                                onWallpaperChanged = { wp -> viewModel.updateWallpaper(wp, activeTheme) },
                                onPrivacyChanged = viewModel::updatePrivacy,
                                onSignOut = {
                                    signOut(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        beforeSignOut = { viewModel.goOfflineBeforeSignOut() }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab, selected: Boolean) {
    val icon = when (tab) {
        MainTab.CHATS -> if (selected) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat
        MainTab.STORIES -> if (selected) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome
        MainTab.SETTINGS -> if (selected) Icons.Default.Settings else Icons.Outlined.Settings
    }
    Icon(imageVector = icon, contentDescription = tab.label)
}
