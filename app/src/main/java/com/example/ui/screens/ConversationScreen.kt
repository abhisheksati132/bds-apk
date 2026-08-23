package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.*
import com.example.ui.components.AvatarView
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversation: ConversationEntity,
    messages: List<MessageEntity>,
    uiState: UiState,
    onBack: () -> Unit,
    onSendMessage: (text: String, isDisappearing: Boolean, disappearingSeconds: Long) -> Unit,
    onSendImage: (uri: Uri, isDisappearing: Boolean, disappearingSeconds: Long) -> Unit = { _, _, _ -> },
    onStartVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onFinishVoiceRecording: (durationSeconds: Long, isDisappearing: Boolean, disappearingSeconds: Long) -> Unit,
    onStartCall: (ConversationEntity, isVideo: Boolean) -> Unit,
    onUpdateDisappearingTimer: (conversationId: Long, timerSeconds: Long) -> Unit,
    onClearChat: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onSetReplyTo: (MessageEntity?) -> Unit,
    onOpenFingerprint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val MAX_CHAR_LIMIT = 500
    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var selectedMessageForCipher by remember { mutableStateOf<MessageEntity?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    
    // Per-message disappearing duration: 0 = off, 5 = 5s, 60 = 1m, 3600 = 1h, 86400 = 24h
    var currentDisappearingTimer by remember(conversation.disappearingTimerSeconds) {
        mutableStateOf(conversation.disappearingTimerSeconds)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Gallery image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onSendImage(
                uri,
                currentDisappearingTimer > 0,
                currentDisappearingTimer
            )
        }
    }

    // Auto-scroll on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val memberCount = remember(conversation.groupMembers) {
        if (conversation.isGroup && !conversation.groupMembers.isNullOrEmpty()) {
            conversation.groupMembers.split(",").filter { it.isNotBlank() }.size
        } else 1
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_conversation"),
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("conversation_top_bar"),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable { onOpenFingerprint() }
                    ) {
                        AvatarView(
                            name = conversation.peerName,
                            bgHex = conversation.avatarBgColorHex,
                            textHex = conversation.avatarTextColorHex,
                            size = 40.dp,
                            isOnline = conversation.isOnline
                        )

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = conversation.peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (conversation.isGroup) Icons.Default.Groups else Icons.Default.Lock,
                                    contentDescription = if (conversation.isGroup) "Group" else "Encrypted",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = if (uiState.isPeerTyping) "typing encrypted message..."
                                else if (conversation.isGroup) "$memberCount participants • E2EE"
                                else if (conversation.isOnline) "Online • Active"
                                else "@${conversation.peerHandle}",
                                fontSize = 11.sp,
                                color = if (uiState.isPeerTyping || conversation.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_conversation")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Quick Disappearing Timer in Top Bar
                    IconButton(
                        onClick = { showTimerDialog = true },
                        modifier = Modifier.testTag("btn_disappearing_timer_top")
                    ) {
                        BadgedBox(
                            badge = {
                                if (currentDisappearingTimer > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(
                                            text = formatTimerShort(currentDisappearingTimer),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentDisappearingTimer > 0) Icons.Default.Timer else Icons.Outlined.Timer,
                                contentDescription = "Disappearing timer",
                                tint = if (currentDisappearingTimer > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Voice Call
                    IconButton(
                        onClick = { onStartCall(conversation, false) },
                        modifier = Modifier.testTag("btn_voice_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Encrypted Voice Call",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Video Call
                    IconButton(
                        onClick = { onStartCall(conversation, true) },
                        modifier = Modifier.testTag("btn_video_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Encrypted Video Call",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Overflow Menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Disappearing Messages") },
                                onClick = {
                                    showMenu = false
                                    showTimerDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Timer, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Encryption Fingerprint") },
                                onClick = {
                                    showMenu = false
                                    onOpenFingerprint()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Chat Messages") },
                                onClick = {
                                    showMenu = false
                                    onClearChat(conversation.id)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete Conversation", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteConversation(conversation.id)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // E2EE Security Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (conversation.isGroup) "AES-256 E2EE Group Stream" else "AES-256 E2EE Live Channel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (currentDisappearingTimer > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "⏳ ${formatTimerShort(currentDisappearingTimer)} self-destruct",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (conversation.isGroup) Icons.Default.Groups else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (conversation.isGroup) "End-to-End Encrypted Group" else "End-to-End Encrypted Live Chat",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Messages and photos are zero-knowledge encrypted and synced in real-time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onReply = { onSetReplyTo(message) },
                        onInspectCipher = { selectedMessageForCipher = message },
                        onImageClick = { url -> previewImageUrl = url }
                    )
                }
            }

            // Reply Banner
            AnimatedVisibility(visible = uiState.replyingToMessage != null) {
                uiState.replyingToMessage?.let { replyMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = if (replyMsg.isMe) "Replying to yourself" else "Replying to ${conversation.peerName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = replyMsg.text,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onSetReplyTo(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel reply",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Per-Message Disappearing Quick Selector Strip
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (currentDisappearingTimer > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Disappearing:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val durations = listOf(
                        0L to "Off",
                        5L to "5s",
                        60L to "1m",
                        3600L to "1h",
                        86400L to "24h"
                    )

                    durations.forEach { (seconds, label) ->
                        val isSelected = currentDisappearingTimer == seconds
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                currentDisappearingTimer = seconds
                                onUpdateDisappearingTimer(conversation.id, seconds)
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Bottom Composer Input Bar with Image, Voice & Live Character Count
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    if (uiState.isRecordingVoice) {
                        // Voice Recording State
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Text(
                                    text = "Recording 0:${String.format(Locale.getDefault(), "%02d", uiState.recordingSeconds)}",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onCancelVoiceRecording) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                                }

                                IconButton(
                                    onClick = {
                                        onFinishVoiceRecording(
                                            uiState.recordingSeconds.toLong(),
                                            currentDisappearingTimer > 0,
                                            currentDisappearingTimer
                                        )
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send voice",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Regular Message & Media Input Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Media & Attachment Action
                            IconButton(
                                onClick = { showAttachmentSheet = true },
                                modifier = Modifier
                                    .testTag("btn_attach")
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Attach media",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quick Photo Gallery Button
                            IconButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .testTag("btn_quick_photo")
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Select photo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                TextField(
                                    value = inputText,
                                    onValueChange = {
                                        if (it.length <= MAX_CHAR_LIMIT) {
                                            inputText = it
                                        }
                                    },
                                    placeholder = {
                                        Text(
                                            text = if (currentDisappearingTimer > 0) "Disappearing (${formatTimerShort(currentDisappearingTimer)}) message..." else "Encrypted message...",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_message_field"),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    maxLines = 4
                                )

                                // Live Character Count Display
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    val count = inputText.length
                                    val isNearLimit = count > 450
                                    Text(
                                        text = "$count / $MAX_CHAR_LIMIT",
                                        fontSize = 10.sp,
                                        fontWeight = if (isNearLimit) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            count >= MAX_CHAR_LIMIT -> MaterialTheme.colorScheme.error
                                            isNearLimit -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        }
                                    )
                                }
                            }

                            if (inputText.isBlank()) {
                                IconButton(
                                    onClick = onStartVoiceRecording,
                                    modifier = Modifier
                                        .testTag("btn_mic_record")
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice note",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        val text = inputText.trim()
                                        if (text.isNotEmpty()) {
                                            onSendMessage(
                                                text,
                                                currentDisappearingTimer > 0,
                                                currentDisappearingTimer
                                            )
                                            inputText = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .testTag("btn_send_message")
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
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
    }

    // Attachment Options Sheet
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Encrypted Attachments",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentOption(
                        icon = Icons.Default.Image,
                        label = "Gallery Photo",
                        bgColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = {
                            showAttachmentSheet = false
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.CameraAlt,
                        label = "5s View-Once",
                        bgColor = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = {
                            showAttachmentSheet = false
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.Timer,
                        label = "Burn After 1m",
                        bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = {
                            showAttachmentSheet = false
                            onSendMessage(
                                "⏳ [Burn-on-Read Message: 1m countdown]",
                                true,
                                60L
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Disappearing Timer Dialog
    if (showTimerDialog) {
        val timerOptions = listOf(
            0L to "Off (Permanent)",
            5L to "5 Seconds (Burn-on-Read)",
            30L to "30 Seconds",
            60L to "1 Minute",
            3600L to "1 Hour",
            86400L to "24 Hours"
        )

        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Disappearing Messages")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "When enabled, newly sent and received messages will automatically self-destruct from Firestore and local device memory after the selected duration:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    timerOptions.forEach { (seconds, label) ->
                        val isSelected = currentDisappearingTimer == seconds
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentDisappearingTimer = seconds
                                    onUpdateDisappearingTimer(conversation.id, seconds)
                                    showTimerDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) { Text("Close") }
            }
        )
    }

    // Image Preview Fullscreen Dialog
    previewImageUrl?.let { url ->
        Dialog(onDismissRequest = { previewImageUrl = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Encrypted Media Preview", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { previewImageUrl = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Encrypted image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }

    // Ciphertext Inspection Dialog
    selectedMessageForCipher?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForCipher = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("AES-256 Ciphertext Inspection")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Decrypted Local Content:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (msg.type == MessageType.IMAGE) "📷 Photo Attachment (${msg.mediaUrl ?: "local"})" else msg.text,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 13.sp
                        )
                    }

                    Text("Stored Database Cipher (AES-CBC):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (msg.cipherText.isNotEmpty()) msg.cipherText else "[Direct Encrypted Media Payload]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (msg.isDisappearing && msg.expiresAtTimestamp != null) {
                        val remainingMs = msg.expiresAtTimestamp - System.currentTimeMillis()
                        val remainingSec = (remainingMs / 1000).coerceAtLeast(0)
                        Text(
                            text = "⏳ Self-destruct in: ${remainingSec}s",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForCipher = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    onReply: () -> Unit,
    onInspectCipher: () -> Unit,
    onImageClick: (String) -> Unit = {}
) {
    val isMe = message.isMe
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isMe) Alignment.End else Alignment.Start

    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    // Dynamic timer ticker for disappearing message
    var remainingSeconds by remember(message.expiresAtTimestamp) {
        mutableStateOf(
            if (message.expiresAtTimestamp != null) {
                ((message.expiresAtTimestamp - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            } else 0L
        )
    }

    if (message.isDisappearing && message.expiresAtTimestamp != null) {
        LaunchedEffect(message.expiresAtTimestamp) {
            while (true) {
                val rem = ((message.expiresAtTimestamp - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                remainingSeconds = rem
                if (rem <= 0) break
                delay(1000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .clickable { onInspectCipher() }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Sender label if in group chat and not me
                if (!isMe && !message.senderName.isNullOrEmpty() && message.senderName != "You") {
                    Text(
                        text = message.senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Quoted reply if exists
                if (!message.replyToText.isNullOrEmpty()) {
                    Surface(
                        color = if (isMe) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = message.replyToText,
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Image Message View
                if (message.type == MessageType.IMAGE && !message.mediaUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(message.mediaUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Encrypted image attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(message.mediaUrl) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Voice Message View
                if (message.type == MessageType.VOICE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play voice note",
                            tint = textColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(12, 20, 15, 24, 18, 10, 22, 16, 8, 14, 20, 12).forEach { height ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(height.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(textColor.copy(alpha = 0.7f))
                                )
                            }
                        }
                        Text(
                            text = "0:${String.format(Locale.getDefault(), "%02d", message.voiceDurationSeconds.coerceAtLeast(1))}",
                            fontSize = 11.sp,
                            color = textColor
                        )
                    }
                } else if (message.type != MessageType.IMAGE) {
                    // Regular Text
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = textColor
                    )
                }

                // Bottom Metadata: Time, Disappearing Badge, and Read Receipt Status Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Disappearing countdown badge
                    if (message.isDisappearing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Disappearing",
                                tint = if (isMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${remainingSeconds}s",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Timestamp
                    Text(
                        text = formatMessageTime(message.timestamp),
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )

                    // Real-time Read Receipt Status Indicators (SENDING, SENT, DELIVERED, READ/SEEN)
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Sending",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = textColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.DELIVERED -> {
                                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Delivered",
                                        tint = textColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = textColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            MessageStatus.READ -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Seen / Read",
                                        tint = Color(0xFF03A9F4),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTimerShort(seconds: Long): String {
    return when {
        seconds <= 0 -> "Off"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }
}
