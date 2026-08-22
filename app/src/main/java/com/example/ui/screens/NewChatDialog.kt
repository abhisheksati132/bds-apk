package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
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
    onDismiss: () -> Unit
) {
    var isAddingCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customHandle by remember { mutableStateOf("") }

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
                        text = "Enter a privacy handle or nickname. No phone number or email is needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. Elena Rostova") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_new_contact_name")
                    )

                    OutlinedTextField(
                        value = customHandle,
                        onValueChange = { customHandle = it },
                        label = { Text("Privacy Handle") },
                        placeholder = { Text("e.g. elena.4019") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_new_contact_handle")
                    )
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
                                text = "Enter Privacy Handle / Code",
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
        },
        confirmButton = {
            if (isAddingCustom) {
                Button(
                    onClick = {
                        if (customName.isNotBlank() && customHandle.isNotBlank()) {
                            onCreateContactAndChat(customName.trim(), customHandle.trim())
                        }
                    },
                    enabled = customName.isNotBlank() && customHandle.isNotBlank(),
                    modifier = Modifier.testTag("btn_create_contact_chat")
                ) {
                    Text("Start Chat")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isAddingCustom) {
                        isAddingCustom = false
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
