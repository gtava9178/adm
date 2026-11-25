package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ThemeMode(val wireValue: String, val arabicLabel: String) {
    SYSTEM("system", "تلقائي (حسب النظام)"),
    LIGHT("light", "الوضع النهاري"),
    DARK("dark", "الوضع الليلي");

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

enum class ChatWallpaper(val wireValue: String, val arabicLabel: String) {
    EMERALD_CLASSIC("emerald_classic", "زمردي كلاسيكي"),
    WARM_SAND("warm_sand", "رملي دافئ"),
    MIDNIGHT_TEAL("midnight_teal", "أزرق ليلي"),
    DESERT_ROSE("desert_rose", "وردي هادئ"),
    MINIMAL_SLATE("minimal_slate", "رمادي أنيق"),
    BOTANICAL_MINT("botanical_mint", "نعناع ناعم");

    companion object {
        fun fromWire(value: String?): ChatWallpaper =
            entries.firstOrNull { it.wireValue == value } ?: EMERALD_CLASSIC
    }
}

enum class MessageType(val wireValue: String) {
    TEXT("text"),
    IMAGE("image"),
    AUDIO("audio"),
    FILE("file");

    companion object {
        fun fromWire(value: String?): MessageType =
            entries.firstOrNull { it.wireValue == value } ?: TEXT
    }
}

enum class MessageStatus(val wireValue: String) {
    SENT("sent"),
    DELIVERED("delivered"),
    READ("read");

    companion object {
        fun fromWire(value: String?): MessageStatus =
            entries.firstOrNull { it.wireValue == value } ?: SENT
    }
}

@Keep
@IgnoreExtraProperties
data class PrivateUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val phone: String? = null,
    val themeMode: String = "system",
    val chatWallpaper: String = "emerald_classic",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DirectoryProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String? = null,
    val avatarData: String? = null,
    val online: Boolean = false,
    val discoverable: Boolean = true,
    val hideLastSeen: Boolean = false,
    val hideReadReceipts: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val type: String = "direct",
    val title: String = "",
    val avatarData: String? = null,
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val createdBy: String = "",
    val lastMessageText: String? = null,
    val lastMessageType: String? = "none",
    val lastMessageSenderId: String? = null,
    val lastMessageStatus: String? = "none",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val type: String = "text",
    val text: String = "",
    val mediaData: String? = null,
    val mediaName: String? = null,
    val mediaMime: String? = null,
    val mediaSizeBytes: Long? = null,
    val audioDurationSec: Long? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val status: String = "sent",
    val readBy: List<String> = emptyList(),
    val deleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TypingStatus(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val typing: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val type: String = "text",
    val text: String = "",
    val mediaData: String? = null,
    val backgroundColorHex: Long = 0xFF0D6E5C,
    val discoverable: Boolean = true,
    val viewerUids: List<String> = emptyList(),
    val expiresAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
