package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.CryptoHelper
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.remote.CloudStatus
import com.example.data.remote.FirebaseCloudService
import com.example.data.repository.MessengerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MainTab {
    CHATS,
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
    val isKeyFingerprintDialogOpen: Boolean = false,
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
    val myHandle: String = "whisper.7029",
    val myPublicKey: String = "ECDH-P256: 4F91B2E6AA1998C1",
    val cloudStatus: CloudStatus = CloudStatus.Checking,
    val isSearchingCloud: Boolean = false
)

class MessengerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessengerRepository
    private val cloudService: FirebaseCloudService
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var callTimerJob: Job? = null
    private var voiceRecordJob: Job? = null
    private var cleanupTimerJob: Job? = null

    init {
        val db = AppDatabase.getDatabase(application, viewModelScope)
        cloudService = FirebaseCloudService(application, db.messengerDao(), viewModelScope)
        repository = MessengerRepository(db.messengerDao(), cloudService)

        // Observe Cloud Status
        viewModelScope.launch {
            cloudService.cloudStatus.collect { status ->
                _uiState.update { it.copy(cloudStatus = status) }
            }
        }

        // Register default handle in Cloud
        viewModelScope.launch {
            cloudService.registerUser(
                handle = _uiState.value.myHandle,
                displayName = "User ${_uiState.value.myHandle}",
                publicKey = _uiState.value.myPublicKey
            )
        }

        // Start periodic cleanup for disappearing messages
        cleanupTimerJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                repository.purgeExpiredMessages()
            }
        }
    }

    val conversations: StateFlow<List<ConversationEntity>> = repository.activeConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        return repository.getConversation(conversationId)
    }

    fun setTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun openConversation(conversationId: Long) {
        _uiState.update { it.copy(activeConversationId = conversationId, replyingToMessage = null) }
        viewModelScope.launch {
            repository.markConversationRead(conversationId)
        }
    }

    fun closeConversation() {
        _uiState.update { it.copy(activeConversationId = null, replyingToMessage = null) }
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
        val reply = _uiState.value.replyingToMessage
        val currentHandle = _uiState.value.myHandle

        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                text = text,
                isDisappearing = isDisappearing,
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
        viewModelScope.launch {
            repository.clearChatMessages(conversationId)
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
                val contact = ContactEntity(
                    handle = cloudUser.handle,
                    name = cloudUser.displayName,
                    avatarBgHex = cloudUser.avatarBgHex,
                    avatarTextHex = cloudUser.avatarTextHex,
                    publicKey = cloudUser.publicKey,
                    isVerified = true,
                    about = cloudUser.about
                )
                repository.addContact(contact)
                val convId = repository.createOrGetConversationForContact(contact)
                setNewChatDialogOpen(false)
                openConversation(convId)
                onResult(true, "Connected with @${cloudUser.handle}")
            } else {
                // If not found in cloud, still allow adding as local contact
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
        viewModelScope.launch {
            repository.logCall(peer.peerName, peer.peerHandle, peer.avatarBgColorHex, peer.avatarTextColorHex, isVideo)
        }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isCallMuted = !it.isCallMuted) }
    }

    fun toggleSpeaker() {
        _uiState.update { it.copy(isCallSpeaker = !it.isCallSpeaker) }
    }

    fun endCall() {
        callTimerJob?.cancel()
        _uiState.update { it.copy(activeCall = null, callDurationSeconds = 0) }
    }

    fun postStatus(caption: String) {
        if (caption.isBlank()) return
        viewModelScope.launch {
            repository.addStatus(caption)
        }
    }

    fun togglePreventScreenshots(prevent: Boolean) {
        _uiState.update { it.copy(preventScreenshots = prevent) }
    }

    fun setPin(pin: String) {
        _uiState.update { it.copy(pinCode = pin, isPinEnabled = pin.isNotEmpty(), isAppLocked = false) }
    }

    fun disablePin() {
        _uiState.update { it.copy(pinCode = "", isPinEnabled = false, isAppLocked = false) }
    }

    fun unlockWithPin(entered: String): Boolean {
        if (entered == _uiState.value.pinCode || _uiState.value.pinCode.isEmpty()) {
            _uiState.update { it.copy(isAppLocked = false) }
            return true
        }
        return false
    }

    fun lockApp() {
        if (_uiState.value.isPinEnabled) {
            _uiState.update { it.copy(isAppLocked = true) }
        }
    }

    fun updateMyHandle(newHandle: String) {
        val clean = if (newHandle.startsWith("@")) newHandle.substring(1) else newHandle
        if (clean.isNotBlank()) {
            val trimmed = clean.trim()
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

    fun panicWipeAllData() {
        viewModelScope.launch {
            repository.panicWipeVault()
            _uiState.update {
                it.copy(
                    activeConversationId = null,
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

