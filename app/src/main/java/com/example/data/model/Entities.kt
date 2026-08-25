package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE,
    DOCUMENT,
    SYSTEM
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val peerId: String,
    val peerName: String,
    val peerHandle: String,
    val avatarBgColorHex: String,
    val avatarTextColorHex: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isGroup: Boolean = false,
    val groupMembers: String = "", // comma-separated handles for group chats
    val groupAdminHandle: String = "",
    val disappearingTimerSeconds: Long = 0, // 0 = disabled, 30 = 30s, 86400 = 24h
    val isEncrypted: Boolean = true,
    val keyFingerprint: String = "8A:F2:1C:99:B4:63",
    val cloudDocId: String? = null,
    val avatarUrl: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val senderId: String,
    val senderName: String? = null,
    val text: String,
    val cipherText: String = "",
    val timestamp: Long,
    val isMe: Boolean,
    val status: MessageStatus = MessageStatus.READ,
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String? = null,
    val voiceDurationSeconds: Int = 0,
    val isDisappearing: Boolean = false,
    val expiresAtTimestamp: Long? = null,
    val replyToId: Long? = null,
    val replyToText: String? = null,
    val reaction: String? = null,
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val fileName: String? = null,
    val fileSizeBytes: Long = 0L,
    val cloudMsgDocId: String? = null
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val handle: String,
    val name: String,
    val avatarBgHex: String,
    val avatarTextHex: String,
    val publicKey: String,
    val isVerified: Boolean = true,
    val about: String = "Available",
    val isBlocked: Boolean = false,
    val avatarUrl: String? = null
)

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val authorName: String,
    val authorHandle: String,
    val avatarBgHex: String,
    val avatarTextHex: String,
    val caption: String,
    val timestamp: Long,
    val isMe: Boolean = false,
    val isViewed: Boolean = false
)

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val peerName: String,
    val peerHandle: String,
    val avatarBgHex: String,
    val avatarTextHex: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val isMissed: Boolean,
    val isVideo: Boolean = false,
    val durationSeconds: Int = 0
)

data class AuthUser(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false
)
