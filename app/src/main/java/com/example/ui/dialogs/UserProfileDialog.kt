package com.example.ui.dialogs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.AvatarView
import com.example.ui.viewmodel.UiState

data class AvatarStylePreset(
    val name: String,
    val bgHex: String,
    val textHex: String
)

val AVATAR_PRESETS = listOf(
    AvatarStylePreset("Indigo", "#DDE1FF", "#001453"),
    AvatarStylePreset("Emerald", "#C4EED0", "#00210E"),
    AvatarStylePreset("Amethyst", "#EADDFF", "#21005D"),
    AvatarStylePreset("Coral", "#FFDAD6", "#410002"),
    AvatarStylePreset("Amber", "#FFDEA5", "#271900"),
    AvatarStylePreset("Slate", "#E0E2EC", "#191C20")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDialog(
    uiState: UiState,
    onDismiss: () -> Unit,
    onUploadAvatar: (Uri) -> Unit,
    onUpdateProfile: (String, String, String?, String?) -> Unit,
    onRemoveAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var displayNameInput by remember(uiState.myDisplayName, uiState.myHandle) {
        mutableStateOf(
            if (uiState.myDisplayName.isNotBlank()) uiState.myDisplayName
            else if (uiState.authUser?.displayName != null) uiState.authUser.displayName!!
            else "@${uiState.myHandle}"
        )
    }

    var aboutInput by remember(uiState.myAbout) {
        mutableStateOf(uiState.myAbout.ifBlank { "Zero-trust encrypted peer" })
    }

    var selectedPreset by remember {
        mutableStateOf(
            AVATAR_PRESETS.firstOrNull { it.bgHex.equals(uiState.myAvatarBgHex, ignoreCase = true) }
                ?: AVATAR_PRESETS[0]
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onUploadAvatar(uri)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .testTag("dialog_user_profile"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "User Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_profile")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Avatar Section with Photo / Monogram & Edit Overlay
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.padding(8.dp)
                ) {
                    AvatarView(
                        name = displayNameInput.ifBlank { uiState.myHandle },
                        bgHex = selectedPreset.bgHex,
                        textHex = selectedPreset.textHex,
                        size = 100.dp,
                        imageUrl = uiState.myAvatarUrl,
                        isOnline = true
                    )

                    // Edit button badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(36.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable {
                                photoPickerLauncher.launch("image/*")
                            }
                            .testTag("btn_upload_avatar"),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.isUpdatingProfile) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Upload Avatar",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Avatar Actions (Upload / Remove)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_choose_photo")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Photo", fontSize = 12.sp)
                    }

                    if (!uiState.myAvatarUrl.isNullOrBlank()) {
                        FilledTonalButton(
                            onClick = onRemoveAvatar,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_remove_avatar")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }

                // Monogram Color Theme Presets
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Avatar Monogram Style",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AVATAR_PRESETS.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Color(0xFF000000 or preset.bgHex.removePrefix("#").toLong(16))
                                )
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedPreset = preset
                                    onUpdateProfile(displayNameInput, aboutInput, preset.bgHex, preset.textHex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = preset.name,
                                    tint = Color(0xFF000000 or preset.textHex.removePrefix("#").toLong(16)),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Display Name field
                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = { displayNameInput = it },
                    label = { Text("Display Name") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_display_name"),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // About / Status field
                OutlinedTextField(
                    value = aboutInput,
                    onValueChange = { aboutInput = it },
                    label = { Text("About / Status") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_about"),
                    shape = RoundedCornerShape(14.dp)
                )

                // Quick Status Presets Chips
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("🛡️ Encrypted", "🟢 Available", "🔒 Vault Locked").forEach { statusPreset ->
                        SuggestionChip(
                            onClick = { aboutInput = statusPreset },
                            label = { Text(statusPreset, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Handle and Public Fingerprint info card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Permanent Handle",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "@${uiState.myHandle.ifBlank { "anonymous" }}",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString("@${uiState.myHandle}"))
                                    Toast.makeText(context, "Handle copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Handle", modifier = Modifier.size(16.dp))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ECDH Identity Fingerprint",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.myPublicKey,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(uiState.myPublicKey))
                                    Toast.makeText(context, "Key fingerprint copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Fingerprint", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save Changes Button
                Button(
                    onClick = {
                        onUpdateProfile(
                            displayNameInput.trim(),
                            aboutInput.trim(),
                            selectedPreset.bgHex,
                            selectedPreset.textHex
                        )
                        Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_save_profile"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
