package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactEntity
import com.example.data.remote.CloudUser
import com.example.ui.components.AvatarView

@Composable
fun NewChatDialog(
    contacts: List<ContactEntity>,
    cloudUsers: List<CloudUser> = emptyList(),
    onSelectContact: (ContactEntity) -> Unit,
    onSelectCloudUser: (CloudUser) -> Unit = {},
    onOpenCreateGroup: () -> Unit = {},
    onCreateContactAndChat: (name: String, handle: String) -> Unit,
    onSearchCloudPeer: (handle: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    isSearchingCloud: Boolean = false,
    onDismiss: () -> Unit
) {
    var isAddingCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customHandle by remember { mutableStateOf("") }
    var lookupStatusMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isAddingCustom) "New Privacy Contact" else "Start Encrypted Chat",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (isAddingCustom) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter any peer's @handle to connect live over Firebase Cloud Relay:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customHandle,
                        onValueChange = {
                            customHandle = it.lowercase().replace(" ", "")
                            lookupStatusMessage = null
                        },
                        label = { Text("Peer @Handle") },
                        placeholder = { Text("e.g. alex.8821") },
                        prefix = { Text("@") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_contact_handle")
                    )

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Display Nickname (Optional)") },
                        placeholder = { Text("e.g. Alex") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_contact_name")
                    )

                    if (lookupStatusMessage != null) {
                        Text(
                            text = lookupStatusMessage!!,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action button to Create Group Chat
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCreateGroup() }
                            .testTag("btn_start_new_group")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "New Group Chat (Multi-user)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Action button to enter handle
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAddingCustom = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Search Peer @Handle / Connect Cloud",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Cloud Registered Users section
                    if (cloudUsers.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Registered Cloud Users",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(cloudUsers, key = { "cloud_${it.handle}" }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectCloudUser(user) }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AvatarView(
                                        name = user.displayName,
                                        bgHex = user.avatarBgHex,
                                        textHex = user.avatarTextHex,
                                        size = 36.dp,
                                        isOnline = user.isOnline
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "@${user.handle}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("Cloud", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }

                    Text(
                        text = "Saved Contacts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    if (contacts.isEmpty() && cloudUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No saved contacts yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(contacts, key = { it.handle }) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectContact(contact) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AvatarView(
                                        name = contact.name,
                                        bgHex = contact.avatarBgHex,
                                        textHex = contact.avatarTextHex,
                                        size = 38.dp
                                    )
                                    Column {
                                        Text(
                                            text = contact.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "@${contact.handle}",
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
        },
        confirmButton = {
            if (isAddingCustom) {
                Button(
                    onClick = {
                        val handle = customHandle.trim().replace("@", "")
                        val name = customName.trim().ifEmpty { "@$handle" }
                        if (handle.isNotBlank()) {
                            onSearchCloudPeer(handle) { success, msg ->
                                lookupStatusMessage = msg
                            }
                        }
                    },
                    enabled = customHandle.isNotBlank() && !isSearchingCloud,
                    modifier = Modifier.testTag("btn_create_contact_chat")
                ) {
                    if (isSearchingCloud) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Connect & Chat")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isAddingCustom) {
                        isAddingCustom = false
                        lookupStatusMessage = null
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (isAddingCustom) "Back" else "Cancel")
            }
        }
    )
}
