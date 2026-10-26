package com.piceroo.app.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.piceroo.app.data.RoundResult

@Composable
fun CelebrateScreen(result: RoundResult, onNext: () -> Unit, onHome: () -> Unit) {
    // Count the points up from zero for a little slot-machine feel.
    var target by remember { mutableIntStateOf(0) }
    val shown by animateIntAsState(target, tween(900), label = "points")
    LaunchedEffect(result) { target = result.pointsEarned.toInt() }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BouncingRoo(110.dp)
            Spacer(Modifier.height(16.dp))
            Text("+$shown pts", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            if (result.trashedCount > 0) {
                Text(
                    "${result.trashedCount} gone · ${result.bytesFreed.formatBytes()} freed",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text("${result.keptCount} kept in the pouch", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            if (result.leveledUp) {
                Text(
                    "LEVEL UP! New rank: ${result.stats.title}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Roo(24.dp, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "${result.stats.streak}-day streak",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Next round") }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Home") }
        }
    }
}
