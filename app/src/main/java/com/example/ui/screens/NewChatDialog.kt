package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import com.example.ui.components.AvatarView

@Composable
fun NewChatDialog(
    contacts: List<ContactEntity>,
    onSelectContact: (ContactEntity) -> Unit,
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
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            if (isAddingCustom) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a peer's @handle to connect live via Firebase Cloud Relay or local encrypted channel:",
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
                        .heightIn(max = 350.dp)
                ) {
                    // Action button to enter handle
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAddingCustom = true }
                            .padding(bottom = 12.dp)
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
                                text = "Enter Privacy Handle / Connect Cloud",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Text(
                        text = "Saved Contacts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    if (contacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
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
                            modifier = Modifier.fillMaxWidth(),
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
                                        size = 40.dp
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

