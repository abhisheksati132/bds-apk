package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import kotlin.math.abs

@Composable
fun MediaLightboxViewer(
    imageUrl: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (imageUrl.isNullOrBlank()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDraggingToDismiss by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Dismiss threshold when dragging down
    val dismissThreshold = 180f
    val bgAlpha by animateFloatAsState(
        targetValue = if (isDraggingToDismiss) (1f - (abs(offsetY) / 500f)).coerceIn(0.2f, 1f) else 1f,
        label = "bgAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = {
                        if (scale <= 1.05f) {
                            onDismiss()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.8f, 5f)
                    scale = newScale

                    if (scale > 1f) {
                        // Panning around zoomed image
                        val maxOffsetX = (size.width * (scale - 1f)) / 2f
                        val maxOffsetY = (size.height * (scale - 1f)) / 2f
                        offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                        isDraggingToDismiss = false
                    } else {
                        // Dragging down to dismiss
                        offsetY += pan.y
                        offsetX += pan.x * 0.4f
                        isDraggingToDismiss = true

                        if (abs(offsetY) > dismissThreshold) {
                            onDismiss()
                        }
                    }
                }
            }
    ) {
        // Image content with smooth graphics transformation
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Full-screen media view",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )

        // Floating Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (scale > 1.05f) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "${String.format(java.util.Locale.getDefault(), "%.1f", scale)}x",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Share Button
                IconButton(
                    onClick = {
                        shareMediaImage(context, imageUrl)
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share image",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Download / Save to Gallery Button
                IconButton(
                    onClick = {
                        if (!isSaving) {
                            isSaving = true
                            coroutineScope.launch {
                                val success = saveImageToGallery(context, imageUrl)
                                isSaving = false
                                Toast.makeText(
                                    context,
                                    if (success) "Saved to Gallery" else "Failed to save image",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save to Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private suspend fun saveImageToGallery(context: Context, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val bitmap = loadBitmapFromUriOrUrl(context, imageUrl) ?: return@withContext false
        val filename = "IMG_${System.currentTimeMillis()}.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PrivateMessenger")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                }
                return@withContext true
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appDir = File(imagesDir, "PrivateMessenger").apply { mkdirs() }
            val imageFile = File(appDir, filename)
            FileOutputStream(imageFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
            return@withContext true
        }
        false
    } catch (e: Exception) {
        false
    }
}

private fun shareMediaImage(context: Context, imageUrl: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            if (imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUrl))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, imageUrl)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share Image"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share image", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun loadBitmapFromUriOrUrl(context: Context, urlOrUri: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (urlOrUri.startsWith("data:image")) {
            val base64Data = urlOrUri.substringAfter("base64,")
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else if (urlOrUri.startsWith("content://") || urlOrUri.startsWith("file://")) {
            val uri = Uri.parse(urlOrUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                return@withContext BitmapFactory.decodeStream(stream)
            }
        } else if (urlOrUri.startsWith("http://") || urlOrUri.startsWith("https://")) {
            val url = URL(urlOrUri)
            url.openStream().use { stream ->
                return@withContext BitmapFactory.decodeStream(stream)
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}
