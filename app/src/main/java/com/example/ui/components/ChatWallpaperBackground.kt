package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChatWallpaperBackground(
    wallpaper: String = "DOODLE_GEOMETRIC",
    opacity: Float = 0.35f,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val doodleColor = if (isDark) {
        Color.White.copy(alpha = (opacity * 0.18f).coerceIn(0.02f, 0.4f))
    } else {
        Color(0xFF2B3A4A).copy(alpha = (opacity * 0.12f).coerceIn(0.02f, 0.3f))
    }

    val baseBgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (wallpaper.uppercase()) {
                "DOODLE_GEOMETRIC", "DOODLE" -> {
                    drawDoodlePatterns(doodleColor)
                }
                "MIDNIGHT_NEBULA", "NEBULA" -> {
                    drawMidnightNebula(opacity)
                }
                "EMERALD_BOTANICAL", "EMERALD" -> {
                    drawEmeraldBotanical(opacity)
                }
                "CYBER_GRID", "CYBERPUNK" -> {
                    drawCyberGrid(opacity)
                }
                else -> {
                    // Minimal Solid with soft vertical gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                baseBgColor,
                                baseBgColor.copy(alpha = 0.95f)
                            )
                        )
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawDoodlePatterns(color: Color) {
    val stepX = 120.dp.toPx()
    val stepY = 120.dp.toPx()
    val strokeWidth = 1.5.dp.toPx()

    var row = 0
    var y = 30.dp.toPx()
    while (y < size.height + stepY) {
        var col = 0
        var x = if (row % 2 == 0) 30.dp.toPx() else (30.dp.toPx() + stepX / 2f)
        while (x < size.width + stepX) {
            val patternType = (row * 7 + col * 11) % 6
            when (patternType) {
                0 -> drawPaperPlane(Offset(x, y), color, strokeWidth)
                1 -> drawPadlock(Offset(x, y), color, strokeWidth)
                2 -> drawChatBubble(Offset(x, y), color, strokeWidth)
                3 -> drawStarDoodle(Offset(x, y), color, strokeWidth)
                4 -> drawSoundWave(Offset(x, y), color, strokeWidth)
                5 -> drawShieldDoodle(Offset(x, y), color, strokeWidth)
            }
            col++
            x += stepX
        }
        row++
        y += stepY
    }
}

private fun DrawScope.drawPaperPlane(center: Offset, color: Color, strokeWidth: Float) {
    rotate(degrees = -25f, pivot = center) {
        val path = Path().apply {
            moveTo(center.x + 18f, center.y)
            lineTo(center.x - 14f, center.y - 12f)
            lineTo(center.x - 8f, center.y)
            lineTo(center.x - 14f, center.y + 12f)
            close()
            moveTo(center.x - 8f, center.y)
            lineTo(center.x + 18f, center.y)
        }
        drawPath(path, color, style = Stroke(width = strokeWidth))
    }
}

private fun DrawScope.drawPadlock(center: Offset, color: Color, strokeWidth: Float) {
    // Shackle
    val shackleRadius = 7f
    val path = Path().apply {
        moveTo(center.x - shackleRadius, center.y - 2f)
        cubicTo(
            center.x - shackleRadius, center.y - 16f,
            center.x + shackleRadius, center.y - 16f,
            center.x + shackleRadius, center.y - 2f
        )
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
    // Body
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - 11f, center.y - 2f),
        size = Size(22f, 18f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        style = Stroke(width = strokeWidth)
    )
    // Keyhole
    drawCircle(color, radius = 2f, center = Offset(center.x, center.y + 5f))
}

private fun DrawScope.drawChatBubble(center: Offset, color: Color, strokeWidth: Float) {
    val path = Path().apply {
        moveTo(center.x - 14f, center.y - 10f)
        lineTo(center.x + 14f, center.y - 10f)
        quadraticTo(center.x + 18f, center.y - 10f, center.x + 18f, center.y - 6f)
        lineTo(center.x + 18f, center.y + 6f)
        quadraticTo(center.x + 18f, center.y + 10f, center.x + 14f, center.y + 10f)
        lineTo(center.x - 4f, center.y + 10f)
        lineTo(center.x - 12f, center.y + 16f)
        lineTo(center.x - 10f, center.y + 10f)
        lineTo(center.x - 14f, center.y + 10f)
        quadraticTo(center.x - 18f, center.y + 10f, center.x - 18f, center.y + 6f)
        lineTo(center.x - 18f, center.y - 6f)
        quadraticTo(center.x - 18f, center.y - 10f, center.x - 14f, center.y - 10f)
        close()
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawStarDoodle(center: Offset, color: Color, strokeWidth: Float) {
    val rOut = 12f
    val rIn = 5f
    val path = Path()
    for (i in 0 until 8) {
        val r = if (i % 2 == 0) rOut else rIn
        val angle = i * Math.PI / 4.0 - Math.PI / 2.0
        val px = center.x + (r * cos(angle)).toFloat()
        val py = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawSoundWave(center: Offset, color: Color, strokeWidth: Float) {
    val heights = floatArrayOf(8f, 16f, 22f, 14f, 8f)
    for (i in heights.indices) {
        val x = center.x - 16f + (i * 8f)
        val h = heights[i]
        drawLine(
            color = color,
            start = Offset(x, center.y - h / 2f),
            end = Offset(x, center.y + h / 2f),
            strokeWidth = strokeWidth
        )
    }
}

private fun DrawScope.drawShieldDoodle(center: Offset, color: Color, strokeWidth: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - 14f)
        lineTo(center.x + 12f, center.y - 8f)
        lineTo(center.x + 12f, center.y + 4f)
        quadraticTo(center.x, center.y + 16f, center.x, center.y + 16f)
        quadraticTo(center.x, center.y + 16f, center.x - 12f, center.y + 4f)
        lineTo(center.x - 12f, center.y - 8f)
        close()
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawMidnightNebula(opacity: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF1B1B3A).copy(alpha = opacity * 0.4f),
                Color(0xFF090A10).copy(alpha = opacity * 0.8f),
                Color(0xFF000000)
            ),
            center = Offset(size.width * 0.7f, size.height * 0.3f),
            radius = size.width * 0.9f
        )
    )

    // Star dust spots
    val starColor = Color.White.copy(alpha = (opacity * 0.5f).coerceIn(0.1f, 0.8f))
    val positions = listOf(
        Offset(0.15f, 0.12f), Offset(0.42f, 0.08f), Offset(0.78f, 0.18f),
        Offset(0.25f, 0.35f), Offset(0.65f, 0.45f), Offset(0.88f, 0.55f),
        Offset(0.12f, 0.65f), Offset(0.50f, 0.72f), Offset(0.82f, 0.82f),
        Offset(0.32f, 0.88f), Offset(0.68f, 0.92f)
    )

    for (pos in positions) {
        val cx = pos.x * size.width
        val cy = pos.y * size.height
        drawCircle(starColor, radius = 1.5.dp.toPx(), center = Offset(cx, cy))
        drawLine(
            color = starColor.copy(alpha = starColor.alpha * 0.6f),
            start = Offset(cx - 4.dp.toPx(), cy),
            end = Offset(cx + 4.dp.toPx(), cy),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = starColor.copy(alpha = starColor.alpha * 0.6f),
            start = Offset(cx, cy - 4.dp.toPx()),
            end = Offset(cx, cy + 4.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawEmeraldBotanical(opacity: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0E241B).copy(alpha = opacity * 0.5f),
                Color(0xFF05120D)
            )
        )
    )
    val strokeColor = Color(0xFF34D399).copy(alpha = (opacity * 0.15f).coerceIn(0.03f, 0.3f))
    drawDoodlePatterns(strokeColor)
}

private fun DrawScope.drawCyberGrid(opacity: Float) {
    val gridColor = Color(0xFFBD34FE).copy(alpha = (opacity * 0.12f).coerceIn(0.02f, 0.25f))
    val spacing = 40.dp.toPx()

    var x = 0f
    while (x < size.width) {
        drawLine(gridColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
        x += spacing
    }

    var y = 0f
    while (y < size.height) {
        drawLine(gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
        y += spacing
    }
}
