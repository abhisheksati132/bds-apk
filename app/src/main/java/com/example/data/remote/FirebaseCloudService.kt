package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.crypto.CryptoHelper
import com.example.data.local.MessengerDao
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class CloudStatus {
    object Checking : CloudStatus()
    data class Connected(val myHandle: String) : CloudStatus()
    data class StandaloneLocal(val reason: String) : CloudStatus()
}

data class CloudUser(
    val handle: String = "",
    val displayName: String = "",
    val publicKey: String = "",
    val avatarBgHex: String = "#DDE1FF",
    val avatarTextHex: String = "#001453",
    val avatarUrl: String = "",
    val about: String = "Available",
    val isOnline: Boolean = false,
    val fcmToken: String = ""
)

class FirebaseCloudService(
    private val context: Context,
    private val dao: MessengerDao,
    private val scope: CoroutineScope
) {
    private val TAG = "FirebaseCloudService"
    private var firestore: FirebaseFirestore? = null
    private var realtimeDb: FirebaseDatabase? = null
    private var storage: FirebaseStorage? = null

    private var incomingMessageListener: ListenerRegistration? = null
    private var conversationStreamListener: ListenerRegistration? = null
    private var incomingCallListener: ListenerRegistration? = null
    private var presenceStatusListener: ValueEventListener? = null
    private var connectionStateListener: ValueEventListener? = null

    private val _cloudStatus = MutableStateFlow<CloudStatus>(CloudStatus.Checking)
    val cloudStatus: StateFlow<CloudStatus> = _cloudStatus.asStateFlow()

    // Realtime Database Presence Map: handle -> isOnline (Boolean)
    private val _presenceMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val presenceMap: StateFlow<Map<String, Boolean>> = _presenceMap.asStateFlow()

    // Blocked users set for fast lookup
    private val _blockedUsers = MutableStateFlow<Set<String>>(emptySet())
    val blockedUsers: StateFlow<Set<String>> = _blockedUsers.asStateFlow()

    private var typingListener: ListenerRegistration? = null
    private var currentUserHandle: String = ""

    init {
        initFirebase()
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Eager FirebaseApp.initializeApp: ${e.message}")
                }
            }

            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firestore = FirebaseFirestore.getInstance()
                try {
                    realtimeDb = FirebaseDatabase.getInstance()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime Database initialization warning: ${e.message}")
                }
                try {
                    storage = FirebaseStorage.getInstance()
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase Storage initialization warning: ${e.message}")
                }

                Log.d(TAG, "Firebase initialized successfully.")
                syncFcmToken()
                startRealtimePresenceListener()
            } else {
                Log.w(TAG, "No Firebase App initialized; running in Local Encrypted Mode.")
                _cloudStatus.value = CloudStatus.StandaloneLocal("Offline/Local Encrypted Mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization exception: ${e.message}")
            _cloudStatus.value = CloudStatus.StandaloneLocal("Local Encrypted Mode (${e.localizedMessage ?: "Standalone"})")
        }
    }

    fun isCloudAvailable(): Boolean = firestore != null

    private fun syncFcmToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val token = task.result
                    Log.d(TAG, "Fetched FCM Token: $token")
                    val prefs = context.getSharedPreferences("vault_messenger_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("fcm_token", token).apply()

                    val savedHandle = prefs.getString("saved_handle", "") ?: ""
                    if (savedHandle.isNotBlank()) {
                        scope.launch {
                            updateUserFcmToken(savedHandle, token)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM Token fetch skipped or failed: ${e.message}")
        }
    }

    suspend fun updateUserFcmToken(handle: String, token: String) {
        val fs = firestore ?: return
        val clean = handle.lowercase().replace("@", "").trim()
        if (clean.isBlank()) return
        try {
            fs.collection("users").document(clean).update("fcmToken", token).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update FCM token for user $clean: ${e.message}")
        }
    }

    // ==========================================
    // 1. REALTIME DATABASE PRESENCE SYSTEM
    // ==========================================
    private fun setupRealtimePresence(handle: String) {
        val rdb = realtimeDb ?: return
        val cleanHandle = handle.lowercase().replace("@", "").trim()
        if (cleanHandle.isBlank()) return

        currentUserHandle = cleanHandle

        try {
            val connectedRef = rdb.getReference(".info/connected")
            val userStatusRef = rdb.getReference("status/$cleanHandle")

            connectionStateListener?.let { connectedRef.removeEventListener(it) }

            connectionStateListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        // When client disconnects unexpectedly, set to offline
                        val offlineState = mapOf(
                            "state" to "offline",
                            "last_changed" to ServerValue.TIMESTAMP,
                            "handle" to cleanHandle
                        )
                        userStatusRef.onDisconnect().setValue(offlineState)

                        // Set online state
                        val onlineState = mapOf(
                            "state" to "online",
                            "last_changed" to ServerValue.TIMESTAMP,
                            "handle" to cleanHandle
                        )
                        userStatusRef.setValue(onlineState)

                        // Update local presence map immediately
                        _presenceMap.value = _presenceMap.value.toMutableMap().apply {
                            put(cleanHandle, true)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Presence listener cancelled: ${error.message}")
                }
            }

            connectedRef.addValueEventListener(connectionStateListener!!)
        } catch (e: Exception) {
            Log.w(TAG, "Realtime Database presence setup error: ${e.message}")
        }
    }

    private fun startRealtimePresenceListener() {
        val rdb = realtimeDb ?: return
        try {
            val statusRef = rdb.getReference("status")
            presenceStatusListener?.let { statusRef.removeEventListener(it) }

            presenceStatusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val newMap = mutableMapOf<String, Boolean>()
                    for (child in snapshot.children) {
                        val handle = child.key ?: continue
                        val state = child.child("state").getValue(String::class.java)
                        val isOnline = state.equals("online", ignoreCase = true)
                        newMap[handle] = isOnline
                    }
                    _presenceMap.value = newMap
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Presence observer cancelled: ${error.message}")
                }
            }

            statusRef.addValueEventListener(presenceStatusListener!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start presence status observer: ${e.message}")
        }
    }

    fun setUserOffline() {
        val rdb = realtimeDb
        val clean = currentUserHandle
        if (rdb != null && clean.isNotBlank()) {
            try {
                val userStatusRef = rdb.getReference("status/$clean")
                userStatusRef.setValue(
                    mapOf(
                        "state" to "offline",
                        "last_changed" to ServerValue.TIMESTAMP,
                        "handle" to clean
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error setting user offline: ${e.message}")
            }
        }
    }

    // ==========================================
    // USER REGISTRATION & CLOUD DIRECTORY
    // ==========================================
    suspend fun registerUser(handle: String, displayName: String, publicKey: String): Boolean {
        val fs = firestore ?: return false
        val cleanHandle = handle.lowercase().replace("@", "").trim()
        if (cleanHandle.isEmpty()) return false

        return try {
            val prefs = context.getSharedPreferences("vault_messenger_prefs", Context.MODE_PRIVATE)
            val fcmToken = prefs.getString("fcm_token", "") ?: ""

            val userMap = hashMapOf(
                "handle" to cleanHandle,
                "displayName" to displayName,
                "publicKey" to publicKey,
                "avatarBgHex" to "#DDE1FF",
                "avatarTextHex" to "#001453",
                "about" to "Hey there! I am using itas.",
                "lastActive" to FieldValue.serverTimestamp(),
                "isOnline" to true,
                "fcmToken" to fcmToken
            )
            fs.collection("users").document(cleanHandle)
                .set(userMap, SetOptions.merge())
                .await()

            _cloudStatus.value = CloudStatus.Connected(cleanHandle)
            setupRealtimePresence(cleanHandle)
            startIncomingMessageListener(cleanHandle)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register user in cloud: ${e.message}")
            false
        }
    }

    // ==========================================
    // 2. INCOMING MESSAGES & GROUP LISTENER
    // ==========================================
    fun startIncomingMessageListener(myHandle: String) {
        val fs = firestore ?: return
        val cleanHandle = myHandle.lowercase().replace("@", "").trim()
        if (cleanHandle.isEmpty()) return

        incomingMessageListener?.remove()

        try {
            incomingMessageListener = fs.collection("messages")
                .whereArrayContains("targetRecipients", cleanHandle)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen failed: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        for (doc in snapshots.documents) {
                            val senderHandle = doc.getString("senderHandle") ?: continue
                            if (senderHandle == cleanHandle) continue // Ignore own messages

                            // If sender is blocked, ignore incoming message
                            if (_blockedUsers.value.contains(senderHandle.lowercase().trim())) {
                                Log.d(TAG, "Dropped message from blocked user: $senderHandle")
                                continue
                            }

                            val cipherText = doc.getString("cipherText") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val msgType = doc.getString("type") ?: MessageType.TEXT.name
                            val mediaUrl = doc.getString("mediaUrl")
                            val voiceDuration = doc.getLong("voiceDuration")?.toInt() ?: 0
                            val disappearingSeconds = doc.getLong("disappearingSeconds") ?: 0L
                            val expiresAt = doc.getLong("expiresAtTimestamp")
                            val replyToText = doc.getString("replyToText")
                            val isGroup = doc.getBoolean("isGroup") ?: false
                            val groupId = doc.getString("groupId")
                            val groupName = doc.getString("groupName")
                            val groupMembers = (doc.get("groupMembers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            val reaction = doc.getString("reaction")
                            val isPinned = doc.getBoolean("isPinned") ?: false
                            val isEdited = doc.getBoolean("isEdited") ?: false
                            val fileName = doc.getString("fileName")
                            val fileSizeBytes = doc.getLong("fileSizeBytes") ?: 0L
                            val docId = doc.id

                            // Process message in background
                            scope.launch(Dispatchers.IO) {
                                handleReceivedCloudMessage(
                                    senderHandle = senderHandle,
                                    cipherText = cipherText,
                                    timestamp = timestamp,
                                    type = try { MessageType.valueOf(msgType) } catch (e: Exception) { MessageType.TEXT },
                                    mediaUrl = mediaUrl,
                                    voiceDuration = voiceDuration,
                                    disappearingSeconds = disappearingSeconds,
                                    expiresAt = expiresAt,
                                    replyToText = replyToText,
                                    isGroup = isGroup,
                                    groupId = groupId,
                                    groupName = groupName,
                                    groupMembers = groupMembers,
                                    reaction = reaction,
                                    isPinned = isPinned,
                                    isEdited = isEdited,
                                    fileName = fileName,
                                    fileSizeBytes = fileSizeBytes,
                                    cloudDocId = docId
                                )

                                // Update status in Firestore to DELIVERED
                                try {
                                    fs.collection("messages").document(docId).update("status", "DELIVERED").await()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not update status to DELIVERED: ${e.message}")
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting snapshot listener: ${e.message}")
        }
    }

    private suspend fun handleReceivedCloudMessage(
        senderHandle: String,
        cipherText: String,
        timestamp: Long,
        type: MessageType,
        mediaUrl: String?,
        voiceDuration: Int,
        disappearingSeconds: Long,
        expiresAt: Long?,
        replyToText: String?,
        isGroup: Boolean = false,
        groupId: String? = null,
        groupName: String? = null,
        groupMembers: List<String> = emptyList(),
        reaction: String? = null,
        isPinned: Boolean = false,
        isEdited: Boolean = false,
        fileName: String? = null,
        fileSizeBytes: Long = 0L,
        cloudDocId: String? = null
    ) {
        val decryptedText = if (type == MessageType.TEXT && cipherText.isNotEmpty()) CryptoHelper.decrypt(cipherText) else cipherText

        if (cloudDocId != null) {
            val existing = dao.getMessageByCloudDocId(cloudDocId)
            if (existing != null) {
                if (existing.reaction != reaction) {
                    dao.updateMessageReaction(existing.id, reaction)
                }
                if (existing.isPinned != isPinned) {
                    dao.updateMessagePinned(existing.id, isPinned)
                }
                if (existing.isEdited != isEdited || (isEdited && existing.cipherText != cipherText)) {
                    dao.updateMessageText(existing.id, decryptedText, cipherText)
                }
                return
            }
        }

        val conversationKey = if (isGroup && groupId != null) groupId else senderHandle
        var conv = dao.getConversationByHandle(conversationKey)
        val conversationId: Long

        val preview = when (type) {
            MessageType.IMAGE -> "📷 Photo attachment"
            MessageType.VOICE -> "🎤 Voice note (${voiceDuration}s)"
            MessageType.DOCUMENT -> "📁 ${fileName ?: "Document"}"
            else -> if (cipherText.isNotEmpty()) decryptedText else "Encrypted message"
        }

        if (conv == null) {
            val displayName = if (isGroup) groupName ?: "Group Chat" else "@$senderHandle"
            val newConv = ConversationEntity(
                peerId = if (isGroup) "grp_$conversationKey" else "u_${senderHandle.replace(".", "_")}",
                peerName = displayName,
                peerHandle = conversationKey,
                avatarBgColorHex = if (isGroup) "#E8DEF8" else "#EADDFF",
                avatarTextColorHex = if (isGroup) "#1D192B" else "#21005D",
                lastMessage = preview,
                lastTimestamp = timestamp,
                unreadCount = 1,
                isOnline = _presenceMap.value[senderHandle] ?: false,
                isGroup = isGroup,
                groupMembers = groupMembers.joinToString(","),
                isEncrypted = true,
                keyFingerprint = CryptoHelper.generateFingerprint(conversationKey),
                disappearingTimerSeconds = disappearingSeconds,
                cloudDocId = groupId
            )
            conversationId = dao.insertConversation(newConv)
        } else {
            conversationId = conv.id
            dao.updateConversation(
                conv.copy(
                    lastMessage = preview,
                    lastTimestamp = timestamp,
                    unreadCount = conv.unreadCount + 1,
                    disappearingTimerSeconds = if (disappearingSeconds > 0) disappearingSeconds else conv.disappearingTimerSeconds
                )
            )
        }

        val computedExpiresAt = expiresAt ?: if (disappearingSeconds > 0) timestamp + (disappearingSeconds * 1000L) else null

        val message = MessageEntity(
            conversationId = conversationId,
            senderId = senderHandle,
            senderName = "@$senderHandle",
            text = if (type == MessageType.DOCUMENT) (fileName ?: "Document") else decryptedText,
            cipherText = cipherText,
            timestamp = timestamp,
            isMe = false,
            status = MessageStatus.READ,
            type = type,
            mediaUrl = mediaUrl,
            voiceDurationSeconds = voiceDuration,
            isDisappearing = disappearingSeconds > 0,
            expiresAtTimestamp = computedExpiresAt,
            replyToText = replyToText,
            reaction = reaction,
            isPinned = isPinned,
            isEdited = isEdited,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            cloudMsgDocId = cloudDocId
        )

        dao.insertMessage(message)
    }

    // ==========================================
    // 3. SEND MESSAGE & GROUP MESSAGE
    // ==========================================
    suspend fun sendCloudMessage(
        senderHandle: String,
        recipientHandle: String,
        cipherText: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        voiceDurationSeconds: Int = 0,
        disappearingTimerSeconds: Long = 0,
        replyToText: String? = null,
        isGroup: Boolean = false,
        groupId: String? = null,
        groupName: String? = null,
        groupMembers: List<String> = emptyList(),
        fileName: String? = null,
        fileSizeBytes: Long = 0L,
        isPinned: Boolean = false,
        isEdited: Boolean = false
    ): String? {
        val fs = firestore ?: return null
        val cleanSender = senderHandle.lowercase().replace("@", "").trim()
        val cleanRecipient = recipientHandle.lowercase().replace("@", "").trim()

        if (cleanSender.isEmpty()) return null

        val now = System.currentTimeMillis()
        val expiresAt = if (disappearingTimerSeconds > 0) now + (disappearingTimerSeconds * 1000L) else null

        val targetRecipients = if (isGroup) {
            groupMembers.map { it.lowercase().replace("@", "").trim() }.filter { it.isNotEmpty() }
        } else {
            listOf(cleanRecipient, cleanSender)
        }

        return try {
            val payload = hashMapOf(
                "senderHandle" to cleanSender,
                "recipientHandle" to cleanRecipient,
                "targetRecipients" to targetRecipients,
                "cipherText" to cipherText,
                "timestamp" to now,
                "type" to type.name,
                "mediaUrl" to (mediaUrl ?: ""),
                "voiceDuration" to voiceDurationSeconds,
                "disappearingSeconds" to disappearingTimerSeconds,
                "expiresAtTimestamp" to expiresAt,
                "replyToText" to (replyToText ?: ""),
                "isGroup" to isGroup,
                "groupId" to (groupId ?: ""),
                "groupName" to (groupName ?: ""),
                "groupMembers" to groupMembers,
                "fileName" to (fileName ?: ""),
                "fileSizeBytes" to fileSizeBytes,
                "isPinned" to isPinned,
                "isEdited" to isEdited,
                "status" to "SENT"
            )

            val docRef = fs.collection("messages").add(payload).await()
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to relay cloud message: ${e.message}")
            null
        }
    }

    // ==========================================
    // 4. READ RECEIPTS (SEEN / DELIVERED)
    // ==========================================
    suspend fun markMessagesAsReadInCloud(peerHandle: String, myHandle: String) {
        val fs = firestore ?: return
        val cleanMe = myHandle.lowercase().replace("@", "").trim()
        val cleanPeer = peerHandle.lowercase().replace("@", "").trim()
        if (cleanMe.isEmpty() || cleanPeer.isEmpty()) return

        try {
            // Fetch unread messages from this peer directed to me
            val query = fs.collection("messages")
                .whereEqualTo("senderHandle", cleanPeer)
                .whereArrayContains("targetRecipients", cleanMe)
                .limit(50)
                .get()
                .await()

            val unreadDocs = query.documents.filter { it.getString("status") != "READ" }
            if (unreadDocs.isNotEmpty()) {
                val batch = fs.batch()
                for (doc in unreadDocs) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "status" to "READ",
                            "readTimestamp" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
                Log.d(TAG, "Successfully marked ${unreadDocs.size} messages as READ in cloud from $cleanPeer")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Mark read in cloud: ${e.message}")
        }
    }

    // ==========================================
    // 5. CLEAR CHAT (REMOTE DELETION)
    // ==========================================
    suspend fun clearChatInCloud(
        myHandle: String,
        peerHandle: String,
        isGroup: Boolean = false,
        groupId: String? = null
    ) {
        val fs = firestore ?: return
        val cleanMe = myHandle.lowercase().replace("@", "").trim()
        val cleanPeer = peerHandle.lowercase().replace("@", "").trim()

        try {
            if (isGroup && !groupId.isNullOrBlank()) {
                val groupQuery = fs.collection("messages")
                    .whereEqualTo("groupId", groupId)
                    .limit(100)
                    .get()
                    .await()
                for (doc in groupQuery.documents) {
                    doc.reference.delete().await()
                }
            } else {
                val directQuery1 = fs.collection("messages")
                    .whereEqualTo("senderHandle", cleanMe)
                    .whereEqualTo("recipientHandle", cleanPeer)
                    .limit(100)
                    .get()
                    .await()
                for (doc in directQuery1.documents) {
                    doc.reference.delete().await()
                }

                val directQuery2 = fs.collection("messages")
                    .whereEqualTo("senderHandle", cleanPeer)
                    .whereEqualTo("recipientHandle", cleanMe)
                    .limit(100)
                    .get()
                    .await()
                for (doc in directQuery2.documents) {
                    doc.reference.delete().await()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cloud messages: ${e.message}")
        }
    }

    // ==========================================
    // 6. REAL-TIME TYPING INDICATORS
    // ==========================================
    suspend fun setTypingStatus(threadKey: String, userHandle: String, isTyping: Boolean) {
        val fs = firestore ?: return
        val cleanThread = threadKey.lowercase().replace("@", "").trim()
        val cleanUser = userHandle.lowercase().replace("@", "").trim()
        if (cleanThread.isEmpty() || cleanUser.isEmpty()) return

        try {
            val docRef = fs.collection("chat_threads").document(cleanThread)
            val updatePayload = mapOf(
                "typing.$cleanUser" to isTyping,
                "lastTypingTimestamp" to FieldValue.serverTimestamp()
            )
            docRef.set(updatePayload, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to update typing status: ${e.message}")
        }
    }

    fun observeTypingStatus(
        threadKey: String,
        peerHandle: String,
        onTypingChanged: (Boolean) -> Unit
    ): ListenerRegistration? {
        val fs = firestore ?: return null
        val cleanThread = threadKey.lowercase().replace("@", "").trim()
        val cleanPeer = peerHandle.lowercase().replace("@", "").trim()
        if (cleanThread.isEmpty() || cleanPeer.isEmpty()) return null

        typingListener?.remove()
        try {
            typingListener = fs.collection("chat_threads").document(cleanThread)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Typing indicator listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val typingMap = snapshot.get("typing") as? Map<*, *>
                        val isTyping = typingMap?.get(cleanPeer) as? Boolean ?: false
                        onTypingChanged(isTyping)
                    } else {
                        onTypingChanged(false)
                    }
                }
            return typingListener
        } catch (e: Exception) {
            Log.e(TAG, "Failed to observe typing status: ${e.message}")
            return null
        }
    }

    // ==========================================
    // 7. BLOCK / UNBLOCK USERS
    // ==========================================
    suspend fun blockUser(myHandle: String, peerHandle: String) {
        val fs = firestore ?: return
        val cleanMe = myHandle.lowercase().replace("@", "").trim()
        val cleanPeer = peerHandle.lowercase().replace("@", "").trim()
        if (cleanMe.isEmpty() || cleanPeer.isEmpty()) return

        try {
            // Update Firestore user profile
            fs.collection("users").document(cleanMe).update(
                "blockedUsers", FieldValue.arrayUnion(cleanPeer)
            ).await()

            _blockedUsers.value = _blockedUsers.value + cleanPeer
            dao.setContactBlocked(cleanPeer, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to block user in cloud: ${e.message}")
            _blockedUsers.value = _blockedUsers.value + cleanPeer
            dao.setContactBlocked(cleanPeer, true)
        }
    }

    suspend fun unblockUser(myHandle: String, peerHandle: String) {
        val fs = firestore ?: return
        val cleanMe = myHandle.lowercase().replace("@", "").trim()
        val cleanPeer = peerHandle.lowercase().replace("@", "").trim()
        if (cleanMe.isEmpty() || cleanPeer.isEmpty()) return

        try {
            // Update Firestore user profile
            fs.collection("users").document(cleanMe).update(
                "blockedUsers", FieldValue.arrayRemove(cleanPeer)
            ).await()

            _blockedUsers.value = _blockedUsers.value - cleanPeer
            dao.setContactBlocked(cleanPeer, false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unblock user in cloud: ${e.message}")
            _blockedUsers.value = _blockedUsers.value - cleanPeer
            dao.setContactBlocked(cleanPeer, false)
        }
    }

    suspend fun syncBlockedUsers(myHandle: String) {
        val fs = firestore ?: return
        val cleanMe = myHandle.lowercase().replace("@", "").trim()
        if (cleanMe.isEmpty()) return

        try {
            val doc = fs.collection("users").document(cleanMe).get().await()
            if (doc.exists()) {
                val list = (doc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val set = list.map { it.lowercase().trim() }.toSet()
                _blockedUsers.value = set
                for (b in set) {
                    dao.setContactBlocked(b, true)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to sync blocked users: ${e.message}")
        }
    }

    // ==========================================
    // 8. EMOJI REACTIONS IN FIRESTORE
    // ==========================================
    suspend fun updateMessageReactionInCloud(cloudDocId: String, reaction: String?) {
        val fs = firestore ?: return
        if (cloudDocId.isBlank()) return

        try {
            fs.collection("messages").document(cloudDocId).update(
                "reaction", reaction ?: ""
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update reaction in cloud: ${e.message}")
        }
    }

    suspend fun deleteMessageFromCloud(cloudDocId: String) {
        val fs = firestore ?: return
        if (cloudDocId.isBlank()) return
        try {
            fs.collection("messages").document(cloudDocId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete message from cloud: ${e.message}")
        }
    }

    // ==========================================
    // 9. GROUP CHAT CREATION IN FIRESTORE
    // ==========================================
    suspend fun createGroupInCloud(
        groupName: String,
        memberHandles: List<String>,
        adminHandle: String
    ): String? {
        val fs = firestore ?: return null
        val cleanAdmin = adminHandle.lowercase().replace("@", "").trim()
        val cleanMembers = (memberHandles + cleanAdmin)
            .map { it.lowercase().replace("@", "").trim() }
            .distinct()
            .filter { it.isNotEmpty() }

        return try {
            val groupDoc = fs.collection("groups").document()
            val payload = hashMapOf(
                "groupId" to groupDoc.id,
                "name" to groupName,
                "adminHandle" to cleanAdmin,
                "members" to cleanMembers,
                "createdAt" to FieldValue.serverTimestamp(),
                "avatarBgHex" to "#E8DEF8",
                "avatarTextHex" to "#1D192B"
            )
            groupDoc.set(payload).await()
            groupDoc.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create group in cloud: ${e.message}")
            null
        }
    }

    // ==========================================
    // 6. FIREBASE STORAGE IMAGE UPLOAD
    // ==========================================
    suspend fun uploadChatImage(
        imageUri: Uri,
        conversationId: String
    ): String? {
        val compressedFile = com.example.util.ImageCompressorHelper.compressImage(context, imageUri)
        val fileToUpload = compressedFile ?: run {
            val fallback = File(context.filesDir, "chat_img_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(fallback).use { output -> input.copyTo(output) }
            }
            fallback
        }

        val st = storage
        if (st != null && fileToUpload.exists()) {
            try {
                val imageRef = st.reference.child("chat_images/$conversationId/${UUID.randomUUID()}.jpg")
                fileToUpload.inputStream().use { stream ->
                    imageRef.putStream(stream).await()
                }
                val downloadUrl = imageRef.downloadUrl.await().toString()
                try { compressedFile?.delete() } catch (_: Exception) {}
                return downloadUrl
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Storage upload failed: ${e.message}. Saving local copy.")
            }
        }

        // Offline / Local File fallback
        return try {
            Uri.fromFile(fileToUpload).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Local image save fallback error: ${e.message}")
            imageUri.toString()
        }
    }

    suspend fun uploadChatDocument(
        docUri: Uri,
        conversationId: String,
        fileName: String
    ): String? {
        val st = storage
        if (st != null) {
            try {
                val docRef = st.reference.child("chat_documents/$conversationId/${UUID.randomUUID()}_$fileName")
                context.contentResolver.openInputStream(docUri)?.use { stream ->
                    docRef.putStream(stream).await()
                }
                return docRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                Log.w(TAG, "Document upload failed: ${e.message}")
            }
        }
        return try {
            val localFile = File(context.filesDir, "doc_${UUID.randomUUID()}_$fileName")
            context.contentResolver.openInputStream(docUri)?.use { input ->
                FileOutputStream(localFile).use { output -> input.copyTo(output) }
            }
            Uri.fromFile(localFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Local document save fallback error: ${e.message}")
            docUri.toString()
        }
    }

    suspend fun editCloudMessage(cloudDocId: String, newCipherText: String) {
        val fs = firestore ?: return
        try {
            fs.collection("messages").document(cloudDocId).update(
                mapOf(
                    "cipherText" to newCipherText,
                    "isEdited" to true,
                    "editTimestamp" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to edit cloud message: ${e.message}")
        }
    }

    suspend fun pinCloudMessage(cloudDocId: String, isPinned: Boolean) {
        val fs = firestore ?: return
        try {
            fs.collection("messages").document(cloudDocId).update("isPinned", isPinned).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pin cloud message: ${e.message}")
        }
    }

    /**
     * Upload User Avatar image to Firebase Storage and return download URL.
     */
    suspend fun uploadUserAvatar(
        imageUri: Uri,
        handle: String
    ): String? {
        val cleanHandle = handle.lowercase().replace("@", "").trim()
        val st = storage
        if (st != null) {
            try {
                val avatarRef = st.reference.child("user_avatars/${cleanHandle}_${System.currentTimeMillis()}.jpg")
                val stream = context.contentResolver.openInputStream(imageUri)
                if (stream != null) {
                    avatarRef.putStream(stream).await()
                    val downloadUrl = avatarRef.downloadUrl.await().toString()
                    return downloadUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Storage avatar upload failed: ${e.message}. Using local file.")
            }
        }

        // Local fallback for offline mode
        return try {
            val localFile = File(context.filesDir, "avatar_${cleanHandle}.jpg")
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(localFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Local avatar save fallback error: ${e.message}")
            imageUri.toString()
        }
    }

    /**
     * Updates user's profile info (avatar, display name, status/about) in Firestore.
     */
    suspend fun updateUserProfile(
        handle: String,
        displayName: String,
        about: String,
        avatarUrl: String? = null,
        avatarBgHex: String? = null,
        avatarTextHex: String? = null
    ): Boolean {
        val fs = firestore ?: return false
        val clean = handle.lowercase().replace("@", "").trim()
        if (clean.isEmpty()) return false

        return try {
            val updates = hashMapOf<String, Any>(
                "displayName" to displayName,
                "about" to about,
                "lastActive" to FieldValue.serverTimestamp()
            )
            if (avatarUrl != null) {
                updates["avatarUrl"] = avatarUrl
            }
            if (avatarBgHex != null) {
                updates["avatarBgHex"] = avatarBgHex
            }
            if (avatarTextHex != null) {
                updates["avatarTextHex"] = avatarTextHex
            }

            fs.collection("users").document(clean)
                .set(updates, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile for $clean: ${e.message}")
            false
        }
    }

    /**
     * Upload real recorded voice note .m4a audio file to Firebase Storage
     */
    suspend fun uploadVoiceNote(
        file: File,
        conversationKey: String
    ): String? {
        val st = storage
        if (st != null && file.exists()) {
            try {
                val voiceRef = st.reference.child("voice_notes/${conversationKey}_${System.currentTimeMillis()}.m4a")
                voiceRef.putFile(Uri.fromFile(file)).await()
                return voiceRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Storage voice note upload error: ${e.message}. Using local file URI.")
            }
        }
        return if (file.exists()) Uri.fromFile(file).toString() else null
    }

    /**
     * Post 24-Hour Ephemeral Status / Story to Firestore
     */
    suspend fun postStatusToCloud(
        caption: String,
        authorHandle: String,
        authorName: String,
        avatarBgHex: String = "#DDE1FF",
        avatarTextHex: String = "#001453",
        mediaUrl: String? = null
    ): Boolean {
        val fs = firestore ?: return false
        val cleanHandle = authorHandle.lowercase().replace("@", "").trim()
        val now = System.currentTimeMillis()
        val expiresAt = now + (24 * 60 * 60 * 1000L) // 24 Hours TTL

        return try {
            val statusMap = hashMapOf(
                "authorHandle" to cleanHandle,
                "authorName" to authorName,
                "avatarBgHex" to avatarBgHex,
                "avatarTextHex" to avatarTextHex,
                "caption" to caption,
                "mediaUrl" to (mediaUrl ?: ""),
                "timestamp" to now,
                "expiresAt" to expiresAt
            )
            fs.collection("statuses").add(statusMap).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post cloud status: ${e.message}")
            false
        }
    }

    /**
     * Observe Active 24-Hour Stories across all users from Firestore
     */
    fun observeCloudStatuses(): Flow<List<StatusEntity>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        val listener = fs.collection("statuses")
            .whereGreaterThanOrEqualTo("timestamp", cutoff)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Cloud statuses listen error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val handle = doc.getString("authorHandle") ?: return@mapNotNull null
                        val name = doc.getString("authorName") ?: "@$handle"
                        val caption = doc.getString("caption") ?: ""
                        val bg = doc.getString("avatarBgHex") ?: "#DDE1FF"
                        val textHex = doc.getString("avatarTextHex") ?: "#001453"
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val media = doc.getString("mediaUrl")

                        StatusEntity(
                            id = doc.id.hashCode().toLong(),
                            authorName = name,
                            authorHandle = handle,
                            avatarBgHex = bg,
                            avatarTextHex = textHex,
                            caption = caption,
                            timestamp = timestamp,
                            isMe = (handle == currentUserHandle),
                            isViewed = false
                        )
                    }.sortedByDescending { it.timestamp }
                    trySend(list)
                }
            }

        awaitClose { listener.remove() }
    }

    // ==========================================
    // DISAPPEARING MESSAGES PURGE
    // ==========================================
    suspend fun purgeExpiredCloudMessages() {
        val fs = firestore ?: return
        val now = System.currentTimeMillis()
        try {
            val snapshot = fs.collection("messages")
                .whereGreaterThan("disappearingSeconds", 0)
                .whereLessThanOrEqualTo("expiresAtTimestamp", now)
                .limit(50)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    try {
                        doc.reference.delete().await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete expired cloud message ${doc.id}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Purge cloud query: ${e.message}")
        }
    }

    /**
     * Real-time SnapshotListener on Firestore to stream messages for an active conversation.
     */
    fun streamConversationMessages(
        myHandle: String,
        peerHandle: String,
        onMessagesReceived: (List<Map<String, Any>>) -> Unit
    ): ListenerRegistration? {
        val fs = firestore ?: return null
        val cleanMe = myHandle.lowercase().replace("@", "").trim()
        val cleanPeer = peerHandle.lowercase().replace("@", "").trim()
        if (cleanMe.isEmpty() || cleanPeer.isEmpty()) return null

        return try {
            fs.collection("messages")
                .whereArrayContains("targetRecipients", cleanMe)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Conversation stream error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val messagesList = snapshot.documents.mapNotNull { it.data }
                        onMessagesReceived(messagesList)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream conversation: ${e.message}")
            null
        }
    }

    suspend fun searchUserByHandle(handleQuery: String): CloudUser? {
        val fs = firestore ?: return null
        val clean = handleQuery.lowercase().replace("@", "").trim()
        if (clean.isEmpty()) return null

        return try {
            val snapshot = fs.collection("users").document(clean).get().await()
            if (snapshot.exists()) {
                val isOnlinePresence = _presenceMap.value[clean] ?: snapshot.getBoolean("isOnline") ?: false
                CloudUser(
                    handle = snapshot.getString("handle") ?: clean,
                    displayName = snapshot.getString("displayName") ?: clean,
                    publicKey = snapshot.getString("publicKey") ?: "ECDH-P256",
                    avatarBgHex = snapshot.getString("avatarBgHex") ?: "#DDE1FF",
                    avatarTextHex = snapshot.getString("avatarTextHex") ?: "#001453",
                    avatarUrl = snapshot.getString("avatarUrl") ?: "",
                    about = snapshot.getString("about") ?: "Available",
                    isOnline = isOnlinePresence,
                    fcmToken = snapshot.getString("fcmToken") ?: ""
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error querying user $clean: ${e.message}")
            null
        }
    }

    suspend fun getRecentRegisteredUsers(): List<CloudUser> {
        val fs = firestore ?: return emptyList()
        return try {
            val snapshot = fs.collection("users")
                .limit(50)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                val handle = doc.getString("handle") ?: return@mapNotNull null
                val isOnlinePresence = _presenceMap.value[handle] ?: doc.getBoolean("isOnline") ?: false
                CloudUser(
                    handle = handle,
                    displayName = doc.getString("displayName") ?: handle,
                    publicKey = doc.getString("publicKey") ?: "ECDH-P256",
                    avatarBgHex = doc.getString("avatarBgHex") ?: "#DDE1FF",
                    avatarTextHex = doc.getString("avatarTextHex") ?: "#001453",
                    avatarUrl = doc.getString("avatarUrl") ?: "",
                    about = doc.getString("about") ?: "Available",
                    isOnline = isOnlinePresence,
                    fcmToken = doc.getString("fcmToken") ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load recent registered users: ${e.message}")
            emptyList()
        }
    }

    /**
     * Real-time Flow of all registered users in Firestore combined with presence.
     */
    fun observeRegisteredUsers(): Flow<List<CloudUser>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = fs.collection("users")
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Users listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val users = snapshot.documents.mapNotNull { doc ->
                        val handle = doc.getString("handle") ?: return@mapNotNull null
                        val isOnline = _presenceMap.value[handle] ?: doc.getBoolean("isOnline") ?: false
                        CloudUser(
                            handle = handle,
                            displayName = doc.getString("displayName") ?: handle,
                            publicKey = doc.getString("publicKey") ?: "ECDH-P256",
                            avatarBgHex = doc.getString("avatarBgHex") ?: "#DDE1FF",
                            avatarTextHex = doc.getString("avatarTextHex") ?: "#001453",
                            avatarUrl = doc.getString("avatarUrl") ?: "",
                            about = doc.getString("about") ?: "Available",
                            isOnline = isOnline,
                            fcmToken = doc.getString("fcmToken") ?: ""
                        )
                    }
                    trySend(users)
                }
            }

        awaitClose { listener.remove() }
    }

    // ==========================================
    // CALL SIGNALING SERVICE
    // ==========================================
    fun startIncomingCallListener(myHandle: String, onIncomingCall: (CallSignal) -> Unit) {
        val fs = firestore ?: return
        val clean = myHandle.lowercase().replace("@", "").trim()
        if (clean.isEmpty()) return

        incomingCallListener?.remove()
        try {
            incomingCallListener = fs.collection("call_signals")
                .whereEqualTo("recipientHandle", clean)
                .whereEqualTo("status", "OFFERING")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "Call signal listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null && !snapshots.isEmpty) {
                        for (doc in snapshots.documents) {
                            val call = CallSignal(
                                callId = doc.id,
                                callerHandle = doc.getString("callerHandle") ?: "",
                                callerName = doc.getString("callerName") ?: "Peer",
                                recipientHandle = doc.getString("recipientHandle") ?: "",
                                isVideo = doc.getBoolean("isVideo") ?: false,
                                status = doc.getString("status") ?: "OFFERING",
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                            onIncomingCall(call)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call listener: ${e.message}")
        }
    }

    suspend fun initiateCall(callerHandle: String, callerName: String, recipientHandle: String, isVideo: Boolean): String? {
        val fs = firestore ?: return null
        val cleanRecipient = recipientHandle.lowercase().replace("@", "").trim()
        val cleanCaller = callerHandle.lowercase().replace("@", "").trim()
        if (cleanRecipient.isEmpty() || cleanCaller.isEmpty()) return null

        return try {
            val callDoc = fs.collection("call_signals").document()
            val payload = hashMapOf(
                "callId" to callDoc.id,
                "callerHandle" to cleanCaller,
                "callerName" to callerName,
                "recipientHandle" to cleanRecipient,
                "isVideo" to isVideo,
                "status" to "OFFERING",
                "timestamp" to System.currentTimeMillis()
            )
            callDoc.set(payload).await()
            callDoc.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call signal: ${e.message}")
            null
        }
    }

    suspend fun acceptCall(callId: String) {
        val fs = firestore ?: return
        try {
            fs.collection("call_signals").document(callId)
                .update("status", "ACCEPTED")
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept call: ${e.message}")
        }
    }

    suspend fun rejectCall(callId: String) {
        val fs = firestore ?: return
        try {
            fs.collection("call_signals").document(callId)
                .update("status", "REJECTED")
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reject call: ${e.message}")
        }
    }

    suspend fun endCall(callId: String) {
        val fs = firestore ?: return
        try {
            fs.collection("call_signals").document(callId)
                .update("status", "ENDED")
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call: ${e.message}")
        }
    }

    fun observeCallState(callId: String, onStatusChanged: (String) -> Unit): ListenerRegistration? {
        val fs = firestore ?: return null
        return try {
            fs.collection("call_signals").document(callId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val status = snapshot.getString("status") ?: "ENDED"
                        onStatusChanged(status)
                    }
                }
        } catch (e: Exception) {
            null
        }
    }

    fun stopListener() {
        setUserOffline()
        incomingMessageListener?.remove()
        incomingMessageListener = null
        incomingCallListener?.remove()
        incomingCallListener = null
        conversationStreamListener?.remove()
        conversationStreamListener = null
    }
}

data class CallSignal(
    val callId: String = "",
    val callerHandle: String = "",
    val callerName: String = "",
    val recipientHandle: String = "",
    val isVideo: Boolean = false,
    val status: String = "OFFERING",
    val timestamp: Long = System.currentTimeMillis()
)
