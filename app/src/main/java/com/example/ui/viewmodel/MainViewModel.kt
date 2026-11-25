package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ChatWallpaper
import com.example.data.model.DirectoryProfile
import com.example.data.model.MessageType
import com.example.data.model.PrivateUser
import com.example.data.model.StoryItem
import com.example.data.model.ThemeMode
import com.example.data.model.TypingStatus
import com.example.data.model.UiState
import com.example.data.preferences.UserPreferencesStore
import com.example.data.repository.ResalahRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: ResalahRepository,
    private val prefsStore: UserPreferencesStore,
    val currentUserId: String,
    firebaseUser: FirebaseUser?
) : ViewModel() {

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var typingDebounceJob: Job? = null

    init {
        if (firebaseUser != null) {
            viewModelScope.launch {
                repository.ensureUserInitialized(firebaseUser)
            }
        }
    }

    private fun <T> Flow<T>.toUiState(tag: String, fallbackError: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(tag, fallbackError, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val privateUserState: StateFlow<UiState<PrivateUser?>> =
        repository.observePrivateUser(currentUserId)
            .toUiState("MainVM", "تعذر تحميل بيانات الحساب")

    val directoryState: StateFlow<UiState<List<DirectoryProfile>>> =
        repository.observeDirectory(currentUserId)
            .toUiState("MainVM", "تعذر تحميل قائمة المستخدمين")

    val chatsState: StateFlow<UiState<List<ChatThread>>> =
        repository.observeChats(currentUserId)
            .toUiState("MainVM", "تعذر تحميل المحادثات")

    val storiesState: StateFlow<UiState<List<StoryItem>>> =
        repository.observeActiveStories(currentUserId)
            .toUiState("MainVM", "تعذر تحميل الحالات")

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        _selectedChatId.flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(chatId, currentUserId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w("MainVM", "Error observing messages", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل الرسائل"))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(emptyList())
        )

    val typingState: StateFlow<List<TypingStatus>> =
        _selectedChatId.flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeTyping(chatId, currentUserId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun emitMessage(text: String) {
        _snackbarEvents.tryEmit(text)
    }

    fun startDirectChat(peerUid: String, onChatReady: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.getOrCreateDirectChat(peerUid)
            _isBusy.value = false
            res.onSuccess { chatId ->
                _selectedChatId.value = chatId
                onChatReady(chatId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "تعذر فتح المحادثة")
            }
        }
    }

    fun createGroupChat(title: String, memberUids: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createGroupChat(title, memberUids)
            _isBusy.value = false
            res.onSuccess { chatId ->
                _selectedChatId.value = chatId
                _snackbarEvents.tryEmit("تم إنشاء المجموعة بنجاح")
                onCreated(chatId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "تعذر إنشاء المجموعة")
            }
        }
    }

    fun updateGroup(chatId: String, title: String, memberUids: List<String>, adminUids: List<String>) {
        viewModelScope.launch {
            repository.updateGroupMembersAndTitle(chatId, title, memberUids, adminUids)
                .onSuccess { _snackbarEvents.tryEmit("تم تحديث بيانات المجموعة") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "تعذر تحديث المجموعة") }
        }
    }

    fun leaveGroup(chat: ChatThread, onLeft: () -> Unit) {
        viewModelScope.launch {
            repository.leaveGroup(chat)
                .onSuccess {
                    if (_selectedChatId.value == chat.id) _selectedChatId.value = null
                    _snackbarEvents.tryEmit("غادرت المجموعة")
                    onLeft()
                }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "تعذر مغادرة المجموعة") }
        }
    }

    fun sendTextMessage(chat: ChatThread, senderName: String, text: String, replyTo: ChatMessage?) {
        viewModelScope.launch {
            stopTypingNow(chat, senderName)
            repository.sendMessage(
                chat = chat,
                senderName = senderName,
                type = MessageType.TEXT,
                text = text,
                replyTo = replyTo
            ).onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "فشل إرسال الرسالة")
            }
        }
    }

    fun sendImageMessage(chat: ChatThread, senderName: String, base64: String, caption: String, replyTo: ChatMessage?) {
        viewModelScope.launch {
            repository.sendMessage(
                chat = chat,
                senderName = senderName,
                type = MessageType.IMAGE,
                text = caption,
                mediaData = base64,
                mediaMime = "image/jpeg",
                replyTo = replyTo
            ).onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "فشل إرسال الصورة")
            }
        }
    }

    fun sendAudioMessage(chat: ChatThread, senderName: String, base64: String, durationSec: Long, replyTo: ChatMessage?) {
        viewModelScope.launch {
            repository.sendMessage(
                chat = chat,
                senderName = senderName,
                type = MessageType.AUDIO,
                text = "",
                mediaData = base64,
                mediaMime = "audio/mp4",
                audioDurationSec = durationSec,
                replyTo = replyTo
            ).onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "فشل إرسال الرسالة الصوتية")
            }
        }
    }

    fun sendFileMessage(
        chat: ChatThread,
        senderName: String,
        fileName: String,
        mime: String,
        sizeBytes: Long,
        base64: String?,
        replyTo: ChatMessage?
    ) {
        viewModelScope.launch {
            repository.sendMessage(
                chat = chat,
                senderName = senderName,
                type = MessageType.FILE,
                text = fileName,
                mediaData = base64,
                mediaName = fileName,
                mediaMime = mime,
                mediaSizeBytes = sizeBytes,
                replyTo = replyTo
            ).onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "فشل إرسال الملف")
            }
        }
    }

    fun onTypingChanged(chat: ChatThread, displayName: String, hasText: Boolean) {
        typingDebounceJob?.cancel()
        if (!hasText) {
            viewModelScope.launch { repository.setTyping(chat, displayName, false) }
            return
        }
        typingDebounceJob = viewModelScope.launch {
            repository.setTyping(chat, displayName, true)
            delay(3500L)
            repository.setTyping(chat, displayName, false)
        }
    }

    fun stopTypingNow(chat: ChatThread, displayName: String) {
        typingDebounceJob?.cancel()
        viewModelScope.launch { repository.setTyping(chat, displayName, false) }
    }

    fun markChatRead(chat: ChatThread, messages: List<ChatMessage>, hideReadReceipts: Boolean) {
        viewModelScope.launch {
            repository.markMessagesDeliveredAndRead(chat, currentUserId, messages, hideReadReceipts)
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            repository.softDeleteMessage(chatId, messageId)
                .onSuccess { _snackbarEvents.tryEmit("تم حذف الرسالة") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "تعذر حذف الرسالة") }
        }
    }

    fun createStory(
        authorName: String,
        authorAvatar: String?,
        type: String,
        text: String,
        mediaData: String?,
        backgroundColorHex: Long,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createStory(authorName, authorAvatar, type, text, mediaData, backgroundColorHex)
            _isBusy.value = false
            res.onSuccess {
                _snackbarEvents.tryEmit("تم نشر الحالة لمدة 24 ساعة")
                onDone()
            }.onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "تعذر نشر الحالة")
            }
        }
    }

    fun markStoryViewed(story: StoryItem) {
        viewModelScope.launch { repository.markStoryViewed(story) }
    }

    fun deleteStory(storyId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteStory(storyId)
                .onSuccess {
                    _snackbarEvents.tryEmit("تم حذف الحالة")
                    onDone()
                }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "تعذر حذف الحالة") }
        }
    }

    fun saveProfile(
        displayName: String,
        username: String,
        bio: String,
        avatarData: String?,
        phone: String?,
        hideLastSeen: Boolean,
        hideReadReceipts: Boolean,
        themeMode: ThemeMode,
        wallpaper: ChatWallpaper
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            prefsStore.setThemeMode(themeMode)
            prefsStore.setChatWallpaper(wallpaper)
            val res = repository.updateProfileAndSettings(
                displayName = displayName,
                username = username,
                bio = bio,
                avatarData = avatarData,
                phone = phone,
                hideLastSeen = hideLastSeen,
                hideReadReceipts = hideReadReceipts,
                themeMode = themeMode.wireValue,
                chatWallpaper = wallpaper.wireValue
            )
            _isBusy.value = false
            res.onSuccess { _snackbarEvents.tryEmit("تم حفظ التغييرات بنجاح") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "تعذر حفظ التغييرات") }
        }
    }

    fun updateThemeMode(mode: ThemeMode, currentWallpaper: ChatWallpaper) {
        viewModelScope.launch {
            prefsStore.setThemeMode(mode)
            repository.updateAppearance(mode.wireValue, currentWallpaper.wireValue)
        }
    }

    fun updateWallpaper(wallpaper: ChatWallpaper, currentTheme: ThemeMode) {
        viewModelScope.launch {
            prefsStore.setChatWallpaper(wallpaper)
            repository.updateAppearance(currentTheme.wireValue, wallpaper.wireValue)
            _snackbarEvents.tryEmit("تم تغيير خلفية الدردشة")
        }
    }

    fun updatePrivacy(hideLastSeen: Boolean, hideReadReceipts: Boolean) {
        viewModelScope.launch {
            repository.updatePrivacy(hideLastSeen, hideReadReceipts)
                .onSuccess { _snackbarEvents.tryEmit("تم تحديث إعدادات الخصوصية") }
        }
    }

    suspend fun goOfflineBeforeSignOut() {
        repository.setOnlineStatus(false)
    }

    fun setOnline(online: Boolean) {
        viewModelScope.launch { repository.setOnlineStatus(online) }
    }
}
