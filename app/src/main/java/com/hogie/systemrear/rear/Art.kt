package com.hogie.systemrear.rear

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

val Panel = Color(0xE6060B18)
val Dim = Color(0xFF8A9BB8)

/** A "System" window: dark glass panel with a glowing accent border and a header. */
@Composable
fun SystemWindow(title: String, accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .drawBehind {
                // Soft glow around the frame.
                for (i in 1..4) {
                    drawRect(
                        accent.copy(alpha = 0.07f), topLeft = Offset(-i * 2f, -i * 2f),
                        size = size.copy(width = size.width + i * 4f, height = size.height + i * 4f),
                        style = Stroke(width = 2f),
                    )
                }
            }
            .background(Panel)
            .border(1.dp, accent.copy(alpha = 0.9f))
            .padding(8.dp),
    ) {
        Text(
            "◆ $title", color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, letterSpacing = 2.sp,
        )
        Box(Modifier.padding(top = 4.dp)) { content() }
    }
}

/** Hexagonal rank emblem (original design). */
@Composable
fun RankBadge(rank: String, accent: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            val path = Path()
            for (i in 0..5) {
                val a = Math.toRadians((60.0 * i) - 90.0)
                val p = Offset(c.x + (r * 0.92f * cos(a)).toFloat(), c.y + (r * 0.92f * sin(a)).toFloat())
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Black), c, r))
            drawPath(path, accent, style = Stroke(width = 3f))
        }
        Text(rank, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.45f).sp, fontFamily = FontFamily.Monospace)
    }
}

/** Floating companion: the user's imported image, or the original "Shade" figure. */
@Composable
fun AllyView(file: String?, accent: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "float")
    val bob by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob")
    val pulse by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    Box(modifier, contentAlignment = Alignment.Center) {
        // Aura
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = pulse), Color.Transparent), center, size.minDimension / 1.6f),
                radius = size.minDimension / 1.6f,
            )
        }
        val bitmap = remember(file) {
            file?.let { path ->
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
                }.getOrNull()
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = bob * density }) {
            if (bitmap != null) {
                Image(bitmap, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
            } else {
                Shade(accent, pulse)
            }
        }
    }
}

/** Original art: a hooded shadow soldier with glowing eyes. */
@Composable
private fun BoxScope.Shade(accent: Color, glow: Float) {
    Canvas(Modifier.fillMaxSize().padding(6.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // Cloak with a ragged hem
        val cloak = Path().apply {
            moveTo(cx - w * 0.13f, h * 0.30f)
            cubicTo(cx - w * 0.30f, h * 0.42f, cx - w * 0.38f, h * 0.70f, cx - w * 0.40f, h * 0.95f)
            val teeth = 7
            for (i in 0..teeth) {
                val x = cx - w * 0.40f + (w * 0.80f) * i / teeth
                val y = if (i % 2 == 0) h * 0.95f else h * 0.86f
                lineTo(x, y)
            }
            cubicTo(cx + w * 0.38f, h * 0.70f, cx + w * 0.30f, h * 0.42f, cx + w * 0.13f, h * 0.30f)
            close()
        }
        drawPath(cloak, Brush.verticalGradient(listOf(Color(0xFF141A2E), Color(0xFF05070D)), 0f, h))
        drawPath(cloak, accent.copy(alpha = 0.6f), style = Stroke(width = 2f))
        // Hood
        val hood = Path().apply {
            moveTo(cx, h * 0.05f)
            cubicTo(cx + w * 0.22f, h * 0.08f, cx + w * 0.22f, h * 0.30f, cx + w * 0.16f, h * 0.38f)
            lineTo(cx - w * 0.16f, h * 0.38f)
            cubicTo(cx - w * 0.22f, h * 0.30f, cx - w * 0.22f, h * 0.08f, cx, h * 0.05f)
            close()
        }
        drawPath(hood, Color(0xFF0B0F1C))
        drawPath(hood, accent.copy(alpha = 0.7f), style = Stroke(width = 2f))
        // Face shadow + eyes
        drawOval(Color.Black, topLeft = Offset(cx - w * 0.10f, h * 0.17f), size = androidx.compose.ui.geometry.Size(w * 0.20f, h * 0.17f))
        for (dx in listOf(-0.045f, 0.045f)) {
            val e = Offset(cx + w * dx, h * 0.245f)
            drawCircle(accent.copy(alpha = glow * 0.5f), radius = w * 0.04f, center = e)
            drawCircle(accent, radius = w * 0.018f, center = e)
            drawCircle(Color.White, radius = w * 0.007f, center = e)
        }
    }
}

fun allyFile(dir: File, name: String) = File(dir, name).absolutePath
