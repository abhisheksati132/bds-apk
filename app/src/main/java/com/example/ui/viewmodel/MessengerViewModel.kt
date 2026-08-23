package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.CryptoHelper
import com.example.data.local.AppDatabase
import com.example.data.local.SecurityPreferencesRepository
import com.example.data.model.*
import com.example.data.remote.CallSignal
import com.example.data.remote.CloudStatus
import com.example.data.remote.CloudUser
import com.example.data.remote.FirebaseAuthService
import com.example.data.remote.FirebaseCloudService
import com.example.data.repository.MessengerRepository
import com.example.util.HapticHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MainTab {
    CHATS,
    CONTACTS,
    STATUS,
    CALLS,
    VAULT_SECURITY
}

data class UiState(
    val activeTab: MainTab = MainTab.CHATS,
    val activeConversationId: Long? = null,
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // ALL, UNREAD, ENCRYPTED, GROUPS
    val isNewChatDialogOpen: Boolean = false,
    val isCreateGroupDialogOpen: Boolean = false,
    val isKeyFingerprintDialogOpen: Boolean = false,
    val isAuthDialogOpen: Boolean = false,
    val authUser: AuthUser? = null,
    val isGuestUser: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val isAppLocked: Boolean = false,
    val isPinSetupOpen: Boolean = false,
    val pinCode: String = "",
    val isPinEnabled: Boolean = false,
    val preventScreenshots: Boolean = false,
    val defaultDisappearingSeconds: Long = 0,
    val replyingToMessage: MessageEntity? = null,
    val isRecordingVoice: Boolean = false,
    val recordingSeconds: Int = 0,
    val isPeerTyping: Boolean = false,
    val activeCall: ConversationEntity? = null,
    val isVideoCall: Boolean = false,
    val callDurationSeconds: Int = 0,
    val isCallMuted: Boolean = false,
    val isCallSpeaker: Boolean = false,
    val incomingCall: CallSignal? = null,
    val activeCallSignalId: String? = null,
    val myHandle: String = "",
    val myDisplayName: String = "",
    val myAbout: String = "Zero-trust encrypted peer",
    val myAvatarUrl: String? = null,
    val myAvatarBgHex: String = "#DDE1FF",
    val myAvatarTextHex: String = "#001453",
    val isUserProfileDialogOpen: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val isForwardDialogOpen: Boolean = false,
    val forwardingMessage: MessageEntity? = null,
    val isNotificationSoundEnabled: Boolean = true,
    val vibrationPattern: String = "DEFAULT", // DEFAULT, SHORT, LONG, HEARTBEAT, OFF
    val myPublicKey: String = "ECDH-P256: 4F91B2E6AA1998C1",
    val cloudStatus: CloudStatus = CloudStatus.Checking,
    val isSearchingCloud: Boolean = false,
    val cloudUsers: List<CloudUser> = emptyList(),
    val presenceMap: Map<String, Boolean> = emptyMap(),
    val isDarkMode: Boolean? = null,
    val blockedHandles: Set<String> = emptySet()
) {
    val isAuthenticated: Boolean
        get() = authUser != null || isGuestUser || myHandle.isNotBlank()
}

class MessengerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("vault_messenger_prefs", Context.MODE_PRIVATE)
    private val securityPrefs = SecurityPreferencesRepository(application)
    private val repository: MessengerRepository
    private val cloudService: FirebaseCloudService
    private val authService: FirebaseAuthService
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var callTimerJob: Job? = null
    private var voiceRecordJob: Job? = null
    private var cleanupTimerJob: Job? = null
    private var callStateListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var activeTypingListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        val db = AppDatabase.getDatabase(application, viewModelScope)
        cloudService = FirebaseCloudService(application, db.messengerDao(), viewModelScope)
        authService = FirebaseAuthService(application)
        repository = MessengerRepository(db.messengerDao(), cloudService)

        // Observe DataStore Security Preferences & Dark Mode
        viewModelScope.launch {
            securityPrefs.isDarkModeFlow.collect { dark ->
                _uiState.update { it.copy(isDarkMode = dark) }
            }
        }
        viewModelScope.launch {
            securityPrefs.notificationSoundsFlow.collect { soundsEnabled ->
                _uiState.update { it.copy(isNotificationSoundEnabled = soundsEnabled) }
            }
        }
        viewModelScope.launch {
            securityPrefs.vibrationPatternFlow.collect { pattern ->
                _uiState.update { it.copy(vibrationPattern = pattern) }
            }
        }
        viewModelScope.launch {
            cloudService.blockedUsers.collect { blocked ->
                _uiState.update { it.copy(blockedHandles = blocked) }
            }
        }
        viewModelScope.launch {
            securityPrefs.pinCodeFlow.collect { pin ->
                _uiState.update { it.copy(pinCode = pin) }
            }
        }
        viewModelScope.launch {
            securityPrefs.isPinEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(isPinEnabled = enabled, isAppLocked = enabled && it.pinCode.isNotBlank()) }
            }
        }
        viewModelScope.launch {
            securityPrefs.isScreenshotProtectedFlow.collect { protected ->
                _uiState.update { it.copy(preventScreenshots = protected) }
            }
        }
        viewModelScope.launch {
            securityPrefs.defaultDisappearingFlow.collect { defaultSec ->
                _uiState.update { it.copy(defaultDisappearingSeconds = defaultSec) }
            }
        }

        // Restore saved profile & handle if present
        val savedHandle = prefs.getString("saved_handle", "") ?: ""
        val savedGuest = prefs.getBoolean("is_guest", false)
        val savedDisplayName = prefs.getString("saved_display_name", "") ?: ""
        val savedAbout = prefs.getString("saved_about", "Zero-trust encrypted peer") ?: "Zero-trust encrypted peer"
        val savedAvatarUrl = prefs.getString("saved_avatar_url", null)
        val savedAvatarBgHex = prefs.getString("saved_avatar_bg_hex", "#DDE1FF") ?: "#DDE1FF"
        val savedAvatarTextHex = prefs.getString("saved_avatar_text_hex", "#001453") ?: "#001453"

        if (savedHandle.isNotBlank()) {
            _uiState.update {
                it.copy(
                    myHandle = savedHandle,
                    isGuestUser = savedGuest,
                    myDisplayName = savedDisplayName,
                    myAbout = savedAbout,
                    myAvatarUrl = savedAvatarUrl,
                    myAvatarBgHex = savedAvatarBgHex,
                    myAvatarTextHex = savedAvatarTextHex
                )
            }
            registerAndListenForHandle(savedHandle)
        }

        // Observe Auth User
        viewModelScope.launch {
            authService.currentUser.collect { user ->
                _uiState.update { current ->
                    val handle = if (user?.email != null) {
                        user.email.substringBefore("@").replace(".", "").lowercase()
                    } else if (current.myHandle.isNotBlank()) {
                        current.myHandle
                    } else ""

                    current.copy(
                        authUser = user,
                        myHandle = handle
                    )
                }

                user?.let {
                    val handle = _uiState.value.myHandle.ifBlank {
                        (it.email?.substringBefore("@") ?: "user_${it.uid.take(6)}").lowercase()
                    }
                    prefs.edit().putString("saved_handle", handle).putBoolean("is_guest", false).apply()
                    _uiState.update { s -> s.copy(myHandle = handle) }
                    registerAndListenForHandle(handle)
                }
            }
        }

        // Observe Cloud Status
        viewModelScope.launch {
            cloudService.cloudStatus.collect { status ->
                _uiState.update { it.copy(cloudStatus = status) }
            }
        }

        // Observe Realtime Database Presence
        viewModelScope.launch {
            repository.presenceMap.collect { pMap ->
                _uiState.update { it.copy(presenceMap = pMap) }
            }
        }

        // Observe Real-time Registered Cloud Users
        viewModelScope.launch {
            repository.observeCloudUsers().collect { users ->
                if (users.isNotEmpty()) {
                    _uiState.update { it.copy(cloudUsers = users) }
                }
            }
        }

        // Periodic cleanup for disappearing messages
        cleanupTimerJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                repository.purgeExpiredMessages()
            }
        }

        loadRecentUsers()
    }

    private fun registerAndListenForHandle(handle: String) {
        val clean = handle.lowercase().replace("@", "").trim()
        if (clean.isBlank()) return

        viewModelScope.launch {
            cloudService.registerUser(
                handle = clean,
                displayName = _uiState.value.authUser?.displayName ?: "User $clean",
                publicKey = _uiState.value.myPublicKey
            )
            cloudService.startIncomingMessageListener(clean)
            cloudService.syncBlockedUsers(clean)
            cloudService.startIncomingCallListener(clean) { incoming ->
                _uiState.update { it.copy(incomingCall = incoming) }
            }
        }
    }

    fun loadRecentUsers() {
        viewModelScope.launch {
            val users = cloudService.getRecentRegisteredUsers()
            if (users.isNotEmpty()) {
                _uiState.update { it.copy(cloudUsers = users) }
            }
        }
    }

    fun loginAsGuest(handle: String) {
        val clean = handle.lowercase().replace("@", "").replace(" ", "").trim()
        if (clean.isNotBlank()) {
            prefs.edit().putString("saved_handle", clean).putBoolean("is_guest", true).apply()
            _uiState.update { it.copy(myHandle = clean, isGuestUser = true) }
            registerAndListenForHandle(clean)
        }
    }

    val conversations: StateFlow<List<ConversationEntity>> = combine(
        repository.activeConversations,
        _uiState.map { it.presenceMap }.distinctUntilChanged()
    ) { convs, presence ->
        convs.map { conv ->
            val isOnline = if (conv.isGroup) true else (presence[conv.peerHandle] ?: conv.isOnline)
            conv.copy(isOnline = isOnline)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statuses: StateFlow<List<StatusEntity>> = repository.allStatuses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calls: StateFlow<List<CallEntity>> = repository.allCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun currentMessages(conversationId: Long): Flow<List<MessageEntity>> {
        return repository.getMessages(conversationId)
    }

    fun currentConversation(conversationId: Long): Flow<ConversationEntity?> {
        return combine(
            repository.getConversation(conversationId),
            _uiState.map { it.presenceMap }.distinctUntilChanged()
        ) { conv, presence ->
            if (conv == null) null
            else {
                val isOnline = if (conv.isGroup) true else (presence[conv.peerHandle] ?: conv.isOnline)
                conv.copy(isOnline = isOnline)
            }
        }
    }

    fun setTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun openConversation(conversationId: Long) {
        _uiState.update { it.copy(activeConversationId = conversationId, replyingToMessage = null, isPeerTyping = false) }
        val handle = _uiState.value.myHandle
        viewModelScope.launch {
            repository.markConversationRead(conversationId, handle)
            val conv = repository.getConversation(conversationId).firstOrNull()
            if (conv != null) {
                val threadKey = if (conv.isGroup) conv.peerHandle else {
                    listOf(handle.lowercase().trim(), conv.peerHandle.lowercase().trim()).sorted().joinToString("_")
                }
                activeTypingListener?.remove()
                activeTypingListener = repository.observeTyping(threadKey, conv.peerHandle) { isTyping ->
                    _uiState.update { it.copy(isPeerTyping = isTyping) }
                }
            }
        }
    }

    fun closeConversation() {
        val convId = _uiState.value.activeConversationId
        val handle = _uiState.value.myHandle
        if (convId != null && handle.isNotBlank()) {
            viewModelScope.launch {
                val conv = repository.getConversation(convId).firstOrNull()
                if (conv != null) {
                    val threadKey = if (conv.isGroup) conv.peerHandle else {
                        listOf(handle.lowercase().trim(), conv.peerHandle.lowercase().trim()).sorted().joinToString("_")
                    }
                    repository.setTyping(threadKey, handle, false)
                }
            }
        }
        activeTypingListener?.remove()
        activeTypingListener = null
        _uiState.update { it.copy(activeConversationId = null, replyingToMessage = null, isPeerTyping = false) }
    }

    fun sendTyping(conversationId: Long, isTyping: Boolean) {
        val handle = _uiState.value.myHandle
        if (handle.isBlank()) return
        viewModelScope.launch {
            val conv = repository.getConversation(conversationId).firstOrNull()
            if (conv != null) {
                val threadKey = if (conv.isGroup) conv.peerHandle else {
                    listOf(handle.lowercase().trim(), conv.peerHandle.lowercase().trim()).sorted().joinToString("_")
                }
                repository.setTyping(threadKey, handle, isTyping)
            }
        }
    }

    fun blockUser(handle: String) {
        val clean = handle.lowercase().replace("@", "").trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            repository.blockUser(_uiState.value.myHandle, clean)
        }
    }

    fun unblockUser(handle: String) {
        val clean = handle.lowercase().replace("@", "").trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            repository.unblockUser(_uiState.value.myHandle, clean)
        }
    }

    fun reactToMessage(message: MessageEntity, emoji: String?) {
        HapticHelper.playReactionHaptic(getApplication())
        viewModelScope.launch {
            repository.updateMessageReaction(message.id, message.cloudMsgDocId, emoji)
        }
    }

    fun setDarkMode(enabled: Boolean?) {
        viewModelScope.launch {
            securityPrefs.setDarkMode(enabled)
        }
    }

    fun toggleNotificationSound(enabled: Boolean) {
        viewModelScope.launch {
            securityPrefs.setNotificationSounds(enabled)
        }
    }

    fun setVibrationPattern(pattern: String) {
        viewModelScope.launch {
            securityPrefs.setVibrationPattern(pattern)
        }
    }

    fun testVibration() {
        val app = getApplication<Application>()
        HapticHelper.triggerVibrationPattern(app, _uiState.value.vibrationPattern)
        if (_uiState.value.isNotificationSoundEnabled) {
            HapticHelper.playNotificationSound(app)
        }
    }

    fun setUserProfileDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isUserProfileDialogOpen = open) }
    }

    fun uploadAndSetAvatar(imageUri: Uri, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val handle = _uiState.value.myHandle
        if (handle.isBlank()) {
            onResult(false, "Please set a username handle first")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingProfile = true) }
            val downloadUrl = cloudService.uploadUserAvatar(imageUri, handle)
            if (downloadUrl != null) {
                prefs.edit().putString("saved_avatar_url", downloadUrl).apply()
                _uiState.update { it.copy(myAvatarUrl = downloadUrl, isUpdatingProfile = false) }
                cloudService.updateUserProfile(
                    handle = handle,
                    displayName = _uiState.value.myDisplayName.ifBlank { "@$handle" },
                    about = _uiState.value.myAbout,
                    avatarUrl = downloadUrl
                )
                onResult(true, "Avatar uploaded successfully!")
            } else {
                _uiState.update { it.copy(isUpdatingProfile = false) }
                onResult(false, "Failed to upload avatar image")
            }
        }
    }

    fun updateProfile(
        displayName: String,
        about: String,
        avatarBgHex: String? = null,
        avatarTextHex: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        val handle = _uiState.value.myHandle
        prefs.edit()
            .putString("saved_display_name", displayName)
            .putString("saved_about", about)
            .apply()

        if (avatarBgHex != null) prefs.edit().putString("saved_avatar_bg_hex", avatarBgHex).apply()
        if (avatarTextHex != null) prefs.edit().putString("saved_avatar_text_hex", avatarTextHex).apply()

        _uiState.update {
            it.copy(
                myDisplayName = displayName,
                myAbout = about,
                myAvatarBgHex = avatarBgHex ?: it.myAvatarBgHex,
                myAvatarTextHex = avatarTextHex ?: it.myAvatarTextHex
            )
        }

        if (handle.isNotBlank()) {
            viewModelScope.launch {
                cloudService.updateUserProfile(
                    handle = handle,
                    displayName = displayName,
                    about = about,
                    avatarUrl = _uiState.value.myAvatarUrl,
                    avatarBgHex = avatarBgHex,
                    avatarTextHex = avatarTextHex
                )
                onResult(true)
            }
        } else {
            onResult(true)
        }
    }

    fun removeAvatar() {
        val handle = _uiState.value.myHandle
        prefs.edit().remove("saved_avatar_url").apply()
        _uiState.update { it.copy(myAvatarUrl = null) }
        if (handle.isNotBlank()) {
            viewModelScope.launch {
                cloudService.updateUserProfile(
                    handle = handle,
                    displayName = _uiState.value.myDisplayName.ifBlank { "@$handle" },
                    about = _uiState.value.myAbout,
                    avatarUrl = ""
                )
            }
        }
    }

    fun setForwardDialogOpen(open: Boolean, message: MessageEntity? = null) {
        _uiState.update { it.copy(isForwardDialogOpen = open, forwardingMessage = message) }
    }

    fun forwardMessageTo(targetConversationId: Long, originalMessage: MessageEntity) {
        HapticHelper.playMessageSentHaptic(getApplication())
        _uiState.update { it.copy(isForwardDialogOpen = false, forwardingMessage = null) }

        viewModelScope.launch {
            when (originalMessage.type) {
                MessageType.IMAGE -> {
                    if (originalMessage.mediaUrl != null) {
                        repository.sendMessage(
                            conversationId = targetConversationId,
                            text = originalMessage.text.ifBlank { "Forwarded photo" },
                            type = MessageType.IMAGE,
                            mediaUrl = originalMessage.mediaUrl,
                            myHandle = _uiState.value.myHandle
                        )
                    }
                }
                MessageType.VOICE -> {
                    repository.sendMessage(
                        conversationId = targetConversationId,
                        text = "Forwarded voice message",
                        type = MessageType.VOICE,
                        voiceDurationSeconds = originalMessage.voiceDurationSeconds,
                        mediaUrl = originalMessage.mediaUrl,
                        myHandle = _uiState.value.myHandle
                    )
                }
                else -> {
                    repository.sendMessage(
                        conversationId = targetConversationId,
                        text = originalMessage.text,
                        type = MessageType.TEXT,
                        myHandle = _uiState.value.myHandle
                    )
                }
            }

            if (!cloudService.isCloudAvailable()) {
                triggerSimulatedPeerReply(targetConversationId, originalMessage.text)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setNewChatDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isNewChatDialogOpen = open) }
    }

    fun setCreateGroupDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isCreateGroupDialogOpen = open) }
    }

    fun setKeyFingerprintDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isKeyFingerprintDialogOpen = open) }
    }

    fun setReplyingTo(message: MessageEntity?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun sendMessage(
        conversationId: Long,
        text: String,
        isDisappearing: Boolean = false,
        disappearingTimerSeconds: Long = 0,
        type: MessageType = MessageType.TEXT,
        voiceDurationSeconds: Int = 0
    ) {
        if (text.isBlank() && type == MessageType.TEXT) return
        HapticHelper.playMessageSentHaptic(getApplication())
        val reply = _uiState.value.replyingToMessage
        val currentHandle = _uiState.value.myHandle

        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                text = text,
                isDisappearing = isDisappearing || disappearingTimerSeconds > 0,
                disappearingTimerSeconds = disappearingTimerSeconds,
                replyToId = reply?.id,
                replyToText = reply?.text,
                type = type,
                voiceDurationSeconds = voiceDurationSeconds,
                myHandle = currentHandle
            )

            _uiState.update { it.copy(replyingToMessage = null) }

            // If in local/standalone mode, simulate peer auto-reply for testing
            if (!cloudService.isCloudAvailable()) {
                triggerSimulatedPeerReply(conversationId, text)
            }
        }
    }

    fun sendImage(
        conversationId: Long,
        imageUri: Uri,
        isDisappearing: Boolean = false,
        disappearingTimerSeconds: Long = 0
    ) {
        HapticHelper.playMessageSentHaptic(getApplication())
        val currentHandle = _uiState.value.myHandle
        viewModelScope.launch {
            repository.sendImageMessage(
                conversationId = conversationId,
                imageUri = imageUri,
                isDisappearing = isDisappearing || disappearingTimerSeconds > 0,
                disappearingTimerSeconds = disappearingTimerSeconds,
                myHandle = currentHandle
            )
        }
    }

    fun createGroupChat(groupName: String, memberHandles: List<String>) {
        val adminHandle = _uiState.value.myHandle
        viewModelScope.launch {
            val convId = repository.createGroupChat(
                groupName = groupName,
                memberHandles = memberHandles,
                adminHandle = adminHandle
            )
            setCreateGroupDialogOpen(false)
            setNewChatDialogOpen(false)
            openConversation(convId)
        }
    }

    private fun triggerSimulatedPeerReply(conversationId: Long, sentText: String) {
        viewModelScope.launch {
            delay(1200)
            _uiState.update { it.copy(isPeerTyping = true) }
            delay(1600)
            _uiState.update { it.copy(isPeerTyping = false) }

            val response = when {
                sentText.contains("?", ignoreCase = true) ->
                    "Everything is synchronized and zero-knowledge encrypted on our side! 🔒"
                sentText.contains("hello", ignoreCase = true) || sentText.contains("hey", ignoreCase = true) ->
                    "Hey! Glad you reached out on the secure channel."
                sentText.contains("timer", ignoreCase = true) || sentText.contains("disappear", ignoreCase = true) ->
                    "Disappearing timer acknowledged. Messages will self-destruct seamlessly."
                else -> "Received securely. Key verification check passed."
            }

            repository.receiveSimulatedReply(conversationId, response)
        }
    }

    fun updateDisappearingTimer(conversationId: Long, seconds: Long) {
        viewModelScope.launch {
            repository.setDisappearingTimer(conversationId, seconds)
        }
    }

    fun clearChat(conversationId: Long) {
        HapticHelper.playClearChatHaptic(getApplication())
        val myHandle = _uiState.value.myHandle
        viewModelScope.launch {
            repository.clearChatMessages(conversationId, myHandle)
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            closeConversation()
        }
    }

    fun startNewChatWithContact(contact: ContactEntity) {
        viewModelScope.launch {
            val convId = repository.createOrGetConversationForContact(contact)
            setNewChatDialogOpen(false)
            openConversation(convId)
        }
    }

    fun startNewChatWithCloudUser(cloudUser: CloudUser) {
        viewModelScope.launch {
            val convId = repository.createOrGetConversationForCloudUser(cloudUser)
            setNewChatDialogOpen(false)
            openConversation(convId)
        }
    }

    fun createCustomContactAndChat(name: String, handle: String) {
        viewModelScope.launch {
            val clean = if (handle.startsWith("@")) handle.substring(1) else handle
            val newContact = ContactEntity(
                handle = clean,
                name = name.ifBlank { "@$clean" },
                avatarBgHex = "#DDE1FF",
                avatarTextHex = "#001453",
                publicKey = "ECDH-P256: " + CryptoHelper.generateFingerprint(clean),
                isVerified = true,
                about = "Encrypted peer"
            )
            repository.addContact(newContact)
            val convId = repository.createOrGetConversationForContact(newContact)
            setNewChatDialogOpen(false)
            openConversation(convId)
        }
    }

    fun searchAndAddCloudPeer(handle: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingCloud = true) }
            val clean = if (handle.startsWith("@")) handle.substring(1) else handle
            val cloudUser = cloudService.searchUserByHandle(clean)

            _uiState.update { it.copy(isSearchingCloud = false) }
            if (cloudUser != null) {
                val convId = repository.createOrGetConversationForCloudUser(cloudUser)
                setNewChatDialogOpen(false)
                openConversation(convId)
                onResult(true, "Connected with @${cloudUser.handle}")
            } else {
                createCustomContactAndChat(clean, clean)
                onResult(false, "User not found in cloud registry, added as offline/local contact")
            }
        }
    }

    fun startVoiceRecording() {
        _uiState.update { it.copy(isRecordingVoice = true, recordingSeconds = 0) }
        voiceRecordJob?.cancel()
        voiceRecordJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceRecordJob?.cancel()
        _uiState.update { it.copy(isRecordingVoice = false, recordingSeconds = 0) }
    }

    fun finishVoiceRecording(conversationId: Long, isDisappearing: Boolean, timer: Long) {
        voiceRecordJob?.cancel()
        val duration = _uiState.value.recordingSeconds.coerceAtLeast(1)
        _uiState.update { it.copy(isRecordingVoice = false, recordingSeconds = 0) }
        sendMessage(
            conversationId = conversationId,
            text = "Voice message",
            isDisappearing = isDisappearing,
            disappearingTimerSeconds = timer,
            type = MessageType.VOICE,
            voiceDurationSeconds = duration
        )
    }

    fun startCall(peer: ConversationEntity, isVideo: Boolean) {
        _uiState.update {
            it.copy(
                activeCall = peer,
                isVideoCall = isVideo,
                callDurationSeconds = 0,
                isCallMuted = false,
                isCallSpeaker = false
            )
        }
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }

        // Fire call signal to recipient via Cloud Relay
        viewModelScope.launch {
            repository.logCall(peer.peerName, peer.peerHandle, peer.avatarBgColorHex, peer.avatarTextColorHex, isVideo)
            val callSignalId = cloudService.initiateCall(
                callerHandle = _uiState.value.myHandle,
                callerName = _uiState.value.authUser?.displayName ?: "@${_uiState.value.myHandle}",
                recipientHandle = peer.peerHandle,
                isVideo = isVideo
            )
            _uiState.update { it.copy(activeCallSignalId = callSignalId) }

            if (callSignalId != null) {
                callStateListener?.remove()
                callStateListener = cloudService.observeCallState(callSignalId) { status ->
                    if (status == "ENDED" || status == "REJECTED") {
                        endCall(sendSignal = false)
                    }
                }
            }
        }
    }

    fun startCallWithUser(name: String, handle: String, isVideo: Boolean) {
        val dummyConv = ConversationEntity(
            peerId = "u_${handle.replace(".", "_")}",
            peerName = name,
            peerHandle = handle,
            avatarBgColorHex = "#DDE1FF",
            avatarTextColorHex = "#001453",
            lastMessage = if (isVideo) "Video call" else "Voice call",
            lastTimestamp = System.currentTimeMillis(),
            isOnline = true
        )
        startCall(dummyConv, isVideo)
    }

    fun acceptIncomingCall() {
        val incoming = _uiState.value.incomingCall ?: return
        val peerConv = ConversationEntity(
            peerId = "u_${incoming.callerHandle}",
            peerName = incoming.callerName,
            peerHandle = incoming.callerHandle,
            avatarBgColorHex = "#EADDFF",
            avatarTextColorHex = "#21005D",
            lastMessage = if (incoming.isVideo) "Video call" else "Voice call",
            lastTimestamp = System.currentTimeMillis(),
            isOnline = true
        )

        viewModelScope.launch {
            cloudService.acceptCall(incoming.callId)
        }

        _uiState.update {
            it.copy(
                activeCall = peerConv,
                isVideoCall = incoming.isVideo,
                incomingCall = null,
                activeCallSignalId = incoming.callId,
                callDurationSeconds = 0
            )
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }
    }

    fun rejectIncomingCall() {
        val incoming = _uiState.value.incomingCall ?: return
        viewModelScope.launch {
            cloudService.rejectCall(incoming.callId)
        }
        _uiState.update { it.copy(incomingCall = null) }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isCallMuted = !it.isCallMuted) }
    }

    fun toggleSpeaker() {
        _uiState.update { it.copy(isCallSpeaker = !it.isCallSpeaker) }
    }

    fun endCall(sendSignal: Boolean = true) {
        callTimerJob?.cancel()
        val signalId = _uiState.value.activeCallSignalId
        if (sendSignal && signalId != null) {
            viewModelScope.launch {
                cloudService.endCall(signalId)
            }
        }
        callStateListener?.remove()
        callStateListener = null
        _uiState.update { it.copy(activeCall = null, activeCallSignalId = null, callDurationSeconds = 0) }
    }

    fun postStatus(caption: String) {
        if (caption.isBlank()) return
        viewModelScope.launch {
            repository.addStatus(caption)
        }
    }

    fun togglePreventScreenshots(prevent: Boolean) {
        viewModelScope.launch {
            securityPrefs.setScreenshotProtected(prevent)
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            securityPrefs.savePin(pin)
            _uiState.update { it.copy(pinCode = pin, isPinEnabled = pin.isNotEmpty(), isAppLocked = false) }
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            securityPrefs.setPinEnabled(false)
            _uiState.update { it.copy(pinCode = "", isPinEnabled = false, isAppLocked = false) }
        }
    }

    fun unlockWithPin(entered: String): Boolean {
        if (entered == _uiState.value.pinCode || _uiState.value.pinCode.isEmpty()) {
            _uiState.update { it.copy(isAppLocked = false) }
            return true
        }
        return false
    }

    fun lockApp() {
        if (_uiState.value.isPinEnabled && _uiState.value.pinCode.isNotBlank()) {
            _uiState.update { it.copy(isAppLocked = true) }
        }
    }

    fun updateMyHandle(newHandle: String) {
        val clean = if (newHandle.startsWith("@")) newHandle.substring(1) else newHandle
        if (clean.isNotBlank()) {
            val trimmed = clean.trim()
            prefs.edit().putString("saved_handle", trimmed).apply()
            _uiState.update { it.copy(myHandle = trimmed) }
            viewModelScope.launch {
                cloudService.registerUser(
                    handle = trimmed,
                    displayName = "User $trimmed",
                    publicKey = _uiState.value.myPublicKey
                )
            }
        }
    }

    fun rotateMyKeys() {
        val newKey = "ECDH-P256: " + CryptoHelper.generateFingerprint(_uiState.value.myHandle + System.currentTimeMillis())
        _uiState.update { it.copy(myPublicKey = newKey) }
        viewModelScope.launch {
            cloudService.registerUser(
                handle = _uiState.value.myHandle,
                displayName = "User ${_uiState.value.myHandle}",
                publicKey = newKey
            )
        }
    }

    fun setAuthDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isAuthDialogOpen = open, authErrorMessage = null) }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authErrorMessage = null) }
    }

    fun signUpWithEmail(email: String, pass: String, handle: String?, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || pass.length < 6) {
            val msg = if (pass.length < 6) "Password must be at least 6 characters" else "Please enter a valid email"
            _uiState.update { it.copy(authErrorMessage = msg) }
            onResult(false, msg)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
            val cleanHandle = handle?.trim()?.replace("@", "")?.ifEmpty { null }
            val result = authService.signUpWithEmail(email, pass, cleanHandle ?: email.substringBefore("@"))
            _uiState.update { it.copy(isAuthLoading = false) }

            result.fold(
                onSuccess = { user ->
                    if (!cleanHandle.isNullOrBlank()) {
                        updateMyHandle(cleanHandle)
                    }
                    _uiState.update { it.copy(isAuthDialogOpen = false) }
                    onResult(true, null)
                },
                onFailure = { error ->
                    val msg = error.localizedMessage ?: "Sign-up failed"
                    _uiState.update { it.copy(authErrorMessage = msg) }
                    onResult(false, msg)
                }
            )
        }
    }

    fun signInWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            val msg = "Please enter email and password"
            _uiState.update { it.copy(authErrorMessage = msg) }
            onResult(false, msg)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
            val result = authService.signInWithEmail(email, pass)
            _uiState.update { it.copy(isAuthLoading = false) }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isAuthDialogOpen = false) }
                    onResult(true, null)
                },
                onFailure = { error ->
                    val msg = error.localizedMessage ?: "Sign-in failed"
                    _uiState.update { it.copy(authErrorMessage = msg) }
                    onResult(false, msg)
                }
            )
        }
    }

    fun signInWithGoogle(context: Context, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
            val result = authService.signInWithGoogle(context)
            _uiState.update { it.copy(isAuthLoading = false) }

            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(isAuthDialogOpen = false) }
                    onResult(true, null)
                },
                onFailure = { error ->
                    val msg = error.localizedMessage ?: "Google Sign-In failed"
                    _uiState.update { it.copy(authErrorMessage = msg) }
                    onResult(false, msg)
                }
            )
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank()) {
            val msg = "Please enter your account email"
            _uiState.update { it.copy(authErrorMessage = msg) }
            onResult(false, msg)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
            val result = authService.sendPasswordReset(email)
            _uiState.update { it.copy(isAuthLoading = false) }

            result.fold(
                onSuccess = {
                    onResult(true, "Password reset email sent!")
                },
                onFailure = { error ->
                    val msg = error.localizedMessage ?: "Failed to send reset email"
                    _uiState.update { it.copy(authErrorMessage = msg) }
                    onResult(false, msg)
                }
            )
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        cloudService.stopListener()
        authService.signOut()
        _uiState.update {
            it.copy(
                authUser = null,
                isGuestUser = false,
                myHandle = "",
                activeConversationId = null,
                activeCall = null
            )
        }
    }

    fun panicWipeAllData() {
        prefs.edit().clear().apply()
        cloudService.stopListener()
        authService.signOut()
        viewModelScope.launch {
            securityPrefs.clearSecuritySettings()
            repository.panicWipeVault()
            _uiState.update {
                it.copy(
                    authUser = null,
                    isGuestUser = false,
                    myHandle = "",
                    activeConversationId = null,
                    activeCall = null,
                    isAppLocked = false,
                    isPinEnabled = false,
                    pinCode = ""
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cloudService.stopListener()
        callTimerJob?.cancel()
        voiceRecordJob?.cancel()
        cleanupTimerJob?.cancel()
    }
}
