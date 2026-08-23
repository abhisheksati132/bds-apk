package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import kotlin.math.abs

@Composable
fun MediaLightboxViewer(
    imageUrl: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (imageUrl.isNullOrBlank()) return

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDraggingToDismiss by remember { mutableStateOf(false) }

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
        }
    }
}
