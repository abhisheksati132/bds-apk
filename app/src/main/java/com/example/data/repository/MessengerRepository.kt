package com.example.data.repository

import android.net.Uri
import com.example.data.crypto.CryptoHelper
import com.example.data.local.MessengerDao
import com.example.data.model.*
import com.example.data.remote.CloudUser
import com.example.data.remote.FirebaseCloudService
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class MessengerRepository(
    private val dao: MessengerDao,
    private val cloudService: FirebaseCloudService? = null
) {

    val activeConversations: Flow<List<ConversationEntity>> = dao.getActiveConversations()
    val allContacts: Flow<List<ContactEntity>> = dao.getAllContacts()
    val allStatuses: Flow<List<StatusEntity>> = dao.getAllStatuses()
    val allCalls: Flow<List<CallEntity>> = dao.getAllCalls()

    val presenceMap: Flow<Map<String, Boolean>> = cloudService?.presenceMap ?: flowOf(emptyMap())

    fun getConversation(id: Long): Flow<ConversationEntity?> = dao.getConversationById(id)

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> =
        dao.getMessagesForConversation(conversationId).map { list ->
            list.map { msg ->
                if (msg.text.isEmpty() && msg.cipherText.isNotEmpty()) {
                    msg.copy(text = CryptoHelper.decrypt(msg.cipherText))
                } else {
                    msg
                }
            }
        }

    fun observeCloudUsers(): Flow<List<CloudUser>> {
        return cloudService?.observeRegisteredUsers() ?: flowOf(emptyList())
    }

    fun streamConversation(
        myHandle: String,
        peerHandle: String,
        onMessagesReceived: (List<Map<String, Any>>) -> Unit
    ): ListenerRegistration? {
        return cloudService?.streamConversationMessages(myHandle, peerHandle, onMessagesReceived)
    }

    suspend fun sendMessage(
        conversationId: Long,
        text: String,
        isDisappearing: Boolean = false,
        disappearingTimerSeconds: Long = 0,
        replyToId: Long? = null,
        replyToText: String? = null,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        voiceDurationSeconds: Int = 0,
        myHandle: String = "me"
    ) {
        val now = System.currentTimeMillis()
        val expiresAt = if (isDisappearing && disappearingTimerSeconds > 0) {
            now + (disappearingTimerSeconds * 1000L)
        } else null

        val cipher = if (type == MessageType.TEXT) CryptoHelper.encrypt(text) else ""

        val message = MessageEntity(
            conversationId = conversationId,
            senderId = "me",
            senderName = "You",
            text = text,
            cipherText = cipher,
            timestamp = now,
            isMe = true,
            status = MessageStatus.SENT,
            type = type,
            mediaUrl = mediaUrl,
            voiceDurationSeconds = voiceDurationSeconds,
            isDisappearing = isDisappearing || disappearingTimerSeconds > 0,
            expiresAtTimestamp = expiresAt,
            replyToId = replyToId,
            replyToText = replyToText
        )

        dao.insertMessage(message)

        // Update conversation preview & timer
        val preview = when (type) {
            MessageType.IMAGE -> "📷 Photo attachment"
            MessageType.VOICE -> "🎤 Voice message (${voiceDurationSeconds}s)"
            else -> text
        }

        val conv = dao.getConversationById(conversationId).firstOrNull()
        if (conv != null) {
            dao.updateConversation(
                conv.copy(
                    lastMessage = preview,
                    lastTimestamp = now,
                    disappearingTimerSeconds = if (disappearingTimerSeconds > 0) disappearingTimerSeconds else conv.disappearingTimerSeconds
                )
            )

            // Relay via Firebase Cloud
            val groupMembersList = if (conv.isGroup && !conv.groupMembers.isNullOrEmpty()) {
                conv.groupMembers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else emptyList()

            cloudService?.sendCloudMessage(
                senderHandle = myHandle,
                recipientHandle = conv.peerHandle,
                cipherText = cipher,
                type = type,
                mediaUrl = mediaUrl,
                voiceDurationSeconds = voiceDurationSeconds,
                disappearingTimerSeconds = disappearingTimerSeconds,
                replyToText = replyToText,
                isGroup = conv.isGroup,
                groupId = conv.cloudDocId ?: if (conv.isGroup) conv.peerHandle else null,
                groupName = if (conv.isGroup) conv.peerName else null,
                groupMembers = groupMembersList
            )
        }
    }

    suspend fun sendImageMessage(
        conversationId: Long,
        imageUri: Uri,
        isDisappearing: Boolean = false,
        disappearingTimerSeconds: Long = 0,
        myHandle: String = "me"
    ) {
        val conv = dao.getConversationById(conversationId).firstOrNull()
        val convKey = conv?.peerHandle ?: conversationId.toString()

        // Upload to Firebase Storage
        val mediaUrl = cloudService?.uploadChatImage(imageUri, convKey) ?: imageUri.toString()

        sendMessage(
            conversationId = conversationId,
            text = "📷 Photo",
            isDisappearing = isDisappearing,
            disappearingTimerSeconds = disappearingTimerSeconds,
            type = MessageType.IMAGE,
            mediaUrl = mediaUrl,
            myHandle = myHandle
        )
    }

    suspend fun createGroupChat(
        groupName: String,
        memberHandles: List<String>,
        adminHandle: String
    ): Long {
        val cleanAdmin = adminHandle.lowercase().replace("@", "").trim()
        val allMembers = (memberHandles + cleanAdmin)
            .map { it.lowercase().replace("@", "").trim() }
            .distinct()
            .filter { it.isNotEmpty() }

        val cloudGroupId = cloudService?.createGroupInCloud(
            groupName = groupName,
            memberHandles = allMembers,
            adminHandle = cleanAdmin
        ) ?: ("grp_" + UUID.randomUUID().toString().take(8))

        val groupConv = ConversationEntity(
            peerId = "grp_$cloudGroupId",
            peerName = groupName,
            peerHandle = cloudGroupId,
            avatarBgColorHex = "#E8DEF8",
            avatarTextColorHex = "#1D192B",
            lastMessage = "Group created with ${allMembers.size} members",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            isOnline = true,
            isGroup = true,
            groupMembers = allMembers.joinToString(","),
            groupAdminHandle = cleanAdmin,
            isEncrypted = true,
            keyFingerprint = CryptoHelper.generateFingerprint(cloudGroupId),
            cloudDocId = cloudGroupId
        )

        val convId = dao.insertConversation(groupConv)

        // Add initial system message
        dao.insertMessage(
            MessageEntity(
                conversationId = convId,
                senderId = "system",
                senderName = "System",
                text = "🛡️ Group '$groupName' created. All communications are end-to-end encrypted.",
                cipherText = "",
                timestamp = System.currentTimeMillis(),
                isMe = true,
                status = MessageStatus.READ,
                type = MessageType.TEXT
            )
        )

        return convId
    }

    suspend fun receiveSimulatedReply(
        conversationId: Long,
        replyText: String
    ) {
        val now = System.currentTimeMillis()
        val cipher = CryptoHelper.encrypt(replyText)
        val message = MessageEntity(
            conversationId = conversationId,
            senderId = "peer",
            text = replyText,
            cipherText = cipher,
            timestamp = now,
            isMe = false,
            status = MessageStatus.READ
        )
        dao.insertMessage(message)
    }

    suspend fun searchCloudUser(handle: String): CloudUser? {
        return cloudService?.searchUserByHandle(handle)
    }

    suspend fun getRecentCloudUsers(): List<CloudUser> {
        return cloudService?.getRecentRegisteredUsers() ?: emptyList()
    }

    suspend fun registerCloudUser(handle: String, name: String, publicKey: String): Boolean {
        return cloudService?.registerUser(handle, name, publicKey) ?: false
    }

    suspend fun createOrGetConversationForContact(contact: ContactEntity): Long {
        val existing = dao.getConversationByHandle(contact.handle)
        if (existing != null) {
            return existing.id
        }
        val newConv = ConversationEntity(
            peerId = "u_${contact.handle.replace(".", "_")}",
            peerName = contact.name,
            peerHandle = contact.handle,
            avatarBgColorHex = contact.avatarBgHex,
            avatarTextColorHex = contact.avatarTextHex,
            lastMessage = "Encrypted conversation initialized",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            isOnline = true,
            isEncrypted = true,
            keyFingerprint = CryptoHelper.generateFingerprint(contact.handle),
            avatarUrl = contact.avatarUrl
        )
        return dao.insertConversation(newConv)
    }

    suspend fun createOrGetConversationForCloudUser(cloudUser: CloudUser): Long {
        val existing = dao.getConversationByHandle(cloudUser.handle)
        if (existing != null) {
            return existing.id
        }
        val newConv = ConversationEntity(
            peerId = "u_${cloudUser.handle.replace(".", "_")}",
            peerName = cloudUser.displayName.ifBlank { "@${cloudUser.handle}" },
            peerHandle = cloudUser.handle,
            avatarBgColorHex = cloudUser.avatarBgHex,
            avatarTextColorHex = cloudUser.avatarTextHex,
            lastMessage = "Encrypted cloud conversation started",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            isOnline = cloudUser.isOnline,
            isEncrypted = true,
            keyFingerprint = CryptoHelper.generateFingerprint(cloudUser.handle),
            avatarUrl = cloudUser.avatarUrl.ifBlank { null }
        )
        // Also save to contacts
        dao.insertContact(
            ContactEntity(
                handle = cloudUser.handle,
                name = cloudUser.displayName.ifBlank { "@${cloudUser.handle}" },
                avatarBgHex = cloudUser.avatarBgHex,
                avatarTextHex = cloudUser.avatarTextHex,
                publicKey = cloudUser.publicKey,
                about = cloudUser.about,
                avatarUrl = cloudUser.avatarUrl.ifBlank { null }
            )
        )
        return dao.insertConversation(newConv)
    }

    suspend fun markConversationRead(conversationId: Long, myHandle: String = "") {
        dao.markConversationAsRead(conversationId)
        val conv = dao.getConversationById(conversationId).firstOrNull()
        if (conv != null && myHandle.isNotBlank()) {
            cloudService?.markMessagesAsReadInCloud(conv.peerHandle, myHandle)
        }
    }

    suspend fun setDisappearingTimer(conversationId: Long, timerSeconds: Long) {
        dao.updateDisappearingTimer(conversationId, timerSeconds)
    }

    suspend fun deleteConversation(conversationId: Long) {
        dao.deleteAllMessagesInConversation(conversationId)
        dao.deleteConversation(conversationId)
    }

    suspend fun clearChatMessages(conversationId: Long, myHandle: String = "") {
        val conv = dao.getConversationById(conversationId).firstOrNull()
        dao.deleteAllMessagesInConversation(conversationId)
        if (conv != null) {
            dao.updateConversation(conv.copy(lastMessage = "Chat cleared"))
            if (myHandle.isNotBlank()) {
                cloudService?.clearChatInCloud(
                    myHandle = myHandle,
                    peerHandle = conv.peerHandle,
                    isGroup = conv.isGroup,
                    groupId = conv.cloudDocId
                )
            }
        }
    }

    suspend fun setTyping(threadKey: String, userHandle: String, isTyping: Boolean) {
        cloudService?.setTypingStatus(threadKey, userHandle, isTyping)
    }

    fun observeTyping(threadKey: String, peerHandle: String, onTypingChanged: (Boolean) -> Unit): ListenerRegistration? {
        return cloudService?.observeTypingStatus(threadKey, peerHandle, onTypingChanged)
    }

    suspend fun blockUser(myHandle: String, peerHandle: String) {
        cloudService?.blockUser(myHandle, peerHandle)
        dao.setContactBlocked(peerHandle, true)
    }

    suspend fun unblockUser(myHandle: String, peerHandle: String) {
        cloudService?.unblockUser(myHandle, peerHandle)
        dao.setContactBlocked(peerHandle, false)
    }

    suspend fun syncBlockedUsers(myHandle: String) {
        cloudService?.syncBlockedUsers(myHandle)
    }

    suspend fun updateMessageReaction(messageId: Long, cloudDocId: String?, reaction: String?) {
        dao.updateMessageReaction(messageId, reaction)
        if (!cloudDocId.isNullOrBlank()) {
            cloudService?.updateMessageReactionInCloud(cloudDocId, reaction)
        }
    }

    suspend fun purgeExpiredMessages() {
        val now = System.currentTimeMillis()
        dao.purgeExpiredMessages(now)
        cloudService?.purgeExpiredCloudMessages()
    }

    suspend fun addContact(contact: ContactEntity) {
        dao.insertContact(contact)
    }

    suspend fun addStatus(caption: String) {
        dao.insertStatus(
            StatusEntity(
                authorName = "You",
                authorHandle = "me.private",
                avatarBgHex = "#DDE1FF",
                avatarTextHex = "#001453",
                caption = caption,
                timestamp = System.currentTimeMillis(),
                isMe = true,
                isViewed = true
            )
        )
    }

    suspend fun logCall(peerName: String, peerHandle: String, avatarBg: String, avatarText: String, isVideo: Boolean) {
        dao.insertCall(
            CallEntity(
                peerName = peerName,
                peerHandle = peerHandle,
                avatarBgHex = avatarBg,
                avatarTextHex = avatarText,
                timestamp = System.currentTimeMillis(),
                isIncoming = false,
                isMissed = false,
                isVideo = isVideo,
                durationSeconds = (15..120).random()
            )
        )
    }

    suspend fun panicWipeVault() {
        dao.wipeMessages()
        dao.wipeConversations()
        dao.wipeCalls()
        dao.wipeStatuses()
        dao.wipeContacts()
    }
}
