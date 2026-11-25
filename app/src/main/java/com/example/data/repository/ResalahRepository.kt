package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.DirectoryProfile
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.PrivateUser
import com.example.data.model.StoryItem
import com.example.data.model.TypingStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ResalahRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول عبر Google أولاً.")
    }

    private fun <T> Flow<T>.logFirestoreErrors(uid: String, operation: OperationType, path: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(exceptionToLog, operation, path)
            }
            throw error
        }

    suspend fun ensureUserInitialized(firebaseUser: FirebaseUser): Result<Unit> {
        val uid = firebaseUser.uid
        val privatePath = "users/$uid"
        val directoryPath = "directory/$uid"
        return try {
            val privateRef = db.collection("users").document(uid)
            val privateSnap = privateRef.get().await()
            if (!privateSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "email" to firebaseUser.email?.take(200),
                    "themeMode" to "system",
                    "chatWallpaper" to "emerald_classic",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                privateRef.set(payload).await()
            }

            val dirRef = db.collection("directory").document(uid)
            val dirSnap = dirRef.get().await()
            if (!dirSnap.exists()) {
                val fallbackName = firebaseUser.displayName?.takeIf { it.isNotBlank() }?.take(80)
                    ?: firebaseUser.email?.substringBefore("@")?.take(40)
                    ?: "مستخدم رسالة"
                val rawUsername = (firebaseUser.email?.substringBefore("@") ?: "user_${uid.take(6)}")
                    .replace(Regex("[^a-zA-Z0-9_.]"), "_")
                    .take(36)
                    .let { if (it.length < 2) "user_${uid.take(6)}" else it }
                val dirPayload = mapOf(
                    "userId" to uid,
                    "displayName" to fallbackName,
                    "username" to rawUsername,
                    "bio" to "متاح للتواصل عبر تطبيق رسالة",
                    "online" to true,
                    "discoverable" to true,
                    "hideLastSeen" to false,
                    "hideReadReceipts" to false,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                dirRef.set(dirPayload).await()
            } else {
                dirRef.update(
                    mapOf(
                        "online" to true,
                        "lastSeen" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$privatePath / $directoryPath")
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(online: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "directory/$uid"
        return try {
            db.collection("directory").document(uid).update(
                mapOf(
                    "online" to online,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observePrivateUser(uid: String): Flow<PrivateUser?> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(PrivateUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(uid, OperationType.GET, path)
        )
    }

    fun observeDirectory(uid: String): Flow<List<DirectoryProfile>> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        val path = "directory"
        emitAll(
            db.collection(path)
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DirectoryProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun updateProfileAndSettings(
        displayName: String,
        username: String,
        bio: String,
        avatarData: String?,
        phone: String?,
        hideLastSeen: Boolean,
        hideReadReceipts: Boolean,
        themeMode: String,
        chatWallpaper: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل"))
        val dirPath = "directory/$uid"
        val userPath = "users/$uid"
        return try {
            val cleanName = displayName.trim().take(80)
            val cleanUsername = username.trim().replace(" ", "_").take(40)
            require(cleanName.isNotEmpty()) { "يرجى إدخال الاسم" }
            require(cleanUsername.length >= 2) { "يجب أن يكون اسم المستخدم حرفين على الأقل" }

            val dirUpdates = mutableMapOf<String, Any>(
                "displayName" to cleanName,
                "username" to cleanUsername,
                "bio" to bio.trim().take(200),
                "hideLastSeen" to hideLastSeen,
                "hideReadReceipts" to hideReadReceipts,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarData.isNullOrBlank()) {
                require(avatarData.length <= 350_000) { "حجم الصورة كبير جداً" }
                dirUpdates["avatarData"] = avatarData
            }
            db.collection("directory").document(uid).update(dirUpdates).await()

            val userUpdates = mutableMapOf<String, Any>(
                "themeMode" to themeMode,
                "chatWallpaper" to chatWallpaper,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!phone.isNullOrBlank()) {
                userUpdates["phone"] = phone.trim().take(30)
            }
            db.collection("users").document(uid).update(userUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "$dirPath & $userPath")
            Result.failure(e)
        }
    }

    suspend fun updateAppearance(themeMode: String, chatWallpaper: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "themeMode" to themeMode,
                    "chatWallpaper" to chatWallpaper,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacy(hideLastSeen: Boolean, hideReadReceipts: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل"))
        val path = "directory/$uid"
        return try {
            db.collection("directory").document(uid).update(
                mapOf(
                    "hideLastSeen" to hideLastSeen,
                    "hideReadReceipts" to hideReadReceipts,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeChats(uid: String): Flow<List<ChatThread>> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        val path = "chats"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun getOrCreateDirectChat(peerUid: String): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val sorted = listOf(uid, peerUid).sorted()
        val chatId = "dm_${sorted[0]}_${sorted[1]}".take(128)
        val path = "chats/$chatId"
        return try {
            val chatRef = db.collection("chats").document(chatId)
            val existing = chatRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "type" to "direct",
                    "title" to "",
                    "memberUids" to sorted.distinct(),
                    "adminUids" to sorted.distinct(),
                    "createdBy" to uid,
                    "lastMessageText" to "",
                    "lastMessageType" to "none",
                    "lastMessageSenderId" to uid,
                    "lastMessageStatus" to "none",
                    "unreadCounts" to emptyMap<String, Long>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                chatRef.set(payload).await()
            }
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupChat(
        title: String,
        selectedMemberUids: List<String>,
        avatarData: String? = null
    ): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val cleanTitle = title.trim().take(100)
        if (cleanTitle.isEmpty()) return Result.failure(IllegalArgumentException("يرجى إدخال اسم المجموعة"))
        val members = (listOf(uid) + selectedMemberUids).distinct().take(50)
        val chatId = "grp_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "chats/$chatId"
        return try {
            val payload = mapOf(
                "type" to "group",
                "title" to cleanTitle,
                "avatarData" to avatarData?.take(350_000),
                "memberUids" to members,
                "adminUids" to listOf(uid),
                "createdBy" to uid,
                "lastMessageText" to "تم إنشاء المجموعة",
                "lastMessageType" to "text",
                "lastMessageSenderId" to uid,
                "lastMessageStatus" to "sent",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "unreadCounts" to emptyMap<String, Long>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("chats").document(chatId).set(payload).await()
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupMembersAndTitle(
        chatId: String,
        title: String,
        memberUids: List<String>,
        adminUids: List<String>
    ): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            requireUserId()
            val cleanTitle = title.trim().take(100)
            require(cleanTitle.isNotEmpty()) { "اسم المجموعة مطلوب" }
            require(memberUids.isNotEmpty()) { "المجموعة يجب أن تضم عضواً واحداً على الأقل" }
            db.collection("chats").document(chatId).update(
                mapOf(
                    "title" to cleanTitle,
                    "memberUids" to memberUids.distinct().take(50),
                    "adminUids" to adminUids.distinct().take(20),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(chat: ChatThread): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "chats/${chat.id}"
        return try {
            val remainingMembers = chat.memberUids.filter { it != uid }
            if (remainingMembers.isEmpty()) {
                db.collection("chats").document(chat.id).delete().await()
            } else {
                val remainingAdmins = chat.adminUids.filter { it != uid }.ifEmpty { listOf(remainingMembers.first()) }
                db.collection("chats").document(chat.id).update(
                    mapOf(
                        "memberUids" to remainingMembers,
                        "adminUids" to remainingAdmins,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeMessages(chatId: String, uid: String): Flow<List<ChatMessage>> = flow {
        require(chatId.isNotBlank() && uid.isNotBlank())
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun sendMessage(
        chat: ChatThread,
        senderName: String,
        type: MessageType,
        text: String,
        mediaData: String? = null,
        mediaName: String? = null,
        mediaMime: String? = null,
        mediaSizeBytes: Long? = null,
        audioDurationSec: Long? = null,
        replyTo: ChatMessage? = null
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val msgPath = "chats/${chat.id}/messages/$msgId"
        return try {
            val cleanText = text.trim().take(4000)
            if (type == MessageType.TEXT && cleanText.isEmpty()) {
                return Result.failure(IllegalArgumentException("الرسالة فارغة"))
            }
            val replyPreview = replyTo?.let {
                when {
                    it.deleted -> "رسالة محذوفة"
                    it.type == MessageType.IMAGE.wireValue -> "صورة"
                    it.type == MessageType.AUDIO.wireValue -> "رسالة صوتية"
                    it.type == MessageType.FILE.wireValue -> it.mediaName ?: "ملف"
                    else -> it.text.take(180)
                }
            }

            val msgPayload = mapOf(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to senderName.take(80).ifBlank { "مستخدم" },
                "memberUids" to chat.memberUids.take(50),
                "type" to type.wireValue,
                "text" to cleanText,
                "mediaData" to mediaData?.take(700_000),
                "mediaName" to mediaName?.take(200),
                "mediaMime" to mediaMime?.take(100),
                "mediaSizeBytes" to mediaSizeBytes,
                "audioDurationSec" to audioDurationSec,
                "replyToMessageId" to replyTo?.id,
                "replyToSenderName" to replyTo?.senderName?.take(80),
                "replyToPreview" to replyPreview?.take(200),
                "status" to MessageStatus.SENT.wireValue,
                "readBy" to listOf(uid),
                "deleted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("chats").document(chat.id)
                .collection("messages").document(msgId)
                .set(msgPayload).await()

            val previewText = when (type) {
                MessageType.TEXT -> cleanText.take(480)
                MessageType.IMAGE -> if (cleanText.isNotBlank()) "📷 $cleanText".take(480) else "📷 صورة"
                MessageType.AUDIO -> "🎤 رسالة صوتية (${audioDurationSec ?: 0} ث)"
                MessageType.FILE -> "📎 ${mediaName ?: "ملف"}".take(480)
            }

            val newUnread = chat.unreadCounts.toMutableMap()
            chat.memberUids.forEach { memberId ->
                if (memberId != uid) {
                    newUnread[memberId] = (newUnread[memberId] ?: 0L) + 1L
                }
            }

            db.collection("chats").document(chat.id).update(
                mapOf(
                    "lastMessageText" to previewText,
                    "lastMessageType" to type.wireValue,
                    "lastMessageSenderId" to uid,
                    "lastMessageStatus" to MessageStatus.SENT.wireValue,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadCounts" to newUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgPath)
            Result.failure(e)
        }
    }

    suspend fun markMessagesDeliveredAndRead(
        chat: ChatThread,
        uid: String,
        messages: List<ChatMessage>,
        hideReadReceipts: Boolean
    ) {
        try {
            if ((chat.unreadCounts[uid] ?: 0L) > 0L) {
                val updatedCounts = chat.unreadCounts.toMutableMap()
                updatedCounts[uid] = 0L
                db.collection("chats").document(chat.id).update(
                    mapOf(
                        "unreadCounts" to updatedCounts,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val incomingToUpdate = messages.filter {
                it.senderId != uid && !it.readBy.contains(uid)
            }
            for (msg in incomingToUpdate.takeLast(20)) {
                val targetStatus = if (hideReadReceipts) MessageStatus.DELIVERED.wireValue else MessageStatus.READ.wireValue
                val newReadBy = (msg.readBy + uid).distinct().take(50)
                db.collection("chats").document(chat.id)
                    .collection("messages").document(msg.id)
                    .update(
                        mapOf(
                            "status" to targetStatus,
                            "readBy" to newReadBy,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }

            if (chat.lastMessageSenderId != null && chat.lastMessageSenderId != uid) {
                val targetStatus = if (hideReadReceipts) MessageStatus.DELIVERED.wireValue else MessageStatus.READ.wireValue
                if (chat.lastMessageStatus != targetStatus && chat.lastMessageStatus != MessageStatus.READ.wireValue) {
                    db.collection("chats").document(chat.id).update(
                        mapOf(
                            "lastMessageStatus" to targetStatus,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/${chat.id}/messages")
        }
    }

    suspend fun softDeleteMessage(chatId: String, messageId: String): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "deleted" to true,
                        "text" to "",
                        "mediaData" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeTyping(chatId: String, uid: String): Flow<List<TypingStatus>> = flow {
        require(chatId.isNotBlank() && uid.isNotBlank())
        val path = "chats/$chatId/typing"
        emitAll(
            db.collection("chats").document(chatId).collection("typing")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TypingStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun setTyping(chat: ChatThread, displayName: String, typing: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val path = "chats/${chat.id}/typing/$uid"
        try {
            db.collection("chats").document(chat.id)
                .collection("typing").document(uid)
                .set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to displayName.take(80).ifBlank { "مستخدم" },
                        "typing" to typing,
                        "memberUids" to chat.memberUids.take(50),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    fun observeActiveStories(uid: String): Flow<List<StoryItem>> = flow {
        require(uid.isNotBlank())
        val path = "stories"
        emitAll(
            db.collection(path)
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    val now = System.currentTimeMillis()
                    snap.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.expiresAtEpochMs > now }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun createStory(
        authorName: String,
        authorAvatar: String?,
        type: String,
        text: String,
        mediaData: String?,
        backgroundColorHex: Long
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val storyId = "st_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "stories/$storyId"
        return try {
            val payload = mapOf(
                "userId" to uid,
                "authorName" to authorName.take(80).ifBlank { "مستخدم" },
                "authorAvatar" to authorAvatar?.take(350_000),
                "type" to type,
                "text" to text.trim().take(500),
                "mediaData" to mediaData?.take(700_000),
                "backgroundColorHex" to backgroundColorHex,
                "discoverable" to true,
                "viewerUids" to emptyList<String>(),
                "expiresAtEpochMs" to (System.currentTimeMillis() + 86_400_000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("stories").document(storyId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(story: StoryItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (story.userId == uid || story.viewerUids.contains(uid)) return Result.success(Unit)
        val path = "stories/${story.id}"
        return try {
            val updated = (story.viewerUids + uid).distinct().take(100)
            db.collection("stories").document(story.id).update(
                mapOf(
                    "viewerUids" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val path = "stories/$storyId"
        return try {
            requireUserId()
            db.collection("stories").document(storyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
