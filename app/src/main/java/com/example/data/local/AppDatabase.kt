package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContactEntity::class,
        StatusEntity::class,
        CallEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messengerDao(): MessengerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clean_messenger_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database.messengerDao())
                    }
                }
            }

            private suspend fun populateInitialData(dao: MessengerDao) {
                val now = System.currentTimeMillis()
                val oneMinute = 60 * 1000L
                val oneHour = 60 * oneMinute
                val oneDay = 24 * oneHour

                // 1. Julianne Deff
                val c1Id = dao.insertConversation(
                    ConversationEntity(
                        peerId = "u_julianne",
                        peerName = "Julianne Deff",
                        peerHandle = "julianne.7492",
                        avatarBgColorHex = "#DDE1FF",
                        avatarTextColorHex = "#001453",
                        lastMessage = "I'll check the server logs right now...",
                        lastTimestamp = now - 15 * oneMinute,
                        unreadCount = 0,
                        isOnline = true,
                        isPinned = true,
                        isEncrypted = true,
                        keyFingerprint = "7F:92:B8:31:4A:E0",
                        disappearingTimerSeconds = 30
                    )
                )

                // 2. Marcus King
                val c2Id = dao.insertConversation(
                    ConversationEntity(
                        peerId = "u_marcus",
                        peerName = "Marcus King",
                        peerHandle = "marcus.9182",
                        avatarBgColorHex = "#EADDFF",
                        avatarTextColorHex = "#21005D",
                        lastMessage = "Wait, did you see the update?",
                        lastTimestamp = now - 1 * oneDay + 2 * oneHour,
                        unreadCount = 2,
                        isOnline = false,
                        isPinned = false,
                        isEncrypted = true,
                        keyFingerprint = "C4:A1:09:82:DF:3B",
                        disappearingTimerSeconds = 0
                    )
                )

                // 3. Sarah Riley
                val c3Id = dao.insertConversation(
                    ConversationEntity(
                        peerId = "u_sarah",
                        peerName = "Sarah Riley",
                        peerHandle = "sarah.1044",
                        avatarBgColorHex = "#FDE0FF",
                        avatarTextColorHex = "#3B004D",
                        lastMessage = "The prototype is ready for review.",
                        lastTimestamp = now - 2 * oneDay,
                        unreadCount = 0,
                        isOnline = true,
                        isPinned = false,
                        isEncrypted = true,
                        keyFingerprint = "18:EE:55:7B:A2:CC"
                    )
                )

                // 4. Tech Meetup
                val c4Id = dao.insertConversation(
                    ConversationEntity(
                        peerId = "g_tech_meetup",
                        peerName = "Tech Meetup",
                        peerHandle = "group.techmeetup",
                        avatarBgColorHex = "#D1E4FF",
                        avatarTextColorHex = "#001D36",
                        lastMessage = "Ben: We're meeting at 6PM sharp.",
                        lastTimestamp = now - 3 * oneDay,
                        unreadCount = 0,
                        isOnline = false,
                        isGroup = true,
                        isPinned = false,
                        isEncrypted = true,
                        keyFingerprint = "90:4B:D1:6C:3E:11"
                    )
                )

                // 5. Alex
                val c5Id = dao.insertConversation(
                    ConversationEntity(
                        peerId = "u_alex",
                        peerName = "Alex",
                        peerHandle = "alex.8821",
                        avatarBgColorHex = "#E2E2E9",
                        avatarTextColorHex = "#44474E",
                        lastMessage = "Can you send that file again?",
                        lastTimestamp = now - 4 * oneDay,
                        unreadCount = 0,
                        isOnline = false,
                        isPinned = false,
                        isEncrypted = true,
                        keyFingerprint = "5D:12:80:FF:99:A4"
                    )
                )

                // Populate Messages for Julianne
                dao.insertMessage(
                    MessageEntity(
                        conversationId = c1Id,
                        senderId = "u_julianne",
                        text = "Hey! Did you deploy the latest encrypted protocol update?",
                        timestamp = now - 45 * oneMinute,
                        isMe = false,
                        status = MessageStatus.READ
                    )
                )
                dao.insertMessage(
                    MessageEntity(
                        conversationId = c1Id,
                        senderId = "me",
                        text = "Yes, all endpoints are verified and keys rotated.",
                        timestamp = now - 30 * oneMinute,
                        isMe = true,
                        status = MessageStatus.READ
                    )
                )
                dao.insertMessage(
                    MessageEntity(
                        conversationId = c1Id,
                        senderId = "u_julianne",
                        text = "I'll check the server logs right now...",
                        timestamp = now - 15 * oneMinute,
                        isMe = false,
                        status = MessageStatus.READ,
                        reaction = "🔒"
                    )
                )

                // Populate Messages for Marcus
                dao.insertMessage(
                    MessageEntity(
                        conversationId = c2Id,
                        senderId = "me",
                        text = "Marcus, check out the new design spec when you get a chance.",
                        timestamp = now - 1 * oneDay + 4 * oneHour,
                        isMe = true,
                        status = MessageStatus.READ
                    )
                )
                dao.insertMessage(
                    MessageEntity(
                        conversationId = c2Id,
                        senderId = "u_marcus",
                        text = "Taking a look at it now.",
                        timestamp = now - 1 * oneDay + 3 * oneHour,
                        isMe = false,
                        status = MessageStatus.READ
                    )
                )
                dao.insertMessage(
                    MessageEntity(
                        conversationId = c2Id,
                        senderId = "u_marcus",
                        text = "Wait, did you see the update?",
                        timestamp = now - 1 * oneDay + 2 * oneHour,
                        isMe = false,
                        status = MessageStatus.DELIVERED
                    )
                )

                // Populate Messages for Sarah
                dao.insertMessage(
                    MessageEntity(
                        conversationId = c3Id,
                        senderId = "u_sarah",
                        text = "The prototype is ready for review.",
                        timestamp = now - 2 * oneDay,
                        isMe = false,
                        status = MessageStatus.READ
                    )
                )

                // Populate Contacts
                dao.insertContact(
                    ContactEntity(
                        handle = "julianne.7492",
                        name = "Julianne Deff",
                        avatarBgHex = "#DDE1FF",
                        avatarTextHex = "#001453",
                        publicKey = "ECDH-P256: 7F92B8314AE0D184",
                        isVerified = true,
                        about = "Zero-trust privacy advocate"
                    )
                )
                dao.insertContact(
                    ContactEntity(
                        handle = "marcus.9182",
                        name = "Marcus King",
                        avatarBgHex = "#EADDFF",
                        avatarTextHex = "#21005D",
                        publicKey = "ECDH-P256: C4A10982DF3BA991",
                        isVerified = true,
                        about = "Building next-gen systems"
                    )
                )
                dao.insertContact(
                    ContactEntity(
                        handle = "sarah.1044",
                        name = "Sarah Riley",
                        avatarBgHex = "#FDE0FF",
                        avatarTextHex = "#3B004D",
                        publicKey = "ECDH-P256: 18EE557BA2CC8412",
                        isVerified = true,
                        about = "Design & Minimalism"
                    )
                )
                dao.insertContact(
                    ContactEntity(
                        handle = "alex.8821",
                        name = "Alex",
                        avatarBgHex = "#E2E2E9",
                        avatarTextHex = "#44474E",
                        publicKey = "ECDH-P256: 5D1280FF99A46603",
                        isVerified = false,
                        about = "Away from keyboard"
                    )
                )

                // Populate Statuses
                dao.insertStatus(
                    StatusEntity(
                        authorName = "Julianne Deff",
                        authorHandle = "julianne.7492",
                        avatarBgHex = "#DDE1FF",
                        avatarTextHex = "#001453",
                        caption = "Key rotation completed successfully. 🔐",
                        timestamp = now - 2 * oneHour,
                        isViewed = false
                    )
                )
                dao.insertStatus(
                    StatusEntity(
                        authorName = "Sarah Riley",
                        authorHandle = "sarah.1044",
                        avatarBgHex = "#FDE0FF",
                        avatarTextHex = "#3B004D",
                        caption = "Exploring minimalist typography in Jetpack Compose ✨",
                        timestamp = now - 5 * oneHour,
                        isViewed = true
                    )
                )

                // Populate Calls
                dao.insertCall(
                    CallEntity(
                        peerName = "Julianne Deff",
                        peerHandle = "julianne.7492",
                        avatarBgHex = "#DDE1FF",
                        avatarTextHex = "#001453",
                        timestamp = now - 3 * oneHour,
                        isIncoming = true,
                        isMissed = false,
                        isVideo = false,
                        durationSeconds = 245
                    )
                )
                dao.insertCall(
                    CallEntity(
                        peerName = "Marcus King",
                        peerHandle = "marcus.9182",
                        avatarBgHex = "#EADDFF",
                        avatarTextHex = "#21005D",
                        timestamp = now - 1 * oneDay,
                        isIncoming = false,
                        isMissed = false,
                        isVideo = true,
                        durationSeconds = 512
                    )
                )
                dao.insertCall(
                    CallEntity(
                        peerName = "Alex",
                        peerHandle = "alex.8821",
                        avatarBgHex = "#E2E2E9",
                        avatarTextHex = "#44474E",
                        timestamp = now - 2 * oneDay,
                        isIncoming = true,
                        isMissed = true,
                        isVideo = false,
                        durationSeconds = 0
                    )
                )
            }
        }
    }
}
