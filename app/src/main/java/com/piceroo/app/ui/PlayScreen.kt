package com.piceroo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.piceroo.app.data.Deck
import com.piceroo.app.data.DeckKind
import com.piceroo.app.data.MediaItem
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dateFmt = DateTimeFormatter.ofPattern("MMM d yyyy, h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    deck: Deck,
    onExit: () -> Unit,
    onFinish: (trash: List<MediaItem>, keep: List<MediaItem>) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (deck.kind == DeckKind.BURST) BurstPlay(deck, onFinish) else SwipePlay(deck, onFinish)
        }
    }
}

// ---------- Swipe mode: one card at a time, left = trash, right = keep ----------

@Composable
private fun SwipePlay(deck: Deck, onFinish: (List<MediaItem>, List<MediaItem>) -> Unit) {
    val items = deck.items
    // decisions[i] == true means items[i] goes to the trash. Its size is how far along we are.
    val decisions = remember(deck.key) { mutableStateListOf<Boolean>() }
    val index = decisions.size

    if (index >= items.size) {
        val trash = items.filterIndexed { i, _ -> decisions[i] }
        val keep = items.filterIndexed { i, _ -> !decisions[i] }
        RoundSummary(
            trash = trash,
            onConfirm = { onFinish(trash, keep) },
            onRescue = { item -> decisions[items.indexOf(item)] = false },
            onRedo = { decisions.clear() },
        )
        return
    }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    // Animatable lets us drag the card with a finger, then animate it flying off or springing back.
    val offset = remember(deck.key) { Animatable(Offset.Zero, Offset.VectorConverter) }

    fun decide(trash: Boolean) {
        if (offset.isRunning && offset.targetValue.x != 0f) return // already flying away
        scope.launch {
            offset.animateTo(Offset(if (trash) -2000f else 2000f, offset.value.y), tween(180))
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            decisions.add(trash)
            offset.snapTo(Offset.Zero)
        }
    }

    val queued = items.take(index).filterIndexed { i, _ -> decisions[i] }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LinearProgressIndicator(progress = { index / items.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(
            "${index + 1} of ${items.size} · ${queued.size} queued (${queued.sumOf { it.size }.formatBytes()})",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The next card peeks out behind the current one.
            items.getOrNull(index + 1)?.let { next ->
                MediaCard(next, Modifier.graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; alpha = 0.6f })
            }

            val item = items[index]
            val x = offset.value.x
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = offset.value.x
                        translationY = offset.value.y
                        rotationZ = offset.value.x / 30f
                    }
                    .pointerInput(index) {
                        detectDragGestures(
                            onDragEnd = {
                                val dx = offset.value.x
                                when {
                                    dx < -threshold -> decide(trash = true)
                                    dx > threshold -> decide(trash = false)
                                    else -> scope.launch { offset.animateTo(Offset.Zero, spring()) }
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(Offset.Zero, spring()) } },
                        ) { change, drag ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + drag) }
                        }
                    }
                    .pointerInput(item.id) { detectTapGestures(onTap = { context.viewMedia(item) }) }
            ) {
                MediaCard(item, Modifier)
                // "TRASH" / "KEEP" stamp fades in as you drag.
                val strength = (abs(x) / threshold).coerceIn(0f, 1f)
                if (strength > 0.05f) {
                    Stamp(
                        text = if (x < 0) "TRASH" else "KEEP",
                        color = if (x < 0) TrashRed else KeepGreen,
                        modifier = Modifier
                            .align(if (x < 0) Alignment.TopEnd else Alignment.TopStart)
                            .padding(24.dp)
                            .graphicsLayer { alpha = strength },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LargeFloatingActionButton(onClick = { decide(trash = true) }, containerColor = TrashRed, contentColor = Color.White) {
                Icon(Icons.Filled.Close, contentDescription = "Trash")
            }
            TextButton(
                onClick = { if (decisions.isNotEmpty()) decisions.removeAt(decisions.lastIndex) },
                enabled = decisions.isNotEmpty(),
            ) { Text("Undo") }
            LargeFloatingActionButton(onClick = { decide(trash = false) }, containerColor = KeepGreen, contentColor = Color.White) {
                Icon(Icons.Filled.Check, contentDescription = "Keep")
            }
        }
    }
}

