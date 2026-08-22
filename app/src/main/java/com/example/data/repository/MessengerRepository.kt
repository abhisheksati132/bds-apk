package com.example.data.repository

import com.example.data.crypto.CryptoHelper
import com.example.data.local.MessengerDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessengerRepository(private val dao: MessengerDao) {

    val activeConversations: Flow<List<ConversationEntity>> = dao.getActiveConversations()
    val allContacts: Flow<List<ContactEntity>> = dao.getAllContacts()
    val allStatuses: Flow<List<StatusEntity>> = dao.getAllStatuses()
    val allCalls: Flow<List<CallEntity>> = dao.getAllCalls()

    fun getConversation(id: Long): Flow<ConversationEntity?> = dao.getConversationById(id)

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> =
        dao.getMessagesForConversation(conversationId).map { list ->
            // In a real encrypted pipeline, cipherText is decrypted to text
            list.map { msg ->
                if (msg.text.isEmpty() && msg.cipherText.isNotEmpty()) {
                    msg.copy(text = CryptoHelper.decrypt(msg.cipherText))
                } else {
                    msg
                }
            }
        }

    suspend fun sendMessage(
        conversationId: Long,
        text: String,
        isDisappearing: Boolean = false,
        disappearingTimerSeconds: Long = 0,
        replyToId: Long? = null,
        replyToText: String? = null,
        type: MessageType = MessageType.TEXT,
        voiceDurationSeconds: Int = 0
    ) {
        val now = System.currentTimeMillis()
        val expiresAt = if (isDisappearing && disappearingTimerSeconds > 0) {
            now + (disappearingTimerSeconds * 1000L)
        } else null

        val cipher = CryptoHelper.encrypt(text)

        val message = MessageEntity(
            conversationId = conversationId,
            senderId = "me",
            text = text,
            cipherText = cipher,
            timestamp = now,
            isMe = true,
            status = MessageStatus.SENT,
            type = type,
            voiceDurationSeconds = voiceDurationSeconds,
            isDisappearing = isDisappearing,
            expiresAtTimestamp = expiresAt,
            replyToId = replyToId,
            replyToText = replyToText
        )

        dao.insertMessage(message)

        // Update conversation last message & timestamp
        val preview = if (type == MessageType.VOICE) "🎤 Voice message (${voiceDurationSeconds}s)" else text
        // Update conversation summary
        dao.getConversationById(conversationId).collect { conv ->
            if (conv != null) {
                dao.updateConversation(
                    conv.copy(
                        lastMessage = preview,
                        lastTimestamp = now
                    )
                )
            }
            return@collect
        }
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
            lastMessage = "Started a new encrypted conversation",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            isOnline = true,
            isEncrypted = true,
            keyFingerprint = CryptoHelper.generateFingerprint(contact.handle)
        )
        return dao.insertConversation(newConv)
    }

    suspend fun markConversationRead(conversationId: Long) {
        dao.markConversationAsRead(conversationId)
    }

    suspend fun setDisappearingTimer(conversationId: Long, timerSeconds: Long) {
        dao.updateDisappearingTimer(conversationId, timerSeconds)
    }

    suspend fun deleteConversation(conversationId: Long) {
        dao.deleteAllMessagesInConversation(conversationId)
        dao.deleteConversation(conversationId)
    }

    suspend fun clearChatMessages(conversationId: Long) {
        dao.deleteAllMessagesInConversation(conversationId)
    }

    suspend fun purgeExpiredMessages() {
        dao.purgeExpiredMessages(System.currentTimeMillis())
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
    }
}
