package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.remote.FirebaseDiagnostics

@Composable
fun FirebaseDiagnosticsDialog(
    diagnostics: FirebaseDiagnostics?,
    isRunning: Boolean,
    onRunDiagnostics: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedText by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Live Tests, 1: Setup Guide & Rules

    val firestoreRules = """rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}"""

    val storageRules = """rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if true;
    }
  }
}"""

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Firebase Cloud Health",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Diagnostics & Cloud Setup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Live Tests", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Setup Rules", fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Copied notification snack
                AnimatedVisibility(visible = copiedText != null) {
                    Surface(
                        color = Color(0xFF2E7D32),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = "✓ $copiedText copied to clipboard!",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Content Section
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedTab == 0) {
                        // LIVE TESTS TAB
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 1. Firebase Core Initialization
                            DiagnosticCard(
                                title = "Firebase App Initialization",
                                isSuccess = diagnostics?.isAppInitialized == true,
                                subtitle = if (diagnostics?.isAppInitialized == true) "Initialized via google-services.json" else "Not Initialized (Offline Standalone)",
                                icon = Icons.Default.CheckCircle
                            )

                            // 2. Firestore Connection
                            DiagnosticCard(
                                title = "Firestore Realtime Database",
                                isSuccess = diagnostics?.isFirestoreConnected == true,
                                subtitle = if (diagnostics?.isFirestoreConnected == true) "Connected • Latency ${diagnostics.firestoreLatencyMs}ms" else (diagnostics?.firestoreError ?: "Not connected"),
                                icon = Icons.Default.Storage,
                                details = if (diagnostics?.isFirestoreConnected == false) "Ensure Firestore is created in Firebase Console and rules allow read/write." else null
                            )

                            // 3. Firebase Storage
                            DiagnosticCard(
                                title = "Firebase Storage Bucket",
                                isSuccess = diagnostics?.isStorageAvailable == true,
                                subtitle = if (diagnostics?.isStorageAvailable == true) "Upload & Download active" else (diagnostics?.storageError ?: "Automatic In-Band Base64 Fallback Active"),
                                icon = Icons.Default.FolderShared,
                                details = if (diagnostics?.isStorageAvailable == false) "In-band Base64 fallback is active so photos & voice notes work regardless! Enable Storage in Firebase Console for cloud hosting." else null
                            )

                            // 4. Firebase Authentication
                            DiagnosticCard(
                                title = "Firebase Authentication",
                                isSuccess = diagnostics?.authStatus != "Not Signed In",
                                subtitle = "Status: ${diagnostics?.authStatus ?: "Unknown"}",
                                icon = Icons.Default.Person,
                                details = if (diagnostics?.currentUid != null) "UID: ${diagnostics.currentUid.take(12)}..." else "Enable Anonymous Auth in Firebase Console for guest sync."
                            )

                            // 5. Push Notifications (FCM)
                            DiagnosticCard(
                                title = "FCM Push Notifications",
                                isSuccess = !diagnostics?.fcmToken.isNullOrBlank(),
                                subtitle = if (!diagnostics?.fcmToken.isNullOrBlank()) "Token Registered" else "Token pending or not available",
                                icon = Icons.Default.Notifications
                            )
                        }
                    } else {
                        // SETUP RULES & GUIDE TAB
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "3-Step Firebase Console Setup",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Step 1: Firestore
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Step 1: Firestore Database Rules", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Go to Firebase Console > Firestore Database > Rules, paste:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = firestoreRules,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(firestoreRules))
                                            copiedText = "Firestore Rules"
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Firestore Rules", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Step 2: Storage
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Step 2: Firebase Storage Rules", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Go to Firebase Console > Storage > Rules, paste:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = storageRules,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(storageRules))
                                            copiedText = "Storage Rules"
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Storage Rules", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Step 3: Auth
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Step 3: Enable Anonymous Auth", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Go to Firebase Console > Authentication > Sign-in method, click Anonymous, and toggle Enable.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRunDiagnostics,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-Test")
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    isSuccess: Boolean,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    details: String? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isSuccess) Color(0xFF4CAF50).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSuccess) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                }
            }

            if (details != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = details,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
