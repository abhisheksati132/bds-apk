package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CleanBottomNavBar
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MessengerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MessengerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val conversations by viewModel.conversations.collectAsStateWithLifecycle()
                val contacts by viewModel.contacts.collectAsStateWithLifecycle()
                val statuses by viewModel.statuses.collectAsStateWithLifecycle()
                val calls by viewModel.calls.collectAsStateWithLifecycle()

                // Dynamically apply FLAG_SECURE for screenshot blocking
                LaunchedEffect(uiState.preventScreenshots) {
                    if (uiState.preventScreenshots) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                // Security PIN Lock Screen
                if (uiState.isAppLocked) {
                    PinLockScreen(
                        onUnlock = { pin -> viewModel.unlockWithPin(pin) }
                    )
                } else if (uiState.activeCall != null) {
                    // Active Encrypted Call Screen
                    ActiveCallScreen(
                        peer = uiState.activeCall!!,
                        isVideo = uiState.isVideoCall,
                        durationSeconds = uiState.callDurationSeconds,
                        isMuted = uiState.isCallMuted,
                        isSpeaker = uiState.isCallSpeaker,
                        onToggleMute = { viewModel.toggleMute() },
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onEndCall = { viewModel.endCall() }
                    )
                } else if (uiState.activeConversationId != null) {
                    // Active Conversation Screen
                    val currentConv by viewModel.currentConversation(uiState.activeConversationId!!)
                        .collectAsStateWithLifecycle(initialValue = null)
                    val messages by viewModel.currentMessages(uiState.activeConversationId!!)
                        .collectAsStateWithLifecycle(initialValue = emptyList())

                    BackHandler {
                        viewModel.closeConversation()
                    }

                    if (currentConv != null) {
                        ConversationScreen(
                            conversation = currentConv!!,
                            messages = messages,
                            uiState = uiState,
                            onBack = { viewModel.closeConversation() },
                            onSendMessage = { text, isDisappearing, timer ->
                                viewModel.sendMessage(
                                    conversationId = currentConv!!.id,
                                    text = text,
                                    isDisappearing = isDisappearing,
                                    disappearingTimerSeconds = timer
                                )
                            },
                            onStartVoiceRecording = { viewModel.startVoiceRecording() },
                            onCancelVoiceRecording = { viewModel.cancelVoiceRecording() },
                            onFinishVoiceRecording = { convId, isDisappearing, timer ->
                                viewModel.finishVoiceRecording(convId, isDisappearing, timer)
                            },
                            onStartCall = { peer, isVideo -> viewModel.startCall(peer, isVideo) },
                            onUpdateDisappearingTimer = { convId, seconds ->
                                viewModel.updateDisappearingTimer(convId, seconds)
                            },
                            onClearChat = { convId -> viewModel.clearChat(convId) },
                            onDeleteConversation = { convId -> viewModel.deleteConversation(convId) },
                            onSetReplyTo = { msg -> viewModel.setReplyingTo(msg) },
                            onOpenFingerprint = { viewModel.setKeyFingerprintDialogOpen(true) }
                        )

                        // Safety number fingerprint dialog
                        if (uiState.isKeyFingerprintDialogOpen) {
                            FingerprintDialog(
                                conversation = currentConv!!,
                                myPublicKey = uiState.myPublicKey,
                                onDismiss = { viewModel.setKeyFingerprintDialogOpen(false) }
                            )
                        }
                    }
                } else {
                    // Main Tabs Navigation
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            CleanBottomNavBar(
                                activeTab = uiState.activeTab,
                                onTabSelected = { tab -> viewModel.setTab(tab) }
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (uiState.activeTab) {
                                MainTab.CHATS -> {
                                    ChatListScreen(
                                        uiState = uiState,
                                        conversations = conversations,
                                        onOpenConversation = { id -> viewModel.openConversation(id) },
                                        onOpenNewChat = { viewModel.setNewChatDialogOpen(true) },
                                        onOpenVault = { viewModel.setTab(MainTab.VAULT_SECURITY) },
                                        onSearchQueryChanged = { q -> viewModel.setSearchQuery(q) },
                                        onFilterSelected = { f -> viewModel.setFilter(f) }
                                    )
                                }
                                MainTab.STATUS -> {
                                    StatusScreen(
                                        statuses = statuses,
                                        onPostStatus = { text -> viewModel.postStatus(text) }
                                    )
                                }
                                MainTab.CALLS -> {
                                    CallsScreen(
                                        calls = calls,
                                        conversations = conversations,
                                        onStartCall = { peer, isVideo -> viewModel.startCall(peer, isVideo) }
                                    )
                                }
                                MainTab.VAULT_SECURITY -> {
                                    VaultSecurityScreen(
                                        uiState = uiState,
                                        onTogglePreventScreenshots = { p -> viewModel.togglePreventScreenshots(p) },
                                        onSetPin = { pin -> viewModel.setPin(pin) },
                                        onDisablePin = { viewModel.disablePin() },
                                        onLockAppNow = { viewModel.lockApp() },
                                        onPanicWipeData = { viewModel.panicWipeAllData() },
                                        onUpdateHandle = { handle -> viewModel.updateMyHandle(handle) },
                                        onRotateKeys = { viewModel.rotateMyKeys() }
                                    )
                                }
                            }
                        }

                        // Dialog to start new chat
                        if (uiState.isNewChatDialogOpen) {
                            NewChatDialog(
                                contacts = contacts,
                                onSelectContact = { contact ->
                                    viewModel.startNewChatWithContact(contact)
                                },
                                onCreateContactAndChat = { name, handle ->
                                    viewModel.createCustomContactAndChat(name, handle)
                                },
                                onSearchCloudPeer = { handle, onResult ->
                                    viewModel.searchAndAddCloudPeer(handle, onResult)
                                },
                                isSearchingCloud = uiState.isSearchingCloud,
                                onDismiss = { viewModel.setNewChatDialogOpen(false) }
                            )
                        }
                    }
                }
            }
        }
    }
}
