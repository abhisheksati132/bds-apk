package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.*
import com.example.ui.components.AvatarView
import com.example.ui.components.ChatWallpaperBackground
import com.example.ui.components.MediaLightboxViewer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class ChatWallpaper {
    MINIMAL,
    SOFT_GRADIENT,
    SLATE_GRID,
    MIDNIGHT
}

fun formatMessageDateHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) -> "Today"
        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) -> SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = cursor.getString(index)
            }
        }
    }
    if (name == null) {
        name = uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }
    return name
}

fun getFileSize(context: Context, uri: Uri): Long {
    var size = 0L
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index != -1) size = cursor.getLong(index)
            }
        }
    }
    return size
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    conversation: ConversationEntity,
    messages: List<MessageEntity>,
    uiState: UiState,
    onBack: () -> Unit,
    onSendMessage: (text: String, isDisappearing: Boolean, disappearingSeconds: Long) -> Unit,
    onSendImage: (uri: Uri, isDisappearing: Boolean, disappearingSeconds: Long) -> Unit = { _, _, _ -> },
    onSendDocument: (uri: Uri, fileName: String, size: Long, isDisappearing: Boolean, disappearingSeconds: Long) -> Unit = { _, _, _, _, _ -> },
    onStartVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onFinishVoiceRecording: (durationSeconds: Long, isDisappearing: Boolean, disappearingSeconds: Long) -> Unit,
    onStartCall: (ConversationEntity, isVideo: Boolean) -> Unit,
    onUpdateDisappearingTimer: (conversationId: Long, timerSeconds: Long) -> Unit,
    onClearChat: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onDeleteMessage: (MessageEntity, Boolean) -> Unit = { _, _ -> },
    onSetReplyTo: (MessageEntity?) -> Unit,
    onStartEditingMessage: (MessageEntity) -> Unit = {},
    onCancelEditingMessage: () -> Unit = {},
    onSubmitEditedMessage: (Long, String) -> Unit = { _, _ -> },
    onTogglePinMessage: (MessageEntity) -> Unit = {},
    onOpenFingerprint: () -> Unit,
    onReactToMessage: (MessageEntity, String?) -> Unit = { _, _ -> },
    onTypingChanged: (Boolean) -> Unit = {},
    onForwardMessage: (MessageEntity) -> Unit = {},
    onBlockUser: (String) -> Unit = {},
    onTogglePlayVoiceNote: (String?) -> Unit = {},
    onCycleAudioSpeed: () -> Unit = {},
    onSeekAudio: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val MAX_CHAR_LIMIT = 500
    var inputText by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var inChatSearchQuery by remember { mutableStateOf("") }
    var currentSearchMatchIndex by remember { mutableIntStateOf(0) }
    var currentPinnedIndex by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showSharedMediaSheet by remember { mutableStateOf(false) }
    var selectedWallpaper by remember { mutableStateOf(ChatWallpaper.MINIMAL) }
    var selectedMessageForCipher by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedMessageForReaction by remember { mutableStateOf<MessageEntity?>(null) }
    var messageToDelete by remember { mutableStateOf<MessageEntity?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val pinnedMessages = remember(messages) { messages.filter { it.isPinned } }

    val matchedMessageIndices = remember(messages, inChatSearchQuery) {
        if (inChatSearchQuery.isBlank()) emptyList<Int>()
        else messages.mapIndexedNotNull { index, msg ->
            if (msg.text.contains(inChatSearchQuery, ignoreCase = true)) index else null
        }
    }

    var currentDisappearingTimer by remember(conversation.disappearingTimerSeconds) {
        mutableStateOf(conversation.disappearingTimerSeconds)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Real-time typing debouncer
    LaunchedEffect(inputText) {
        if (inputText.isNotBlank()) {
            onTypingChanged(true)
            delay(2500)
            onTypingChanged(false)
        } else {
            onTypingChanged(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onTypingChanged(false)
        }
    }

    // Gallery image picker
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

    // Document / File picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(context, uri) ?: "Document"
            val fileSize = getFileSize(context, uri)
            onSendDocument(
                uri,
                fileName,
                fileSize,
                currentDisappearingTimer > 0,
                currentDisappearingTimer
            )
        }
    }

    // Audio recording permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartVoiceRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    // Sync input text when entering edit mode
    LaunchedEffect(uiState.editingMessage) {
        if (uiState.editingMessage != null) {
            inputText = uiState.editingMessage.text
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

    val wallpaperModifier = when (selectedWallpaper) {
        ChatWallpaper.MINIMAL -> Modifier.background(MaterialTheme.colorScheme.background)
        ChatWallpaper.SOFT_GRADIENT -> Modifier.background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        )
        ChatWallpaper.SLATE_GRID -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ChatWallpaper.MIDNIGHT -> Modifier.background(Color(0xFF060608))
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_conversation"),
        topBar = {
            if (isSearchOpen) {
                TopAppBar(
                    modifier = Modifier.testTag("conversation_search_top_bar"),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchOpen = false
                            inChatSearchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        TextField(
                            value = inChatSearchQuery,
                            onValueChange = {
                                inChatSearchQuery = it
                                currentSearchMatchIndex = 0
                                if (it.isNotBlank()) {
                                    val matchIdx = messages.indexOfFirst { msg -> msg.text.contains(it, ignoreCase = true) }
                                    if (matchIdx != -1) {
                                        scope.launch { listState.animateScrollToItem(matchIdx) }
                                    }
                                }
                            },
                            placeholder = {
                                Text("Search in chat...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    actions = {
                        if (inChatSearchQuery.isNotBlank()) {
                            Text(
                                text = if (matchedMessageIndices.isEmpty()) "0/0" else "${currentSearchMatchIndex + 1}/${matchedMessageIndices.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            IconButton(
                                onClick = {
                                    if (matchedMessageIndices.isNotEmpty()) {
                                        currentSearchMatchIndex = (currentSearchMatchIndex - 1 + matchedMessageIndices.size) % matchedMessageIndices.size
                                        scope.launch { listState.animateScrollToItem(matchedMessageIndices[currentSearchMatchIndex]) }
                                    }
                                },
                                enabled = matchedMessageIndices.isNotEmpty()
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                            }

                            IconButton(
                                onClick = {
                                    if (matchedMessageIndices.isNotEmpty()) {
                                        currentSearchMatchIndex = (currentSearchMatchIndex + 1) % matchedMessageIndices.size
                                        scope.launch { listState.animateScrollToItem(matchedMessageIndices[currentSearchMatchIndex]) }
                                    }
                                },
                                enabled = matchedMessageIndices.isNotEmpty()
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                            }
                        }

                        IconButton(onClick = {
                            if (inChatSearchQuery.isNotBlank()) {
                                inChatSearchQuery = ""
                            } else {
                                isSearchOpen = false
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )
            } else {
                TopAppBar(
                    modifier = Modifier.testTag("conversation_top_bar"),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenFingerprint() }
                        ) {
                            AvatarView(
                                name = conversation.peerName,
                                bgHex = conversation.avatarBgColorHex,
                                textHex = conversation.avatarTextColorHex,
                                size = 40.dp,
                                imageUrl = conversation.avatarUrl,
                                isOnline = conversation.isOnline
                            )

                            Column(
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = conversation.peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.isPeerTyping) "typing..."
                                    else if (conversation.isGroup) "$memberCount members"
                                    else if (conversation.isOnline) "online"
                                    else "@${conversation.peerHandle}",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                        IconButton(
                            onClick = { onStartCall(conversation, true) },
                            modifier = Modifier.testTag("btn_video_call")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video Call",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { onStartCall(conversation, false) },
                            modifier = Modifier.testTag("btn_voice_call")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Voice Call",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

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
                                    text = { Text("Search in Chat") },
                                    onClick = {
                                        showMenu = false
                                        isSearchOpen = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (currentDisappearingTimer > 0)
                                                "Disappearing Messages (${formatTimerShort(currentDisappearingTimer)})"
                                            else "Disappearing Messages"
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showTimerDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = if (currentDisappearingTimer > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Shared Media & Files") },
                                    onClick = {
                                        showMenu = false
                                        showSharedMediaSheet = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PermMedia, contentDescription = null)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Encryption Fingerprint") },
                                    onClick = {
                                        showMenu = false
                                        onOpenFingerprint()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Chat Wallpaper") },
                                    onClick = {
                                        showMenu = false
                                        showWallpaperDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Wallpaper, contentDescription = null)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Export Transcript") },
                                    onClick = {
                                        showMenu = false
                                        exportChatTranscript(context, conversation, messages)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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

                                DropdownMenuItem(
                                    text = { Text("Delete Conversation", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteConversation(conversation.id)
                                        onBack()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ChatWallpaperBackground(
                wallpaper = uiState.chatWallpaper,
                opacity = uiState.wallpaperOpacity,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Pinned Messages Banner
                if (pinnedMessages.isNotEmpty()) {
                    val safePinIdx = currentPinnedIndex.coerceIn(0, pinnedMessages.size - 1)
                    val activePin = pinnedMessages[safePinIdx]

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentPinnedIndex = (currentPinnedIndex + 1) % pinnedMessages.size
                                val targetMsg = pinnedMessages[currentPinnedIndex]
                                val idx = messages.indexOfFirst { it.id == targetMsg.id }
                                if (idx != -1) {
                                    scope.launch { listState.animateScrollToItem(idx) }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Column {
                                    Text(
                                        text = if (pinnedMessages.size > 1) "Pinned Message ${safePinIdx + 1}/${pinnedMessages.size}" else "Pinned Message",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = when (activePin.type) {
                                            MessageType.IMAGE -> "📷 Photo"
                                            MessageType.VOICE -> "🎤 Voice Note"
                                            MessageType.DOCUMENT -> "📁 ${activePin.fileName ?: "Document"}"
                                            else -> activePin.text
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onTogglePinMessage(activePin) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unpin message",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Messages LazyColumn with generous breathing space
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
                ) {
                    // Start of conversation subtle badge
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Messages are end-to-end encrypted",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    val groupedMessages = messages.groupBy { formatMessageDateHeader(it.timestamp) }

                    groupedMessages.forEach { (dateHeader, dateMessages) ->
                        stickyHeader(key = "header_$dateHeader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    shadowElevation = 1.dp
                                ) {
                                    Text(
                                        text = dateHeader,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            items = dateMessages,
                            key = { _, msg -> msg.id }
                        ) { index, message ->
                            val isPrevSame = index > 0 && dateMessages[index - 1].isMe == message.isMe && (message.timestamp - dateMessages[index - 1].timestamp) < 180000
                            val isNextSame = index < dateMessages.size - 1 && dateMessages[index + 1].isMe == message.isMe && (dateMessages[index + 1].timestamp - message.timestamp) < 180000

                            val bubbleShape: Shape = when {
                                message.isMe -> when {
                                    !isPrevSame && isNextSame -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                                    isPrevSame && isNextSame -> RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                                    isPrevSame && !isNextSame -> RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                                    else -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                                }
                                else -> when {
                                    !isPrevSame && isNextSame -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
                                    isPrevSame && isNextSame -> RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
                                    isPrevSame && !isNextSame -> RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                                    else -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .padding(vertical = if (isNextSame) 1.dp else 4.dp)
                            ) {
                                SwipeableMessageBubble(
                                    message = message,
                                    uiState = uiState,
                                    bubbleShape = bubbleShape,
                                    highlightQuery = if (isSearchOpen) inChatSearchQuery else "",
                                    onReply = { onSetReplyTo(message) },
                                    onDoubleTapHeart = {
                                        val current = message.reaction
                                        val newReaction = if (current == "❤️") null else "❤️"
                                        onReactToMessage(message, newReaction)
                                    },
                                    onInspectCipher = { selectedMessageForCipher = message },
                                    onLongClickReaction = { selectedMessageForReaction = message },
                                    onImageClick = { previewImageUrl = it },
                                    onTogglePlayVoiceNote = onTogglePlayVoiceNote,
                                    onCycleAudioSpeed = onCycleAudioSpeed,
                                    onSeekAudio = onSeekAudio
                                )
                            }
                        }
                    }

                    if (uiState.isPeerTyping) {
                        item {
                            TypingIndicatorBubble(conversation.peerName)
                        }
                    }
                }

                // Active Reply Banner if selected
                // Active Edit Banner if editing
                uiState.editingMessage?.let { editMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Editing Message",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = editMsg.text,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    onCancelEditingMessage()
                                    inputText = ""
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel edit",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Active Reply Banner if selected
                uiState.replyingToMessage?.let { replyMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
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

                // Floating Pill Input Composer Bar (Option 1)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (uiState.isRecordingVoice) {
                        // Advanced Voice Recording Pill (Liquid Glassmorphic & Live Waveform)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .shadow(10.dp, RoundedCornerShape(29.dp)),
                            shape = RoundedCornerShape(29.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(ErrorRed)
                                    )
                                    Text(
                                        text = "0:${String.format(Locale.getDefault(), "%02d", uiState.recordingSeconds)}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )

                                    // Live Dynamic Waveform
                                    LiveVoiceWaveform(
                                        amplitude = uiState.recordingAmplitude,
                                        isRecording = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onCancelVoiceRecording()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Cancel recording",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onFinishVoiceRecording(
                                                uiState.recordingSeconds.toLong(),
                                                currentDisappearingTimer > 0,
                                                currentDisappearingTimer
                                            )
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send voice",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Floating Message Pill Bar
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(6.dp, RoundedCornerShape(28.dp)),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Attachment Plus Button
                                IconButton(
                                    onClick = { showAttachmentSheet = true },
                                    modifier = Modifier
                                        .testTag("btn_attach")
                                        .size(40.dp)
                                        .clip(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Attach media",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Quick Photo Picker
                                IconButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .testTag("btn_quick_photo")
                                        .size(40.dp)
                                        .clip(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Select photo",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Text Input Field
                                TextField(
                                    value = inputText,
                                    onValueChange = {
                                        if (it.length <= MAX_CHAR_LIMIT) inputText = it
                                    },
                                    placeholder = {
                                        Text(
                                            text = if (currentDisappearingTimer > 0) "Disappearing (${formatTimerShort(currentDisappearingTimer)})..." else "Message...",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        autoCorrectEnabled = !uiState.isIncognitoKeyboard,
                                        keyboardType = if (uiState.isIncognitoKeyboard) KeyboardType.Password else KeyboardType.Text
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_message_field"),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    maxLines = 4
                                )

                                // Action Button: Save Edit, Voice note or Send
                                if (uiState.editingMessage != null) {
                                    IconButton(
                                        onClick = {
                                            val text = inputText.trim()
                                            if (text.isNotEmpty()) {
                                                onSubmitEditedMessage(uiState.editingMessage.id, text)
                                                inputText = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .testTag("btn_save_edit")
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Save edit",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else if (inputText.isBlank()) {
                                    IconButton(
                                        onClick = {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPermission) {
                                                onStartVoiceRecording()
                                            } else {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier
                                            .testTag("btn_mic_record")
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice note",
                                            tint = MaterialTheme.colorScheme.onSurface
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
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = MaterialTheme.colorScheme.onPrimary,
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
    }

    // Wallpaper Selector Dialog
    if (showWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("Chat Wallpaper") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChatWallpaper.values().forEach { wp ->
                        val isSelected = selectedWallpaper == wp
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedWallpaper = wp
                                    showWallpaperDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (wp) {
                                        ChatWallpaper.MINIMAL -> "Minimalist Monochrome"
                                        ChatWallpaper.SOFT_GRADIENT -> "Soft Tonal Gradient"
                                        ChatWallpaper.SLATE_GRID -> "Slate Neutral"
                                        ChatWallpaper.MIDNIGHT -> "Midnight OLED"
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                TextButton(onClick = { showWallpaperDialog = false }) { Text("Close") }
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
                    text = "Encrypted Media & Actions",
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
                        icon = Icons.Default.Description,
                        label = "Document / File",
                        bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = {
                            showAttachmentSheet = false
                            documentPickerLauncher.launch("*/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.CameraAlt,
                        label = "View-Once 5s",
                        bgColor = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = {
                            showAttachmentSheet = false
                            imagePickerLauncher.launch("image/*")
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
                        text = "New messages will automatically self-destruct after the chosen timer:",
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
                        Text("Photo Attachment", fontWeight = FontWeight.Bold)
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
                    Text("AES-256 Cipher Inspection")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Decrypted Plaintext:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (msg.type == MessageType.IMAGE) "📷 Photo Attachment" else msg.text,
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
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForCipher = null }) { Text("Close") }
            }
        )
    }

    // Message Action & Reaction Dialog
    selectedMessageForReaction?.let { msg ->
        MessageActionsDialog(
            message = msg,
            currentReaction = msg.reaction,
            onSelectEmoji = { emoji ->
                onReactToMessage(msg, if (msg.reaction == emoji) null else emoji)
                selectedMessageForReaction = null
            },
            onForward = {
                selectedMessageForReaction = null
                onForwardMessage(msg)
            },
            onReply = {
                selectedMessageForReaction = null
                onSetReplyTo(msg)
            },
            onEdit = {
                selectedMessageForReaction = null
                onStartEditingMessage(msg)
            },
            onTogglePin = {
                selectedMessageForReaction = null
                onTogglePinMessage(msg)
            },
            onCopyText = {
                clipboardManager.setText(AnnotatedString(msg.text))
                Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                selectedMessageForReaction = null
            },
            onInspectCipher = {
                selectedMessageForReaction = null
                selectedMessageForCipher = msg
            },
            onDelete = {
                selectedMessageForReaction = null
                messageToDelete = msg
            },
            onDismiss = { selectedMessageForReaction = null }
        )
    }

    // Shared Media & Files Sheet
    if (showSharedMediaSheet) {
        SharedMediaSheet(
            messages = messages,
            onDismiss = { showSharedMediaSheet = false },
            onImageClick = { url ->
                showSharedMediaSheet = false
                previewImageUrl = url
            }
        )
    }

    // Delete Message Confirmation Dialog
    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Delete Message", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = if (msg.isMe) "Delete this message? You can delete it for yourself or for everyone in this chat."
                    else "Delete this message for yourself?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (msg.isMe) {
                        Button(
                            onClick = {
                                val target = messageToDelete
                                messageToDelete = null
                                if (target != null) {
                                    onDeleteMessage(target, true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete for Everyone", color = MaterialTheme.colorScheme.onError)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val target = messageToDelete
                            messageToDelete = null
                            if (target != null) {
                                onDeleteMessage(target, false)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for Me")
                    }

                    TextButton(
                        onClick = { messageToDelete = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            },
            dismissButton = null
        )
    }

    // Full-Screen Fluid Media Lightbox Viewer
    if (!previewImageUrl.isNullOrBlank()) {
        MediaLightboxViewer(
            imageUrl = previewImageUrl,
            onDismiss = { previewImageUrl = null }
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
fun LiveVoiceWaveform(
    amplitude: Int,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
    val animOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animOffset"
    )

    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val baseAmp = (amplitude / 32767f).coerceIn(0.2f, 1f)
        for (i in 0 until 16) {
            val waveFactor = kotlin.math.sin(animOffset + (i * 0.45f)).toFloat()
            val heightFraction = if (isRecording) {
                ((baseAmp * 0.65f) + (kotlin.math.abs(waveFactor) * 0.35f)).coerceIn(0.18f, 1f)
            } else 0.2f

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}

// Swipeable Message Bubble with Swipe-to-Reply & Double Tap to React
@Composable
fun SwipeableMessageBubble(
    message: MessageEntity,
    uiState: UiState,
    bubbleShape: Shape,
    highlightQuery: String = "",
    onReply: () -> Unit,
    onDoubleTapHeart: () -> Unit,
    onInspectCipher: () -> Unit,
    onLongClickReaction: () -> Unit,
    onImageClick: (String) -> Unit,
    onTogglePlayVoiceNote: (String?) -> Unit,
    onCycleAudioSpeed: () -> Unit = {},
    onSeekAudio: (Int) -> Unit = {}
) {
    var offsetX by remember { mutableStateOf(0f) }
    var showHeartPop by remember { mutableStateOf(false) }
    var hasHapticFired by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swipeReply"
    )

    val heartScale by animateFloatAsState(
        targetValue = if (showHeartPop) 1.2f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "heartScale"
    )

    LaunchedEffect(showHeartPop) {
        if (showHeartPop) {
            delay(600)
            showHeartPop = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 70f || offsetX < -70f) {
                            onReply()
                        }
                        offsetX = 0f
                        hasHapticFired = false
                    },
                    onDragCancel = {
                        offsetX = 0f
                        hasHapticFired = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount * 0.6f).coerceIn(-120f, 120f)
                        if ((offsetX > 70f || offsetX < -70f) && !hasHapticFired) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hasHapticFired = true
                        }
                    }
                )
            }
    ) {
        // Background reply indicator icon revealed during swipe
        if (animatedOffsetX != 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = if (animatedOffsetX > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                val iconAlpha = (kotlin.math.abs(animatedOffsetX) / 70f).coerceIn(0f, 1f)
                val iconScale = (kotlin.math.abs(animatedOffsetX) / 70f).coerceIn(0.5f, 1.2f)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = iconAlpha),
                    modifier = Modifier
                        .size(36.dp)
                        .scale(iconScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // The Actual Bubble with Offset
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
        ) {
            MessageBubble(
                message = message,
                uiState = uiState,
                bubbleShape = bubbleShape,
                highlightQuery = highlightQuery,
                onReply = onReply,
                onInspectCipher = onInspectCipher,
                onLongClickReaction = onLongClickReaction,
                onDoubleTap = {
                    showHeartPop = true
                    onDoubleTapHeart()
                },
                onImageClick = onImageClick,
                onTogglePlayVoiceNote = onTogglePlayVoiceNote,
                onCycleAudioSpeed = onCycleAudioSpeed,
                onSeekAudio = onSeekAudio
            )

            // Animated Popping Heart on Double Tap
            if (showHeartPop) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .scale(heartScale)
                ) {
                    Text(text = "❤️", fontSize = 36.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    uiState: UiState,
    bubbleShape: Shape = RoundedCornerShape(18.dp),
    highlightQuery: String = "",
    onReply: () -> Unit,
    onInspectCipher: () -> Unit,
    onLongClickReaction: () -> Unit,
    onDoubleTap: () -> Unit,
    onImageClick: (String) -> Unit,
    onTogglePlayVoiceNote: (String?) -> Unit,
    onCycleAudioSpeed: () -> Unit = {},
    onSeekAudio: (Int) -> Unit = {}
) {
    val isMe = message.isMe
    val context = LocalContext.current
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isMe) Alignment.End else Alignment.Start

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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() },
                        onLongPress = { onLongClickReaction() },
                        onTap = { onLongClickReaction() }
                    )
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Sender label if in group chat
                if (!isMe && !message.senderName.isNullOrEmpty() && message.senderName != "You") {
                    Text(
                        text = message.senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Quoted reply banner
                if (!message.replyToText.isNullOrEmpty()) {
                    Surface(
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
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
                        contentDescription = "Image attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(message.mediaUrl) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Document Message View
                if (message.type == MessageType.DOCUMENT) {
                    Surface(
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (!message.mediaUrl.isNullOrEmpty()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            val uri = if (message.mediaUrl.startsWith("content://") || message.mediaUrl.startsWith("http://") || message.mediaUrl.startsWith("https://")) {
                                                Uri.parse(message.mediaUrl)
                                            } else {
                                                Uri.fromFile(java.io.File(message.mediaUrl))
                                            }
                                            setDataAndType(uri, "*/*")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isMe) textColor else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.fileName ?: message.text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = textColor,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (message.fileSizeBytes > 0) formatFileSize(message.fileSizeBytes) else "Document",
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                // Voice Message View
                if (message.type == MessageType.VOICE) {
                    val isCurrentPlaying = uiState.audioPlaybackState.isPlaying && 
                        (uiState.audioPlaybackState.activeMediaUrl == message.mediaUrl || 
                         (message.mediaUrl.isNullOrEmpty() && uiState.audioPlaybackState.activeMediaUrl == message.text))
                    val progressFraction = if (isCurrentPlaying && uiState.audioPlaybackState.durationMs > 0) {
                        (uiState.audioPlaybackState.currentPositionMs.toFloat() / uiState.audioPlaybackState.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = { onTogglePlayVoiceNote(message.mediaUrl ?: message.text) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(textColor.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play voice note",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Interactive Waveform Seek Bar
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                        ) {
                            val barHeights = listOf(10, 18, 14, 24, 18, 12, 22, 16, 8, 14, 20, 12, 16, 22, 10)
                            barHeights.forEachIndexed { index, height ->
                                val barFraction = (index + 1).toFloat() / barHeights.size.toFloat()
                                val isPlayed = progressFraction >= barFraction
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(height.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (isPlayed) textColor else textColor.copy(alpha = 0.4f)
                                        )
                                        .clickable {
                                            if (isCurrentPlaying && uiState.audioPlaybackState.durationMs > 0) {
                                                val targetMs = (barFraction * uiState.audioPlaybackState.durationMs).toInt()
                                                onSeekAudio(targetMs)
                                            }
                                        }
                                )
                            }
                        }

                        // Playback Speed Button (1X / 1.5X / 2X)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = textColor.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clickable { onCycleAudioSpeed() }
                                .padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = when {
                                    uiState.audioPlaybackState.playbackSpeed >= 1.9f -> "2X"
                                    uiState.audioPlaybackState.playbackSpeed >= 1.4f -> "1.5X"
                                    else -> "1X"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = if (isCurrentPlaying && uiState.audioPlaybackState.currentPositionMs > 0) {
                                val sec = (uiState.audioPlaybackState.currentPositionMs / 1000)
                                "0:${String.format(Locale.getDefault(), "%02d", sec)}"
                            } else {
                                "0:${String.format(Locale.getDefault(), "%02d", message.voiceDurationSeconds.coerceAtLeast(1))}"
                            },
                            fontSize = 11.sp,
                            color = textColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (message.type != MessageType.IMAGE && message.type != MessageType.DOCUMENT) {
                    if (highlightQuery.isNotBlank() && message.text.contains(highlightQuery, ignoreCase = true)) {
                        val annotatedText = buildAnnotatedString {
                            val text = message.text
                            val lowerText = text.lowercase()
                            val lowerQuery = highlightQuery.lowercase()
                            var startIndex = 0
                            while (startIndex < text.length) {
                                val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
                                if (matchIndex == -1) {
                                    append(text.substring(startIndex))
                                    break
                                }
                                append(text.substring(startIndex, matchIndex))
                                withStyle(
                                    style = SpanStyle(
                                        background = if (isMe) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primaryContainer,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(text.substring(matchIndex, matchIndex + highlightQuery.length))
                                }
                                startIndex = matchIndex + highlightQuery.length
                            }
                        }
                        Text(
                            text = annotatedText,
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 20.sp
                        )
                    } else {
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Metadata: Time, Pin, Edited, Disappearing & Read status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = textColor.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(11.dp)
                                .padding(end = 3.dp)
                        )
                    }

                    if (message.isEdited) {
                        Text(
                            text = "(edited)",
                            fontSize = 9.sp,
                            color = textColor.copy(alpha = 0.65f),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    if (message.isDisappearing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Disappearing",
                                tint = if (isMe) textColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${remainingSeconds}s",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) textColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Text(
                        text = formatMessageTime(message.timestamp),
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Sending",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = textColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.DELIVERED -> {
                                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Delivered",
                                        tint = textColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = textColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            MessageStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Seen",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Emoji Reaction Badge
        if (!message.reaction.isNullOrEmpty()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .padding(top = 2.dp, start = if (isMe) 0.dp else 8.dp, end = if (isMe) 8.dp else 0.dp)
                    .clickable { onLongClickReaction() }
            ) {
                Text(
                    text = message.reaction,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun MessageActionsDialog(
    message: MessageEntity,
    currentReaction: String?,
    onSelectEmoji: (String) -> Unit,
    onForward: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onCopyText: () -> Unit,
    onInspectCipher: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val emojis = listOf("❤️", "🔥", "👍", "😂", "😮", "🎉", "🙏")
    val haptic = LocalHapticFeedback.current
    val canEdit = message.isMe && message.type == MessageType.TEXT && (System.currentTimeMillis() - message.timestamp) <= 15 * 60 * 1000L

    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Floating Quick Reactions Bar (iOS / Telegram Style)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    emojis.forEach { emoji ->
                        val isSelected = currentReaction == emoji
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectEmoji(emoji)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }

            // Clean Actions Menu Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.widthIn(max = 260.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReply()
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Reply", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (canEdit) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onEdit()
                                },
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Text("Edit Message", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePin()
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(if (message.isPinned) "Unpin Message" else "Pin Message", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onForward()
                            }
                            .testTag("action_forward_message"),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Forward", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (message.type != MessageType.IMAGE) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCopyText()
                                },
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Text("Copy Text", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDelete()
                            }
                            .testTag("action_delete_message"),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Delete", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMediaSheet(
    messages: List<MessageEntity>,
    onDismiss: () -> Unit,
    onImageClick: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val photos = remember(messages) { messages.filter { it.type == MessageType.IMAGE && !it.mediaUrl.isNullOrEmpty() } }
    val docs = remember(messages) { messages.filter { it.type == MessageType.DOCUMENT } }
    val audios = remember(messages) { messages.filter { it.type == MessageType.VOICE } }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Shared Media & Vault",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Photos (${photos.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Files (${docs.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Voice (${audios.size})") }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            when (selectedTab) {
                0 -> {
                    if (photos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Text("No photos shared in this chat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            items(photos) { msg ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(msg.mediaUrl).crossfade(true).build(),
                                    contentDescription = "Shared photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onImageClick(msg.mediaUrl ?: "") }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (docs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Text("No documents or files shared", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            items(docs) { msg ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!msg.mediaUrl.isNullOrEmpty()) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        val uri = if (msg.mediaUrl.startsWith("content://") || msg.mediaUrl.startsWith("http://") || msg.mediaUrl.startsWith("https://")) {
                                                            Uri.parse(msg.mediaUrl)
                                                        } else {
                                                            Uri.fromFile(java.io.File(msg.mediaUrl))
                                                        }
                                                        setDataAndType(uri, "*/*")
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "No viewer app found", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(msg.fileName ?: msg.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            Text(
                                                text = "${formatFileSize(msg.fileSizeBytes)} • ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(msg.timestamp))}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (audios.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Text("No voice messages shared", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            items(audios) { msg ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Voice Note (${msg.voiceDurationSeconds}s)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(msg.timestamp)),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}

private fun exportChatTranscript(
    context: Context,
    conversation: ConversationEntity,
    messages: List<MessageEntity>
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val stringBuilder = StringBuilder()
    stringBuilder.append("Chat Transcript: ${conversation.peerName} (@${conversation.peerHandle})\n")
    stringBuilder.append("Export Date: ${dateFormat.format(Date())}\n")
    stringBuilder.append("Total Messages: ${messages.size}\n\n")

    for (msg in messages) {
        val timeStr = dateFormat.format(Date(msg.timestamp))
        val sender = if (msg.isMe) "You" else conversation.peerName
        val content = when (msg.type) {
            MessageType.IMAGE -> "[Photo Attachment]"
            MessageType.VOICE -> "[Voice Message: ${msg.voiceDurationSeconds}s]"
            else -> msg.text
        }
        val reactionStr = if (!msg.reaction.isNullOrBlank()) " [Reaction: ${msg.reaction}]" else ""
        stringBuilder.append("[$timeStr] $sender: $content$reactionStr\n")
    }

    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Chat Transcript - ${conversation.peerName}")
            putExtra(Intent.EXTRA_TEXT, stringBuilder.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Export Chat Transcript"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun TypingIndicatorBubble(peerName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$peerName is typing",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha1))
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha2))
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha3))
                )
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
