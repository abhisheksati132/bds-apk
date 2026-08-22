package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.AvatarView
import com.example.ui.viewmodel.UiState
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
    onSendMessage: (String, Boolean, Long) -> Unit,
    onStartVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onFinishVoiceRecording: (Long, Boolean, Long) -> Unit,
    onStartCall: (ConversationEntity, Boolean) -> Unit,
    onUpdateDisappearingTimer: (Long, Long) -> Unit,
    onClearChat: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onSetReplyTo: (MessageEntity?) -> Unit,
    onOpenFingerprint: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var selectedMessageForCipher by remember { mutableStateOf<MessageEntity?>(null) }
    var isDisappearingMode by remember { mutableStateOf(conversation.disappearingTimerSeconds > 0) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
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
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypted",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = if (uiState.isPeerTyping) "typing encrypted message..."
                                else if (conversation.isOnline) "Online • Verified"
                                else "@${conversation.peerHandle}",
                                fontSize = 11.sp,
                                color = if (uiState.isPeerTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    IconButton(
                        onClick = { onStartCall(conversation, false) },
                        modifier = Modifier.testTag("btn_audio_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Encrypted Voice Call",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onStartCall(conversation, true) },
                        modifier = Modifier.testTag("btn_video_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Encrypted Video Call",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("btn_conversation_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Disappearing Messages (${if (conversation.disappearingTimerSeconds > 0) "${conversation.disappearingTimerSeconds}s" else "Off"})") },
                                onClick = {
                                    showMenu = false
                                    showTimerDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Security Fingerprint") },
                                onClick = {
                                    showMenu = false
                                    onOpenFingerprint()
                                },
                                leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Chat History") },
                                onClick = {
                                    showMenu = false
                                    onClearChat(conversation.id)
                                },
                                leadingIcon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete & Burn Conversation", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteConversation(conversation.id)
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // E2E Encryption Banner
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
                        text = "End-to-End Encrypted (AES-256) • No Plaintext Saved",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Disappearing Timer Active Pill
            if (conversation.disappearingTimerSeconds > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Disappearing timer active (${conversation.disappearingTimerSeconds}s)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(
                        message = message,
                        onReply = { onSetReplyTo(message) },
                        onInspectCipher = { selectedMessageForCipher = message }
                    )
                }

                if (uiState.isPeerTyping) {
                    item {
                        TypingIndicatorBubble(peerName = conversation.peerName)
                    }
                }
            }

            // Replying preview banner
            AnimatedVisibility(
                visible = uiState.replyingToMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
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
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = replyMsg.text,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onSetReplyTo(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Input Bottom Area
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (uiState.isRecordingVoice) {
                        // Live Voice Recording Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                                Text(
                                    text = "Recording 0:${String.format(Locale.getDefault(), "%02d", uiState.recordingSeconds)}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onCancelVoiceRecording) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                                }
                                IconButton(
                                    onClick = {
                                        onFinishVoiceRecording(
                                            conversation.id,
                                            isDisappearingMode || conversation.disappearingTimerSeconds > 0,
                                            conversation.disappearingTimerSeconds
                                        )
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .size(36.dp)
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
                        // Regular Message Input Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { showAttachmentSheet = true },
                                modifier = Modifier
                                    .testTag("btn_attach")
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Attach media",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        text = "Encrypted message...",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_message_field"),
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                maxLines = 4
                            )

                            if (inputText.isBlank()) {
                                IconButton(
                                    onClick = onStartVoiceRecording,
                                    modifier = Modifier
                                        .testTag("btn_mic_record")
                                        .size(40.dp)
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
                                                isDisappearingMode || conversation.disappearingTimerSeconds > 0,
                                                conversation.disappearingTimerSeconds
                                            )
                                            inputText = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .testTag("btn_send_message")
                                        .size(40.dp)
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

    // Disappearing Timer Picker Dialog
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Disappearing Messages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "New messages will automatically self-destruct for both participants after the timer expires.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    val options = listOf(
                        0L to "Off (Keep messages)",
                        30L to "30 Seconds ⚡",
                        300L to "5 Minutes",
                        3600L to "1 Hour",
                        86400L to "24 Hours"
                    )

                    options.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onUpdateDisappearingTimer(conversation.id, seconds)
                                    isDisappearingMode = seconds > 0
                                    showTimerDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, fontWeight = if (conversation.disappearingTimerSeconds == seconds) FontWeight.Bold else FontWeight.Normal)
                            if (conversation.disappearingTimerSeconds == seconds) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        icon = Icons.Default.CameraAlt,
                        label = "View-Once Photo",
                        bgColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = {
                            showAttachmentSheet = false
                            onSendMessage(
                                "📷 [Encrypted View-Once Photo]",
                                true,
                                30L
                            )
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.Lock,
                        label = "Secret Note",
                        bgColor = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = {
                            showAttachmentSheet = false
                            onSendMessage(
                                "🔒 [Encrypted Secret Note: Key Hash verified]",
                                false,
                                0L
                            )
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.Timer,
                        label = "Burn After Read",
                        bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = {
                            showAttachmentSheet = false
                            onSendMessage(
                                "⏳ [Burn-on-Read Message: 10s countdown]",
                                true,
                                10L
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
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
                    Text("Decrypted Local Text:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg.text,
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
                            text = if (msg.cipherText.isNotEmpty()) msg.cipherText else "[Generated on transmit]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.primary
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
    onInspectCipher: () -> Unit
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
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .clickable { onInspectCipher() }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
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
                        // Simulated waveform
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
                } else {
                    // Regular Text
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = textColor,
                        lineHeight = 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Footer with time, lock icon, and status ticks
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.isDisappearing) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Disappearing",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )

                    if (isMe) {
                        val (icon, tint) = when (message.status) {
                            MessageStatus.SENDING -> Icons.Default.AccessTime to textColor.copy(alpha = 0.6f)
                            MessageStatus.SENT -> Icons.Default.Check to textColor.copy(alpha = 0.7f)
                            MessageStatus.DELIVERED -> Icons.Default.DoneAll to textColor.copy(alpha = 0.7f)
                            MessageStatus.READ -> Icons.Default.DoneAll to Color(0xFF80D8FF)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Status",
                            tint = tint,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // Message Reaction Badge
        if (!message.reaction.isNullOrEmpty()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .offset(y = (-8).dp, x = if (isMe) (-8).dp else 8.dp)
            ) {
                Text(
                    text = message.reaction,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble(peerName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), repeatMode = RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), repeatMode = RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), repeatMode = RepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = dot1)))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = dot2)))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = dot3)))
    }
}
