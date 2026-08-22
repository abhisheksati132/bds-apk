package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.crypto.CryptoHelper
import com.example.data.local.MessengerDao
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

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
    val about: String = "Available",
    val isOnline: Boolean = true
)

class FirebaseCloudService(
    private val context: Context,
    private val dao: MessengerDao,
    private val scope: CoroutineScope
) {
    private val TAG = "FirebaseCloudService"
    private var firestore: FirebaseFirestore? = null
    private var incomingMessageListener: ListenerRegistration? = null

    private val _cloudStatus = MutableStateFlow<CloudStatus>(CloudStatus.Checking)
    val cloudStatus: StateFlow<CloudStatus> = _cloudStatus.asStateFlow()

    init {
        initFirebase()
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firestore = FirebaseFirestore.getInstance()
                Log.d(TAG, "Firebase initialized successfully.")
            } else {
                Log.w(TAG, "No Firebase App initialized; running in Local Encrypted Mode.")
                _cloudStatus.value = CloudStatus.StandaloneLocal("Offline/Local Encrypted Mode (Add google-services.json to enable Cloud Relay)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization exception: ${e.message}")
            _cloudStatus.value = CloudStatus.StandaloneLocal("Local Encrypted Mode (${e.localizedMessage ?: "Standalone"})")
        }
    }

    fun isCloudAvailable(): Boolean = firestore != null

    suspend fun registerUser(handle: String, displayName: String, publicKey: String): Boolean {
        val fs = firestore ?: return false
        val cleanHandle = handle.lowercase().replace("@", "").trim()
        if (cleanHandle.isEmpty()) return false

        return try {
            val userMap = hashMapOf(
                "handle" to cleanHandle,
                "displayName" to displayName,
                "publicKey" to publicKey,
                "avatarBgHex" to "#DDE1FF",
                "avatarTextHex" to "#001453",
                "about" to "Zero-trust encrypted peer",
                "lastActive" to FieldValue.serverTimestamp(),
                "isOnline" to true
            )
            fs.collection("users").document(cleanHandle)
                .set(userMap, SetOptions.merge())
                .await()

            _cloudStatus.value = CloudStatus.Connected(cleanHandle)
            startIncomingMessageListener(cleanHandle)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register user in cloud: ${e.message}")
            false
        }
    }

    fun startIncomingMessageListener(myHandle: String) {
        val fs = firestore ?: return
        val cleanHandle = myHandle.lowercase().replace("@", "").trim()
        if (cleanHandle.isEmpty()) return

        incomingMessageListener?.remove()

        try {
            incomingMessageListener = fs.collection("messages")
                .whereEqualTo("recipientHandle", cleanHandle)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen failed: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        for (doc in snapshots.documents) {
                            val senderHandle = doc.getString("senderHandle") ?: continue
                            val cipherText = doc.getString("cipherText") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val msgType = doc.getString("type") ?: MessageType.TEXT.name
                            val voiceDuration = doc.getLong("voiceDuration")?.toInt() ?: 0
                            val disappearingSeconds = doc.getLong("disappearingSeconds") ?: 0L
                            val replyToText = doc.getString("replyToText")
                            val docId = doc.id

                            // Process message in background
                            scope.launch(Dispatchers.IO) {
                                handleReceivedCloudMessage(
                                    senderHandle = senderHandle,
                                    cipherText = cipherText,
                                    timestamp = timestamp,
                                    type = MessageType.valueOf(msgType),
                                    voiceDuration = voiceDuration,
                                    disappearingSeconds = disappearingSeconds,
                                    replyToText = replyToText
                                )

                                // Delete message from cloud relay to ensure Zero-Knowledge persistence
                                try {
                                    fs.collection("messages").document(docId).delete().await()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to clear delivered message: ${e.message}")
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
        voiceDuration: Int,
        disappearingSeconds: Long,
        replyToText: String?
    ) {
        var conv = dao.getConversationByHandle(senderHandle)
        val conversationId: Long

        if (conv == null) {
            // Create conversation for this sender
            val newConv = ConversationEntity(
                peerId = "u_${senderHandle.replace(".", "_")}",
                peerName = "@$senderHandle",
                peerHandle = senderHandle,
                avatarBgColorHex = "#EADDFF",
                avatarTextColorHex = "#21005D",
                lastMessage = if (type == MessageType.VOICE) "🎤 Voice note" else CryptoHelper.decrypt(cipherText),
                lastTimestamp = timestamp,
                unreadCount = 1,
                isOnline = true,
                isEncrypted = true,
                keyFingerprint = CryptoHelper.generateFingerprint(senderHandle),
                disappearingTimerSeconds = disappearingSeconds
            )
            conversationId = dao.insertConversation(newConv)
        } else {
            conversationId = conv.id
            val preview = if (type == MessageType.VOICE) "🎤 Voice note" else CryptoHelper.decrypt(cipherText)
            dao.updateConversation(
                conv.copy(
                    lastMessage = preview,
                    lastTimestamp = timestamp,
                    unreadCount = conv.unreadCount + 1
                )
            )
        }

        val decrypted = CryptoHelper.decrypt(cipherText)
        val expiresAt = if (disappearingSeconds > 0) timestamp + (disappearingSeconds * 1000L) else null

        val message = MessageEntity(
            conversationId = conversationId,
            senderId = senderHandle,
            text = decrypted,
            cipherText = cipherText,
            timestamp = timestamp,
            isMe = false,
            status = MessageStatus.READ,
            type = type,
            voiceDurationSeconds = voiceDuration,
            isDisappearing = disappearingSeconds > 0,
            expiresAtTimestamp = expiresAt,
            replyToText = replyToText
        )

        dao.insertMessage(message)
    }

    suspend fun sendCloudMessage(
        senderHandle: String,
        recipientHandle: String,
        cipherText: String,
        type: MessageType = MessageType.TEXT,
        voiceDurationSeconds: Int = 0,
        disappearingTimerSeconds: Long = 0,
        replyToText: String? = null
    ): Boolean {
        val fs = firestore ?: return false
        val cleanRecipient = recipientHandle.lowercase().replace("@", "").trim()
        val cleanSender = senderHandle.lowercase().replace("@", "").trim()

        if (cleanRecipient.isEmpty() || cleanSender.isEmpty()) return false

        return try {
            val payload = hashMapOf(
                "senderHandle" to cleanSender,
                "recipientHandle" to cleanRecipient,
                "cipherText" to cipherText,
                "timestamp" to System.currentTimeMillis(),
                "type" to type.name,
                "voiceDuration" to voiceDurationSeconds,
                "disappearingSeconds" to disappearingTimerSeconds,
                "replyToText" to replyToText,
                "status" to "SENT"
            )

            fs.collection("messages").add(payload).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to relay cloud message: ${e.message}")
            false
        }
    }

    suspend fun searchUserByHandle(handleQuery: String): CloudUser? {
        val fs = firestore ?: return null
        val clean = handleQuery.lowercase().replace("@", "").trim()
        if (clean.isEmpty()) return null

        return try {
            val snapshot = fs.collection("users").document(clean).get().await()
            if (snapshot.exists()) {
                CloudUser(
                    handle = snapshot.getString("handle") ?: clean,
                    displayName = snapshot.getString("displayName") ?: clean,
                    publicKey = snapshot.getString("publicKey") ?: "ECDH-P256",
                    avatarBgHex = snapshot.getString("avatarBgHex") ?: "#DDE1FF",
                    avatarTextHex = snapshot.getString("avatarTextHex") ?: "#001453",
                    about = snapshot.getString("about") ?: "Available",
                    isOnline = snapshot.getBoolean("isOnline") ?: true
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error querying user $clean: ${e.message}")
            null
        }
    }

    fun stopListener() {
        incomingMessageListener?.remove()
        incomingMessageListener = null
    }
}
