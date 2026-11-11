package app.leonardo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.leonardo.LeonardoViewModel

/** "Lezione 2 – Io e gli altri" → ("Lezione 2", "Io e gli altri"). */
fun titelTeile(titel: String): Pair<String?, String> {
    val teile = titel.split(" – ", limit = 2)
    return if (teile.size == 2) teile[0] to teile[1] else null to titel
}

@Composable
fun StartScreen(vm: LeonardoViewModel) {
    val stand by vm.stand.collectAsState()
    val heute = stand.heute(vm.heute)
    val serie = stand.serie(vm.heute)
    val naechste = vm.naechsteEinheit(stand)
    val zumWiederholen = vm.anzahlZumWiederholen(stand)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Begruessung(heute = heute, ziel = stand.einstellungen.tagesziel, serie = serie) }

            if (naechste != null) {
                item {
                    val kapitel = vm.inhalt.kapitelVon(naechste)
                    val gelesen = stand.einheit(naechste.id).erklaerungGelesen
                    AktionsKarte(
                        emoji = "▶️",
                        titel = "Weiter lernen",
                        text = "${naechste.emoji} ${naechste.titel}" +
                            (kapitel?.let { "\n${titelTeile(it.titel).first ?: it.titel}" } ?: "") +
                            if (gelesen) " · üben" else " · erst erklären, dann üben",
                        hervorgehoben = true,
                        onClick = vm::weiterLernen,
                    )
                }
            }
            if (zumWiederholen > 0) {
                item {
                    AktionsKarte(
                        emoji = "🔁",
                        titel = "Wiederholen",
                        text = "$zumWiederholen Wörter und Aufgaben sind noch wackelig.",
                        hervorgehoben = false,
                        onClick = vm::starteWiederholung,
                    )
                }
            }

            item {
                Text(
                    "Dein Lernpfad",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            }
            items(vm.inhalt.kapitel, key = { it.id }) { k ->
                val (obertitel, titel) = titelTeile(k.titel)
                val sterne = k.einheiten.sumOf { stand.einheit(it.id).sterne }
                KapitelEintrag(
                    emoji = k.emoji,
                    obertitel = obertitel ?: k.untertitel,
                    titel = titel,
                    details = "${k.einheiten.count { stand.einheit(it.id).sterne > 0 }} von ${k.einheiten.size} Einheiten" +
                        if (sterne > 0) " · ⭐ $sterne" else "",
                    anteil = vm.fortschritt(k, stand),
                    onClick = { vm.oeffneKapitel(k) },
                )
            }
            item {
                GrosserKnopf(
                    text = "⚙️  Einstellungen",
                    onClick = { vm.zeige(app.leonardo.Screen.Einstellungen) },
                    dezent = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Begruessung(heute: Int, ziel: Int, serie: Int) {
    val zielErreicht = heute >= ziel
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ciao! 👋", style = MaterialTheme.typography.headlineMedium)
            Text(
                when {
                    zielErreicht -> "Tagesziel geschafft. Stark! 🎯"
                    serie > 1 -> "🔥 $serie Tage in Folge"
                    heute > 0 -> "Guter Start. Weiter so!"
                    else -> "Ein paar Minuten reichen."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Fortschrittsring(anteil = heute / ziel.toFloat(), modifier = Modifier.size(84.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$heute", style = MaterialTheme.typography.titleLarge)
                Text("von $ziel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AktionsKarte(emoji: String, titel: String, text: String, hervorgehoben: Boolean, onClick: () -> Unit) {
    val (farbe, kreis) = if (hervorgehoben) {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = farbe),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EmojiKreis(emoji, kreis, groesse = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titel, style = MaterialTheme.typography.titleLarge)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun KapitelEintrag(
    emoji: String,
    obertitel: String?,
    titel: String,
    details: String,
    anteil: Float,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EmojiKreis(emoji, MaterialTheme.colorScheme.primaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (obertitel != null) {
                    Text(obertitel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(titel, style = MaterialTheme.typography.titleMedium)
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Fortschrittsring(anteil = anteil, modifier = Modifier.size(46.dp), dicke = 5.dp) {
                Text(
                    "${(anteil * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (anteil > 0f) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                )
            }
        }
    }
}
