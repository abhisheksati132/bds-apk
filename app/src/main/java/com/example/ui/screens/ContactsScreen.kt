package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
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
import com.example.data.model.ContactEntity
import com.example.data.remote.CloudUser
import com.example.ui.components.AvatarView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contacts: List<ContactEntity>,
    cloudUsers: List<CloudUser>,
    myHandle: String,
    presenceMap: Map<String, Boolean> = emptyMap(),
    blockedHandles: Set<String> = emptySet(),
    onStartChatWithContact: (ContactEntity) -> Unit,
    onStartChatWithCloudUser: (CloudUser) -> Unit,
    onStartCallWithUser: (name: String, handle: String, isVideo: Boolean) -> Unit,
    onAddCustomContact: (name: String, handle: String) -> Unit,
    onSearchCloudPeer: (handle: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onBlockUser: (String) -> Unit = {},
    onUnblockUser: (String) -> Unit = {},
    isSearchingCloud: Boolean = false,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newHandle by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var lookupMessage by remember { mutableStateOf<String?>(null) }

    // Combine local contacts and registered cloud users (excluding self)
    val filteredCloudUsers = remember(cloudUsers, searchQuery, myHandle) {
        val cleanMe = myHandle.lowercase().replace("@", "").trim()
        cloudUsers
            .filter { it.handle.lowercase() != cleanMe }
            .filter {
                if (searchQuery.isBlank()) true
                else it.displayName.contains(searchQuery, ignoreCase = true) ||
                        it.handle.contains(searchQuery, ignoreCase = true)
            }
    }

    val filteredContacts = remember(contacts, searchQuery, myHandle) {
        val cleanMe = myHandle.lowercase().replace("@", "").trim()
        contacts
            .filter { it.handle.lowercase() != cleanMe }
            .filter {
                if (searchQuery.isBlank()) true
                else it.name.contains(searchQuery, ignoreCase = true) ||
                        it.handle.contains(searchQuery, ignoreCase = true)
            }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_contacts"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Contacts",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${filteredCloudUsers.size + filteredContacts.size} contacts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("btn_add_contact_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add Contact",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(bottom = 80.dp)
                    .testTag("fab_add_contact")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name or @handle...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("input_search_contacts")
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cloud Registered Users
                if (filteredCloudUsers.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Registered Cloud Users (${filteredCloudUsers.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    items(filteredCloudUsers, key = { "cloud_${it.handle}" }) { user ->
                        val isOnline = presenceMap[user.handle] ?: user.isOnline
                        val isBlocked = blockedHandles.contains(user.handle.lowercase())
                        CloudContactCard(
                            user = user.copy(isOnline = isOnline),
                            isBlocked = isBlocked,
                            onChatClick = { onStartChatWithCloudUser(user) },
                            onVoiceCallClick = { onStartCallWithUser(user.displayName, user.handle, false) },
                            onVideoCallClick = { onStartCallWithUser(user.displayName, user.handle, true) },
                            onBlockClick = { onBlockUser(user.handle) },
                            onUnblockClick = { onUnblockUser(user.handle) }
                        )
                    }
                }

                // Local Saved Contacts
                if (filteredContacts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Saved Contacts (${filteredContacts.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(filteredContacts, key = { "local_${it.handle}" }) { contact ->
                        val isOnline = presenceMap[contact.handle] ?: true
                        val isBlocked = contact.isBlocked || blockedHandles.contains(contact.handle.lowercase())
                        LocalContactCard(
                            contact = contact,
                            isOnline = isOnline,
                            isBlocked = isBlocked,
                            onChatClick = { onStartChatWithContact(contact) },
                            onVoiceCallClick = { onStartCallWithUser(contact.name, contact.handle, false) },
                            onVideoCallClick = { onStartCallWithUser(contact.name, contact.handle, true) },
                            onBlockClick = { onBlockUser(contact.handle) },
                            onUnblockClick = { onUnblockUser(contact.handle) }
                        )
                    }
                }

                if (filteredCloudUsers.isEmpty() && filteredContacts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No users matching \"$searchQuery\"" else "No contacts found",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Tap the + button to search and connect with any peer @handle.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Search Peer Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                lookupMessage = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Add Contact")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Search and connect to any peer on the Firestore cloud directory:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newHandle,
                        onValueChange = {
                            newHandle = it.lowercase().replace(" ", "")
                            lookupMessage = null
                        },
                        label = { Text("Peer @Handle") },
                        placeholder = { Text("e.g. alice.9942") },
                        prefix = { Text("@") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_contact_screen_handle")
                    )

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Display Nickname (Optional)") },
                        placeholder = { Text("e.g. Alice") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_contact_screen_name")
                    )

                    if (lookupMessage != null) {
                        Text(
                            text = lookupMessage!!,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanHandle = newHandle.trim().replace("@", "")
                        val cleanName = newName.trim().ifEmpty { "@$cleanHandle" }
                        if (cleanHandle.isNotBlank()) {
                            onSearchCloudPeer(cleanHandle) { success, msg ->
                                lookupMessage = msg
                                if (success) {
                                    onAddCustomContact(cleanName, cleanHandle)
                                    showAddDialog = false
                                }
                            }
                        }
                    },
                    enabled = newHandle.isNotBlank() && !isSearchingCloud,
                    modifier = Modifier.testTag("btn_save_new_contact")
                ) {
                    if (isSearchingCloud) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Connect & Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        lookupMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CloudContactCard(
    user: CloudUser,
    isBlocked: Boolean = false,
    onChatClick: () -> Unit,
    onVoiceCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onBlockClick: () -> Unit = {},
    onUnblockClick: () -> Unit = {}
) {
    var showBlockMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (!isBlocked) onChatClick() }
            .testTag("card_cloud_user_${user.handle}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarView(
                name = user.displayName,
                bgHex = if (isBlocked) "#B00020" else user.avatarBgHex,
                textHex = if (isBlocked) "#FFFFFF" else user.avatarTextHex,
                size = 46.dp,
                imageUrl = user.avatarUrl,
                isOnline = if (isBlocked) false else user.isOnline
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = user.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (isBlocked) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text(
                                text = "Blocked",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 3.dp)
                            )
                        }
                    } else {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                text = "Cloud",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "@${user.handle}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isBlocked) "🚫 Blocked from messaging" else if (user.isOnline) "🟢 Online Now" else "⚪ Offline",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isBlocked) MaterialTheme.colorScheme.error else if (user.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isBlocked) {
                    IconButton(onClick = onVoiceCallClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onVideoCallClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showBlockMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More user options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showBlockMenu,
                        onDismissRequest = { showBlockMenu = false }
                    ) {
                        if (isBlocked) {
                            DropdownMenuItem(
                                text = { Text("Unblock User") },
                                onClick = {
                                    showBlockMenu = false
                                    onUnblockClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Block User", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showBlockMenu = false
                                    onBlockClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalContactCard(
    contact: ContactEntity,
    isOnline: Boolean = true,
    isBlocked: Boolean = false,
    onChatClick: () -> Unit,
    onVoiceCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onBlockClick: () -> Unit = {},
    onUnblockClick: () -> Unit = {}
) {
    var showBlockMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (!isBlocked) onChatClick() }
            .testTag("card_contact_${contact.handle}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarView(
                name = contact.name,
                bgHex = if (isBlocked) "#B00020" else contact.avatarBgHex,
                textHex = if (isBlocked) "#FFFFFF" else contact.avatarTextHex,
                size = 46.dp,
                imageUrl = contact.avatarUrl,
                isOnline = if (isBlocked) false else isOnline
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (isBlocked) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text(
                                text = "Blocked",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 3.dp)
                            )
                        }
                    } else if (contact.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = "@${contact.handle}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isBlocked) "🚫 Blocked from messaging" else if (isOnline) "🟢 Online" else "⚪ Offline",
                    fontSize = 11.sp,
                    color = if (isBlocked) MaterialTheme.colorScheme.error else if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isBlocked) {
                    IconButton(onClick = onVoiceCallClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onVideoCallClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showBlockMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More contact options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showBlockMenu,
                        onDismissRequest = { showBlockMenu = false }
                    ) {
                        if (isBlocked) {
                            DropdownMenuItem(
                                text = { Text("Unblock Contact") },
                                onClick = {
                                    showBlockMenu = false
                                    onUnblockClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Block Contact", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showBlockMenu = false
                                    onBlockClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
