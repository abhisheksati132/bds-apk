package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessengerDao {

    // Conversations
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, lastTimestamp DESC")
    fun getActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun getConversationById(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE peerHandle = :handle LIMIT 1")
    suspend fun getConversationByHandle(handle: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :conversationId")
    suspend fun markConversationAsRead(conversationId: Long)

    @Query("UPDATE conversations SET disappearingTimerSeconds = :timerSeconds WHERE id = :conversationId")
    suspend fun updateDisappearingTimer(conversationId: Long, timerSeconds: Long)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateConversationPinned(id: Long, isPinned: Boolean)

    @Query("UPDATE conversations SET isArchived = :isArchived WHERE id = :id")
    suspend fun updateConversationArchived(id: Long, isArchived: Boolean)

    @Query("UPDATE conversations SET unreadCount = :unreadCount WHERE id = :id")
    suspend fun updateConversationUnreadCount(id: Long, unreadCount: Int)

    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY lastTimestamp DESC")
    fun getArchivedConversations(): Flow<List<ConversationEntity>>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    // Messages
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :id")
    suspend fun updateMessageReaction(id: Long, reaction: String?)

    @Query("UPDATE messages SET reaction = :reaction WHERE cloudMsgDocId = :cloudDocId")
    suspend fun updateMessageReactionByCloudDocId(cloudDocId: String, reaction: String?)

    @Query("UPDATE messages SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateMessagePinned(id: Long, isPinned: Boolean)

    @Query("UPDATE messages SET isPinned = :isPinned WHERE cloudMsgDocId = :cloudDocId")
    suspend fun updateMessagePinnedByCloudDocId(cloudDocId: String, isPinned: Boolean)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isPinned = 1 ORDER BY timestamp DESC")
    fun getPinnedMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET text = :newText, cipherText = :newCipherText, isEdited = 1 WHERE id = :id")
    suspend fun updateMessageText(id: Long, newText: String, newCipherText: String = "")

    @Query("UPDATE messages SET text = :newText, cipherText = :newCipherText, isEdited = 1 WHERE cloudMsgDocId = :cloudDocId")
    suspend fun updateMessageTextByCloudDocId(cloudDocId: String, newText: String, newCipherText: String = "")

    @Query("SELECT * FROM messages WHERE cloudMsgDocId = :cloudDocId LIMIT 1")
    suspend fun getMessageByCloudDocId(cloudDocId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllMessagesInConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE expiresAtTimestamp IS NOT NULL AND expiresAtTimestamp <= :now")
    suspend fun purgeExpiredMessages(now: Long)

    // Contacts
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isBlocked = 1")
    fun getBlockedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT isBlocked FROM contacts WHERE handle = :handle LIMIT 1")
    suspend fun isContactBlocked(handle: String): Boolean?

    @Query("UPDATE contacts SET isBlocked = :isBlocked WHERE handle = :handle")
    suspend fun setContactBlocked(handle: String, isBlocked: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE handle = :handle")
    suspend fun deleteContact(handle: String)

    // Statuses
    @Query("SELECT * FROM statuses ORDER BY timestamp DESC")
    fun getAllStatuses(): Flow<List<StatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: StatusEntity): Long

    // Calls
    @Query("SELECT * FROM calls ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity): Long

    @Query("DELETE FROM calls WHERE id = :id")
    suspend fun deleteCall(id: Long)

    @Query("DELETE FROM calls")
    suspend fun clearCallLogs()

    // Global Wipe / Panic
    @Query("DELETE FROM conversations")
    suspend fun wipeConversations()

    @Query("DELETE FROM messages")
    suspend fun wipeMessages()

    @Query("DELETE FROM calls")
    suspend fun wipeCalls()

    @Query("DELETE FROM statuses")
    suspend fun wipeStatuses()

    @Query("DELETE FROM contacts")
    suspend fun wipeContacts()
}
