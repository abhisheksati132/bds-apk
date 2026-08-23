package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthUser
import com.example.ui.components.AvatarView

@Composable
fun AuthDialog(
    currentUser: AuthUser?,
    isLoading: Boolean,
    errorMessage: String?,
    onSignUpWithEmail: (email: String, pass: String, handle: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSignInWithEmail: (email: String, pass: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSignInWithGoogle: (context: Context, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSendPasswordReset: (email: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var customHandle by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localStatusMsg by remember { mutableStateOf<String?>(null) }
    var isResetMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (currentUser != null) Icons.Default.AccountCircle else Icons.Default.LockPerson,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = when {
                        currentUser != null -> "Firebase Account"
                        isResetMode -> "Reset Password"
                        isSignUp -> "Create Account"
                        else -> "Sign In to Cloud"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (currentUser != null) {
                // Signed In State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarView(
                        name = currentUser.displayName ?: currentUser.email ?: "User",
                        bgHex = "#DDE1FF",
                        textHex = "#001453",
                        size = 56.dp
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentUser.displayName ?: "Firebase User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!currentUser.email.isNullOrBlank()) {
                            Text(
                                text = currentUser.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Auth Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Active & Synchronized", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("UID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    currentUser.uid.take(12) + "...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onSignOut()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_auth_sign_out")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            } else {
                // Not Signed In: Login / Register Form
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isSignUp) {
                            "Register with Firebase to secure your privacy identity and sync live real-time relay messages."
                        } else if (isResetMode) {
                            "Enter your email address to receive password reset instructions."
                        } else {
                            "Sign in using Google or Email to unlock cloud message relay across multiple devices."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isResetMode) {
                        // Google Sign-In Button
                        OutlinedButton(
                            onClick = {
                                localStatusMsg = null
                                onSignInWithGoogle(context) { success, msg ->
                                    if (msg != null) localStatusMsg = msg
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_google_signin")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GTranslate,
                                    contentDescription = "Google",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Continue with Google",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = "OR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            localStatusMsg = null
                        },
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auth_email")
                    )

                    if (!isResetMode) {
                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                localStatusMsg = null
                            },
                            label = { Text("Password") },
                            placeholder = { Text("Min 6 characters") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_auth_password")
                        )

                        if (isSignUp) {
                            // Custom Handle Field (only for sign up)
                            OutlinedTextField(
                                value = customHandle,
                                onValueChange = { customHandle = it.lowercase().replace(" ", "") },
                                label = { Text("Custom @Handle (Optional)") },
                                placeholder = { Text("e.g. shadow.99") },
                                prefix = { Text("@") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_auth_handle")
                            )
                        }
                    }

                    // Error / Status Message
                    val displayError = errorMessage ?: localStatusMsg
                    if (!displayError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = displayError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Toggle links
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isResetMode) {
                            TextButton(
                                onClick = {
                                    isSignUp = !isSignUp
                                    localStatusMsg = null
                                }
                            ) {
                                Text(
                                    if (isSignUp) "Already registered? Sign In" else "New here? Create Account",
                                    fontSize = 12.sp
                                )
                            }

                            if (!isSignUp) {
                                TextButton(
                                    onClick = {
                                        isResetMode = true
                                        localStatusMsg = null
                                    }
                                ) {
                                    Text("Forgot?", fontSize = 12.sp)
                                }
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    isResetMode = false
                                    localStatusMsg = null
                                }
                            ) {
                                Text("Back to Sign In", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentUser == null) {
                Button(
                    onClick = {
                        localStatusMsg = null
                        if (isResetMode) {
                            onSendPasswordReset(email) { success, msg ->
                                if (msg != null) localStatusMsg = msg
                            }
                        } else if (isSignUp) {
                            onSignUpWithEmail(email, password, customHandle) { success, msg ->
                                if (msg != null) localStatusMsg = msg
                            }
                        } else {
                            onSignInWithEmail(email, password) { success, msg ->
                                if (msg != null) localStatusMsg = msg
                            }
                        }
                    },
                    enabled = !isLoading && email.isNotBlank() && (isResetMode || password.isNotBlank()),
                    modifier = Modifier.testTag("btn_auth_submit")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            when {
                                isResetMode -> "Send Reset Link"
                                isSignUp -> "Register"
                                else -> "Sign In"
                            }
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_auth_dismiss")
            ) {
                Text(if (currentUser != null) "Close" else "Cancel")
            }
        }
    )
}
