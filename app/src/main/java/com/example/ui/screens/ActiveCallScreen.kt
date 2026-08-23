package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConversationEntity
import com.example.ui.components.AvatarView
import com.example.ui.theme.ErrorRed
import java.util.*

@Composable
fun ActiveCallScreen(
    peer: ConversationEntity,
    isVideo: Boolean,
    durationSeconds: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onMinimizeCall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val durationText = "${durationSeconds / 60}:${String.format(Locale.getDefault(), "%02d", durationSeconds % 60)}"
    var isFrontCamera by remember { mutableStateOf(true) }
    var isVideoCameraEnabled by remember { mutableStateOf(true) }

    // Pulsing animation for audio calls
    val infiniteTransition = rememberInfiniteTransition(label = "pulseAnim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isVideo) Color(0xFF0D0D11) else MaterialTheme.colorScheme.background)
    ) {
        if (isVideo) {
            // VIDEO CALL VIEWPORT
            Box(modifier = Modifier.fillMaxSize()) {
                // Main Peer Video Canvas Placeholder (or Camera Stream)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF14141E)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AvatarView(
                            name = peer.peerName,
                            bgHex = peer.avatarBgColorHex,
                            textHex = peer.avatarTextColorHex,
                            size = 100.dp,
                            isOnline = true
                        )
                        Text(
                            text = peer.peerName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "🔒 E2EE Video Connected",
                                color = Color(0xFF81C784),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Self Camera PIP Floating Window (Top Right)
                if (isVideoCameraEnabled) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 70.dp, end = 20.dp)
                            .size(width = 100.dp, height = 140.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                        color = Color(0xFF222233),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFrontCamera) Icons.Default.Face else Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = if (isFrontCamera) "Front" else "Rear",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top App Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMinimizeCall,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize Call",
                    tint = Color.White
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (durationSeconds == 0) "Connecting..." else durationText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.size(42.dp))
        }

        // VOICE CALL CENTER CONTENT (When not video)
        if (!isVideo) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Pulsing Outer Ring
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                    )

                    AvatarView(
                        name = peer.peerName,
                        bgHex = peer.avatarBgColorHex,
                        textHex = peer.avatarTextColorHex,
                        size = 110.dp,
                        isOnline = false
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = peer.peerName,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "@${peer.peerHandle}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "End-to-End Encrypted Voice",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // BOTTOM CALL CONTROLS DOCK
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            color = if (isVideo) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Mic
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (isMuted) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isVideo) {
                    // Switch Front/Rear Camera
                    IconButton(
                        onClick = { isFrontCamera = !isFrontCamera },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Toggle Video Camera ON/OFF
                    IconButton(
                        onClick = { isVideoCameraEnabled = !isVideoCameraEnabled },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (!isVideoCameraEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = if (isVideoCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Camera Toggle",
                            tint = if (!isVideoCameraEnabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Speaker Toggle
                IconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isSpeaker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = if (isSpeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Speaker",
                        tint = if (isSpeaker) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // End Call Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(ErrorRed)
                        .testTag("btn_end_call")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