@Composable
private fun MediaCard(item: MediaItem, modifier: Modifier) {
    Card(modifier.fillMaxSize(), shape = RoundedCornerShape(20.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.isVideo) {
                Row(
                    Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(24.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    Text(item.durationMs.formatDuration(), color = Color.White)
                }
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0x88000000)).padding(12.dp)
            ) {
                Text(
                    Instant.ofEpochMilli(item.dateTaken).atZone(ZoneId.systemDefault()).format(dateFmt),
                    color = Color.White, style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${item.size.formatBytes()} · ${item.bucket} · tap to view",
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier
            .border(4.dp, color, RoundedCornerShape(8.dp))
            .background(Color(0x66000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// ---------- Burst mode: tap the keeper(s) in each burst, the rest go ----------

@Composable
private fun BurstPlay(deck: Deck, onFinish: (List<MediaItem>, List<MediaItem>) -> Unit) {
    val bursts = deck.bursts
    var burstIndex by remember(deck.key) { mutableIntStateOf(0) }
    val trashed = remember(deck.key) { mutableStateListOf<MediaItem>() }
    val kept = remember(deck.key) { mutableStateListOf<MediaItem>() }
    val context = LocalContext.current

    if (burstIndex >= bursts.size) {
        RoundSummary(
            trash = trashed.toList(),
            onConfirm = { onFinish(trashed.toList(), kept.toList()) },
            onRescue = { item -> trashed.remove(item); kept.add(item) },
            onRedo = { trashed.clear(); kept.clear(); burstIndex = 0 },
        )
        return
    }

    val burst = bursts[burstIndex]
    // Starts with the biggest file selected: in a burst, more bytes usually means more detail.
    val selected = remember(deck.key, burstIndex) {
        mutableStateListOf(burst.maxBy { it.size }.id)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LinearProgressIndicator(progress = { burstIndex / bursts.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(
            "Burst ${burstIndex + 1} of ${bursts.size} · tap the keepers · long-press to view",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(burst, key = { it.id }) { item ->
                val keep = item.id in selected
                Box(
                    Modifier
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(4.dp, if (keep) KeepGreen else Color.Transparent, RoundedCornerShape(12.dp))
                        .pointerInput(item.id) {
                            detectTapGestures(
                                // Re-check here: this handler is created once, so `keep` above may be stale.
                                onTap = { if (item.id in selected) selected.remove(item.id) else selected.add(item.id) },
                                onLongPress = { context.viewMedia(item) },
                            )
                        }
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (keep) 1f else 0.45f },
                    )
                    Icon(
                        if (keep) Icons.Filled.Check else Icons.Filled.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .background(if (keep) KeepGreen else TrashRed, RoundedCornerShape(50))
                            .padding(4.dp),
                    )
                }
            }
        }

        val toTrash = burst.filter { it.id !in selected }
        Button(
            onClick = {
                trashed += toTrash
                kept += burst.filter { it.id in selected }
                burstIndex++
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
        ) { Text("Keep ${selected.size}, trash ${toTrash.size}") }
        TextButton(
            onClick = { kept += burst; burstIndex++ },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Keep them all") }
    }
}

// ---------- End of round: review what's headed to the trash ----------

@Composable
private fun RoundSummary(
    trash: List<MediaItem>,
    onConfirm: () -> Unit,
    onRescue: (MediaItem) -> Unit,
    onRedo: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Roo(36.dp, tint = MaterialTheme.colorScheme.primary)
            Text(
                "Nice hopping!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            if (trash.isEmpty()) "You kept everything. Still counts!"
            else "${trash.size} to trash · ${trash.sumOf { it.size }.formatBytes()}. Tap any to rescue it.",
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(trash, key = { it.id }) { item ->
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                        .pointerInput(item.id) { detectTapGestures(onTap = { onRescue(item) }) },
                )
            }
        }
        Button(
            onClick = onConfirm,
            colors = if (trash.isEmpty()) ButtonDefaults.buttonColors()
            else ButtonDefaults.buttonColors(containerColor = TrashRed, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
        ) { Text(if (trash.isEmpty()) "Finish round" else "Move ${trash.size} to trash") }
        OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Start this deck over")
        }
    }
}
