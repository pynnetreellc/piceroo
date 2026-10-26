package com.piceroo.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piceroo.app.R
import com.piceroo.app.data.MediaItem

val TrashRed = Color(0xFFE5484D)
val KeepGreen = Color(0xFF30A46C)
private val Ochre = Color(0xFFC8642A)

/** Outback colors: an ochre accent on warm sand (light) or deep night (dark). */
@Composable
fun PicerooTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFFF2A46E), onPrimary = Color(0xFF3B1A05),
            primaryContainer = Color(0xFF6A3311), onPrimaryContainer = Color(0xFFFFDCC6),
            background = Color(0xFF1A1411), surface = Color(0xFF1A1411),
            surfaceVariant = Color(0xFF2E2420), surfaceContainerHighest = Color(0xFF2E2420),
        )
    } else {
        lightColorScheme(
            primary = Ochre, onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDCC6), onPrimaryContainer = Color(0xFF3B1A05),
            background = Color(0xFFFFF8F2), surface = Color(0xFFFFF8F2),
            surfaceVariant = Color(0xFFF6E6DA), surfaceContainerHighest = Color(0xFFF6E6DA),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** The kangaroo, filled or as an outline, tinted like text unless told otherwise. */
@Composable
fun Roo(
    size: Dp,
    modifier: Modifier = Modifier,
    outline: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painterResource(if (outline) R.drawable.ic_roo_outline else R.drawable.ic_roo),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** The kangaroo hopping up and down forever. */
@Composable
fun BouncingRoo(size: Dp, tint: Color = MaterialTheme.colorScheme.primary) {
    val bounce by rememberInfiniteTransition(label = "bounce").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "hop",
    )
    // Squashes slightly at the bottom of each hop, so landings look springy.
    Roo(
        size,
        tint = tint,
        modifier = Modifier.graphicsLayer {
            translationY = -bounce * size.toPx() * 0.25f
            scaleY = 0.92f + 0.08f * bounce
        },
    )
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BouncingRoo(120.dp)
        Spacer(Modifier.height(16.dp))
        Text("Piceroo", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "To sort your photos into decks, Piceroo needs to see them. " +
                "Choose \"Allow all\" so nothing gets missed. Nothing leaves your phone.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Start hopping") }
    }
}

fun Long.formatBytes(): String = when {
    this >= 1L shl 30 -> "%.1f GB".format(this / (1L shl 30).toDouble())
    this >= 1L shl 20 -> "%.0f MB".format(this / (1L shl 20).toDouble())
    else -> "%.0f KB".format(this / 1024.0)
}

fun Long.formatDuration(): String {
    val s = this / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Opens the photo in your gallery app (e.g. Google Photos) for a closer look. */
fun Context.viewMedia(item: MediaItem) {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(item.uri, item.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (_: ActivityNotFoundException) {
    }
}
