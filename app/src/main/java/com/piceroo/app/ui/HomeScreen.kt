package com.piceroo.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.piceroo.app.AppViewModel
import com.piceroo.app.R
import com.piceroo.app.data.Deck
import com.piceroo.app.data.DeckKind
import com.piceroo.app.data.Stats

private const val COLLAPSED_ROWS = 5

@Composable
fun HomeScreen(
    vm: AppViewModel,
    partialAccess: Boolean,
    onFixAccess: () -> Unit,
    onShareList: () -> Unit,
) {
    val decks = vm.decks
    val bySection = remember(decks) { decks.groupBy { it.kind } }
    // Which sections are expanded past the first few rows.
    val expanded = remember { mutableStateMapOf<DeckKind, Boolean>() }

    Scaffold { padding ->
        if (vm.loading && decks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Roo(44.dp, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Piceroo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item { StatsCard(vm.stats) }

            if (partialAccess) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Piceroo can only see the photos you picked.")
                        TextButton(onClick = onFixAccess) { Text("Allow all photos") }
                    }
                }
            }

            item {
                Button(
                    onClick = vm::quickRound,
                    enabled = decks.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Quick round", style = MaterialTheme.typography.titleMedium) }
            }

            if (decks.isEmpty()) item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BouncingRoo(72.dp)
                    Text("All clear. Nothing left to hop through!", style = MaterialTheme.typography.titleMedium)
                }
            }

            DeckKind.entries.forEach { kind ->
                val section = bySection[kind].orEmpty()
                if (section.isEmpty()) return@forEach
                val showAll = expanded[kind] == true
                item(key = "header-$kind") {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(
                            "${kind.sectionTitle} · ${section.sumOf { it.count }} items · " +
                                section.sumOf { it.totalBytes }.formatBytes(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(kind.blurb, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(if (showAll) section else section.take(COLLAPSED_ROWS), key = { it.key }) { deck ->
                    DeckRow(deck) { vm.open(deck) }
                }
                if (section.size > COLLAPSED_ROWS) item(key = "more-$kind") {
                    TextButton(onClick = { expanded[kind] = !showAll }) {
                        Text(if (showAll) "Show less" else "Show all ${section.size}")
                    }
                }
            }

            item {
                Column(Modifier.padding(top = 24.dp)) {
                    Text(
                        "Trashed items stay in your phone's trash for 30 days. " +
                            "Google Photos keeps its backed-up copies — use the list below " +
                            "to find and delete those too.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onShareList, Modifier.padding(top = 8.dp)) {
                        Text("Share cloud cleanup list")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: Stats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Level ${stats.level}", style = MaterialTheme.typography.labelLarge)
                    Text(stats.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                // Daily streak: an outlined roo that fills in once you've hopped today.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Roo(32.dp, outline = stats.streak == 0, tint = MaterialTheme.colorScheme.primary)
                    Text("${stats.streak}-day streak", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { stats.levelProgress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Stat("Freed", stats.bytesFreed.formatBytes())
                Stat("Trashed", stats.itemsTrashed.toString())
                Stat("Points", stats.points.toString())
                Stat("Best streak", stats.bestStreak.toString())
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DeckRow(deck: Deck, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = deck.allItems.first().uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(deck.title, style = MaterialTheme.typography.titleSmall)
                Text("${deck.count} items · ${deck.totalBytes.formatBytes()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
