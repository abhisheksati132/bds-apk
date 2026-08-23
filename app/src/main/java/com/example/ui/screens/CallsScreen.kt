package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallEntity
import com.example.data.model.ConversationEntity
import com.example.ui.components.AvatarView
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen

@Composable
fun CallsScreen(
    calls: List<CallEntity>,
    conversations: List<ConversationEntity>,
    onStartCall: (ConversationEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calls",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (calls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No call logs yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(calls, key = { it.id }) { call ->
                        val matchingConv = conversations.find { it.peerHandle == call.peerHandle }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AvatarView(
                                name = call.peerName,
                                bgHex = call.avatarBgHex,
                                textHex = call.avatarTextHex,
                                size = 50.dp
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = call.peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (call.isMissed) ErrorRed else MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val (icon, tint) = when {
                                        call.isMissed -> Icons.AutoMirrored.Filled.CallMissed to ErrorRed
                                        call.isIncoming -> Icons.AutoMirrored.Filled.CallReceived to SuccessGreen
                                        else -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.colorScheme.primary
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(14.dp)
                                    )

                                    val statusText = when {
                                        call.isMissed -> "Missed"
                                        call.isIncoming -> "Incoming"
                                        else -> "Outgoing"
                                    }
                                    val durationStr = if (call.durationSeconds > 0) " (${call.durationSeconds / 60}m ${call.durationSeconds % 60}s)" else ""
                                    Text(
                                        text = "$statusText • ${formatChatTime(call.timestamp)}$durationStr",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    matchingConv?.let { onStartCall(it, call.isVideo) }
                                },
                                modifier = Modifier.testTag("btn_call_again_${call.id}")
                            ) {
                                Icon(
                                    imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Phone,
                                    contentDescription = "Call back",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
