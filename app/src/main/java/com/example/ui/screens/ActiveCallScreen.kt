package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    modifier: Modifier = Modifier
) {
    val durationText = "${durationSeconds / 60}:${String.format(Locale.getDefault(), "%02d", durationSeconds % 60)}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14))
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Encryption Verification Emojis
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFAEC6FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "E2E SAS Verification: 🦁 ⚡ 🔑 🛡️",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = if (durationSeconds == 0) "Connecting encrypted channel..." else durationText,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }

            // Center Section: Big Avatar & Name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AvatarView(
                    name = peer.peerName,
                    bgHex = peer.avatarBgColorHex,
                    textHex = peer.avatarTextColorHex,
                    size = 110.dp,
                    isOnline = false
                )

                Text(
                    text = peer.peerName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Encrypted ${if (isVideo) "Video" else "Audio"} Stream",
                    fontSize = 13.sp,
                    color = Color(0xFFAEC6FF)
                )
            }

            // Bottom Section: Call Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color.White else Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = if (isMuted) Color.Black else Color.White
                        )
                    }

                    // End Call Button
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ErrorRed)
                            .testTag("btn_end_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Speaker
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isSpeaker) Color.White else Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = if (isSpeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = "Speaker",
                            tint = if (isSpeaker) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}
