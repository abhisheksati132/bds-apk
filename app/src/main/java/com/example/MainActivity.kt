package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.CallSignal
import com.example.ui.components.AvatarView
import com.example.ui.components.CleanBottomNavBar
import com.example.ui.dialogs.AppUpdateDialog
import com.example.ui.dialogs.ForwardMessageDialog
import com.example.ui.dialogs.UserProfileDialog
import com.example.ui.screens.*
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MessengerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MessengerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val conversations by viewModel.conversations.collectAsStateWithLifecycle()
            val contacts by viewModel.contacts.collectAsStateWithLifecycle()
            val statuses by viewModel.statuses.collectAsStateWithLifecycle()
            val calls by viewModel.calls.collectAsStateWithLifecycle()

            val isDarkTheme = uiState.isDarkMode ?: isSystemInDarkTheme()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                // Notification Permission for FCM (Android 13+)
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { /* FCM will dispatch messages */ }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Local Security Gate: Lock app whenever brought to the foreground
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, uiState.isPinEnabled, uiState.pinCode) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            if (uiState.isPinEnabled && uiState.pinCode.isNotBlank()) {
                                viewModel.lockApp()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

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

                // Unauthenticated State: Full-Screen Onboarding & Auth
                if (!uiState.isAuthenticated) {
                    AuthScreen(
                        isLoading = uiState.isAuthLoading,
                        errorMessage = uiState.authErrorMessage,
                        onSignUpWithEmail = { email, pass, handle, onResult ->
                            viewModel.signUpWithEmail(email, pass, handle, onResult)
                        },
                        onSignInWithEmail = { email, pass, onResult ->
                            viewModel.signInWithEmail(email, pass, onResult)
                        },
                        onSignInWithGoogle = { ctx, onResult ->
                            viewModel.signInWithGoogle(ctx, onResult)
                        },
                        onGuestLogin = { handle ->
                            viewModel.loginAsGuest(handle)
                        },
                        onSendPasswordReset = { email, onResult ->
                            viewModel.sendPasswordReset(email, onResult)
                        }
                    )
                } else if (uiState.isAppLocked) {
                    // Local Security Gate: 4-digit PIN lock screen
                    PinLockScreen(
                        onUnlock = { pin -> viewModel.unlockWithPin(pin) }
                    )
                } else if (uiState.activeCall != null && !uiState.isCallMinimized) {
                    // Active Call Screen (Full-Screen)
                    ActiveCallScreen(
                        peer = uiState.activeCall!!,
                        isVideo = uiState.isVideoCall,
                        durationSeconds = uiState.callDurationSeconds,
                        isMuted = uiState.isCallMuted,
                        isSpeaker = uiState.isCallSpeaker,
                        onToggleMute = { viewModel.toggleMute() },
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onEndCall = { viewModel.endCall() },
                        onMinimizeCall = { viewModel.setCallMinimized(true) }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = uiState.activeConversationId,
                            transitionSpec = {
                                if (targetState != null) {
                                    (slideInHorizontally(animationSpec = tween(320)) { it } + fadeIn(animationSpec = tween(320)))
                                        .togetherWith(slideOutHorizontally(animationSpec = tween(320)) { -it / 4 } + fadeOut(animationSpec = tween(320)))
                                } else {
                                    (slideInHorizontally(animationSpec = tween(320)) { -it / 4 } + fadeIn(animationSpec = tween(320)))
                                        .togetherWith(slideOutHorizontally(animationSpec = tween(320)) { it } + fadeOut(animationSpec = tween(320)))
                                }
                            },
                            label = "convTransition"
                        ) { activeConvId ->
                        if (activeConvId != null) {
                            // Responsive Live Conversation Screen
                            val currentConv by viewModel.currentConversation(activeConvId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val messages by viewModel.currentMessages(activeConvId)
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
                                    onSendImage = { uri, isDisappearing, timer ->
                                        viewModel.sendImage(
                                            conversationId = currentConv!!.id,
                                            imageUri = uri,
                                            isDisappearing = isDisappearing,
                                            disappearingTimerSeconds = timer
                                        )
                                    },
                                    onStartVoiceRecording = { viewModel.startVoiceRecording() },
                                    onCancelVoiceRecording = { viewModel.cancelVoiceRecording() },
                                    onFinishVoiceRecording = { duration, isDisappearing, timer ->
                                        viewModel.finishVoiceRecording(currentConv!!.id, isDisappearing, timer)
                                    },
                                    onStartCall = { peer, isVideo -> viewModel.startCall(peer, isVideo) },
                                    onUpdateDisappearingTimer = { convId, seconds ->
                                        viewModel.updateDisappearingTimer(convId, seconds)
                                    },
                                    onClearChat = { convId -> viewModel.clearChat(convId) },
                                    onDeleteConversation = { convId -> viewModel.deleteConversation(convId) },
                                    onSetReplyTo = { msg -> viewModel.setReplyingTo(msg) },
                                    onOpenFingerprint = { viewModel.setKeyFingerprintDialogOpen(true) },
                                    onReactToMessage = { msg, reaction -> viewModel.reactToMessage(msg, reaction) },
                                    onTypingChanged = { isTyping -> viewModel.sendTyping(currentConv!!.id, isTyping) },
                                    onForwardMessage = { msg -> viewModel.setForwardDialogOpen(true, msg) },
                                    onBlockUser = { handle -> viewModel.blockUser(handle) },
                                    onTogglePlayVoiceNote = { url -> viewModel.togglePlayVoiceNote(url) }
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
                            // Main Tabs Navigation (Chats, Contacts, Status, Calls, Vault)
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
                                    AnimatedContent(
                                        targetState = uiState.activeTab,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                                                .togetherWith(fadeOut(animationSpec = tween(180)))
                                        },
                                        label = "tabTransition"
                                    ) { currentTab ->
                                        when (currentTab) {
                                            MainTab.CHATS -> {
                                                ChatListScreen(
                                                    uiState = uiState,
                                                    conversations = conversations,
                                                    onOpenConversation = { id -> viewModel.openConversation(id) },
                                                    onOpenNewChat = { viewModel.setNewChatDialogOpen(true) },
                                                    onOpenVault = { viewModel.setTab(MainTab.VAULT_SECURITY) },
                                                    onOpenAuth = { viewModel.setAuthDialogOpen(true) },
                                                    onSearchQueryChanged = { q -> viewModel.setSearchQuery(q) },
                                                    onFilterSelected = { f -> viewModel.setFilter(f) }
                                                )
                                            }
                                            MainTab.CONTACTS -> {
                                                ContactsScreen(
                                                    contacts = contacts,
                                                    cloudUsers = uiState.cloudUsers,
                                                    myHandle = uiState.myHandle,
                                                    presenceMap = uiState.presenceMap,
                                                    blockedHandles = uiState.blockedHandles,
                                                    onStartChatWithContact = { contact ->
                                                         viewModel.startNewChatWithContact(contact)
                                                    },
                                                    onStartChatWithCloudUser = { cloudUser ->
                                                        viewModel.startNewChatWithCloudUser(cloudUser)
                                                    },
                                                    onStartCallWithUser = { name, handle, isVideo ->
                                                        viewModel.startCallWithUser(name, handle, isVideo)
                                                    },
                                                    onAddCustomContact = { name, handle ->
                                                        viewModel.createCustomContactAndChat(name, handle)
                                                    },
                                                    onSearchCloudPeer = { handle, onResult ->
                                                        viewModel.searchAndAddCloudPeer(handle, onResult)
                                                    },
                                                    onBlockUser = { handle -> viewModel.blockUser(handle) },
                                                    onUnblockUser = { handle -> viewModel.unblockUser(handle) },
                                                    isSearchingCloud = uiState.isSearchingCloud
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
                                                    onRotateKeys = { viewModel.rotateMyKeys() },
                                                    onOpenAuthDialog = { viewModel.setAuthDialogOpen(true) },
                                                    onToggleDarkMode = { mode -> viewModel.setDarkMode(mode) },
                                                    onOpenProfileDialog = { viewModel.setUserProfileDialogOpen(true) },
                                                    onToggleNotificationSounds = { enabled -> viewModel.toggleNotificationSound(enabled) },
                                                    onSetVibrationPattern = { pattern -> viewModel.setVibrationPattern(pattern) },
                                                    onTestVibration = { viewModel.testVibration() },
                                                    onCheckForUpdates = { viewModel.checkForUpdates(silent = false) },
                                                    onSetGithubUpdateToken = { token -> viewModel.setGithubUpdateToken(token) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating Mini-Call Overlay Pill (Multitasking PIP)
                        if (uiState.activeCall != null && uiState.isCallMinimized) {
                            val durationText = "${uiState.callDurationSeconds / 60}:${String.format(java.util.Locale.getDefault(), "%02d", uiState.callDurationSeconds % 60)}"
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable { viewModel.setCallMinimized(false) }
                                    .align(Alignment.TopCenter),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(SuccessGreen)
                                        )
                                        AvatarView(
                                            name = uiState.activeCall!!.peerName,
                                            bgHex = uiState.activeCall!!.avatarBgColorHex,
                                            textHex = uiState.activeCall!!.avatarTextColorHex,
                                            size = 32.dp,
                                            isOnline = true
                                        )
                                        Column {
                                            Text(
                                                text = uiState.activeCall!!.peerName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = durationText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.toggleMute() },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.isCallMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                                contentDescription = "Mute",
                                                tint = if (uiState.isCallMuted) ErrorRed else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.endCall() },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(ErrorRed)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CallEnd,
                                                contentDescription = "End Call",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                    // In-App Auto-Updater Dialog
                    if (uiState.availableUpdate != null || uiState.isDownloadingUpdate || uiState.isUpdateReadyToInstall) {
                        uiState.availableUpdate?.let { updateInfo ->
                            AppUpdateDialog(
                                releaseInfo = updateInfo,
                                currentVersion = uiState.currentAppVersion,
                                isDownloading = uiState.isDownloadingUpdate,
                                downloadProgress = uiState.updateDownloadProgress,
                                downloadBytesProgress = uiState.updateDownloadBytesProgress,
                                isReadyToInstall = uiState.isUpdateReadyToInstall,
                                onStartDownload = { viewModel.startDownloadingUpdate() },
                                onInstallDownloadedApk = { viewModel.installDownloadedUpdate() },
                                onDismiss = { viewModel.dismissUpdateDialog() }
                            )
                        }
                    }

                    // Dialog to start new chat
                    if (uiState.isNewChatDialogOpen) {
                        NewChatDialog(
                            contacts = contacts,
                            cloudUsers = uiState.cloudUsers,
                            onSelectContact = { contact ->
                                viewModel.startNewChatWithContact(contact)
                            },
                            onSelectCloudUser = { cloudUser ->
                                viewModel.startNewChatWithCloudUser(cloudUser)
                            },
                            onOpenCreateGroup = {
                                viewModel.setNewChatDialogOpen(false)
                                viewModel.setCreateGroupDialogOpen(true)
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

                    // Dialog to create new group chat
                    if (uiState.isCreateGroupDialogOpen) {
                        CreateGroupDialog(
                            contacts = contacts,
                            cloudUsers = uiState.cloudUsers,
                            myHandle = uiState.myHandle,
                            onCreateGroup = { name, members ->
                                viewModel.createGroupChat(name, members)
                            },
                            onDismiss = { viewModel.setCreateGroupDialogOpen(false) }
                        )
                    }

                    // Firebase Auth Dialog
                    if (uiState.isAuthDialogOpen) {
                        AuthDialog(
                            currentUser = uiState.authUser,
                            isLoading = uiState.isAuthLoading,
                            errorMessage = uiState.authErrorMessage,
                            onSignUpWithEmail = { email, pass, handle, onResult ->
                                viewModel.signUpWithEmail(email, pass, handle, onResult)
                            },
                            onSignInWithEmail = { email, pass, onResult ->
                                viewModel.signInWithEmail(email, pass, onResult)
                            },
                            onSignInWithGoogle = { ctx, onResult ->
                                viewModel.signInWithGoogle(ctx, onResult)
                            },
                            onSendPasswordReset = { email, onResult ->
                                viewModel.sendPasswordReset(email, onResult)
                            },
                            onSignOut = { viewModel.signOut() },
                            onDismiss = { viewModel.setAuthDialogOpen(false) }
                        )
                    }

                    // Real-time Incoming Call Dialog
                    val currentIncomingCall = uiState.incomingCall
                    if (currentIncomingCall != null) {
                        AlertDialog(
                            onDismissRequest = { viewModel.rejectIncomingCall() },
                            icon = {
                                Icon(
                                    imageVector = if (currentIncomingCall.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = {
                                Text("Incoming ${if (currentIncomingCall.isVideo) "Video" else "Voice"} Call", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text(currentIncomingCall.callerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("@${currentIncomingCall.callerHandle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.acceptIncomingCall() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) {
                                    Text("Accept")
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { viewModel.rejectIncomingCall() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Decline")
                                }
                            }
                        )
                    }

                    // User Profile & Avatar Customization Dialog
                    if (uiState.isUserProfileDialogOpen) {
                        UserProfileDialog(
                            uiState = uiState,
                            onDismiss = { viewModel.setUserProfileDialogOpen(false) },
                            onUploadAvatar = { uri -> viewModel.uploadAndSetAvatar(uri) },
                            onUpdateProfile = { name, about, bgHex, textHex ->
                                viewModel.updateProfile(name, about, bgHex, textHex)
                            },
                            onRemoveAvatar = { viewModel.removeAvatar() }
                        )
                    }

                    // Forward Message Dialog
                    val messageToForward = uiState.forwardingMessage
                    if (messageToForward != null) {
                        ForwardMessageDialog(
                            messageToForward = messageToForward,
                            conversations = conversations,
                            currentConversationId = uiState.activeConversationId,
                            onDismiss = { viewModel.setForwardDialogOpen(false) },
                            onSelectTargetConversation = { targetConvId ->
                                viewModel.forwardMessageTo(targetConvId, messageToForward)
                            }
                        )
                    }
                }
            }
        }
    }
}
