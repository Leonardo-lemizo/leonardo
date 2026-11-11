package app.leonardo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.leonardo.LeonardoViewModel

@Composable
fun KapitelScreen(vm: LeonardoViewModel, kapitelId: String) {
    val kapitel = vm.inhalt.kapitel(kapitelId) ?: return
    val stand by vm.stand.collectAsState()
    val (obertitel, titel) = titelTeile(kapitel.titel)

    Scaffold(
        topBar = { LeonardoTopBar(obertitel ?: kapitel.titel, onZurueck = vm::zurueck) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${kapitel.emoji} $titel", style = MaterialTheme.typography.headlineSmall)
                    Text(kapitel.untertitel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(kapitel.einheiten, key = { _, e -> e.id }) { i, einheit ->
                val einheitStand = stand.einheit(einheit.id)
                val anteil = vm.fortschritt(einheit, stand)
                Card(
                    onClick = { vm.oeffneEinheit(einheit) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (einheitStand.sterne > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (einheitStand.sterne > 0) "✓" else "${i + 1}", style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${einheit.emoji}  ${einheit.titel}", style = MaterialTheme.typography.titleMedium)
                            Fortschrittsbalken(anteil)
                            Text(
                                if (einheitStand.sterne > 0) Sterne.text(einheitStand.sterne)
                                else if (anteil > 0f) "${(anteil * 100).toInt()} % geübt"
                                else "Noch nicht begonnen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

object Sterne {
    fun text(anzahl: Int): String = "⭐".repeat(anzahl) + "☆".repeat((3 - anzahl).coerceAtLeast(0))
}
