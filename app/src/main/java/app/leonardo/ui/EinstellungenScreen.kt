package app.leonardo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.leonardo.LeonardoViewModel

@Composable
fun EinstellungenScreen(vm: LeonardoViewModel) {
    val stand by vm.stand.collectAsState()
    val e = stand.einstellungen
    var zeigeZuruecksetzen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { LeonardoTopBar("Einstellungen", onZurueck = vm::zurueck) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Gruppe("Runden") {
                Schalter(
                    titel = "Kurze Runden",
                    beschreibung = "10 statt 18 Aufgaben pro Übung.",
                    an = e.kurzeRunden,
                    onChange = { an -> vm.setzeEinstellungen { it.copy(kurzeRunden = an) } },
                )
                Text("Tagesziel", style = MaterialTheme.typography.titleMedium)
                Text(
                    "So viele Antworten möchtest du pro Tag schaffen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 40).forEach { ziel ->
                        FilterChip(
                            selected = e.tagesziel == ziel,
                            onClick = { vm.setzeEinstellungen { it.copy(tagesziel = ziel) } },
                            label = { Text("$ziel") },
                        )
                    }
                }
            }

            Gruppe("Lesen und Hören") {
                Schalter(
                    titel = "Größere Schrift",
                    beschreibung = "Alle Texte werden etwas größer.",
                    an = e.grosseSchrift,
                    onChange = { an -> vm.setzeEinstellungen { it.copy(grosseSchrift = an) } },
                )
                if (vm.vorleser.verfuegbar) {
                    Schalter(
                        titel = "Automatisch vorlesen",
                        beschreibung = "Italienische Wörter und Lösungen werden vorgelesen.",
                        an = e.autoVorlesen,
                        onChange = { an -> vm.setzeEinstellungen { it.copy(autoVorlesen = an) } },
                    )
                    TextButton(onClick = { vm.vorleser.sprich("Ciao! Come stai?") }) { Text("🔊 Stimme testen") }
                } else {
                    Text(
                        "Vorlesen ist gerade nicht möglich: Auf diesem Gerät ist keine italienische Stimme installiert. " +
                            "Du kannst sie in den Android-Einstellungen unter „Sprachausgabe“ herunterladen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Gruppe("Dein Lernstand") {
                val woerter = stand.vokabeln.count { it.value >= 1 }
                val aufgaben = stand.uebungen.count { it.value >= 1 }
                val einheiten = stand.einheiten.count { it.value.sterne > 0 }
                Text(
                    "💾 Wird nach jeder Antwort auf dem Handy gespeichert – ohne Internet.\n" +
                        "$woerter Wörter gewusst · $aufgaben Aufgaben gelöst · $einheiten Einheiten abgeschlossen",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { zeigeZuruecksetzen = true }) { Text("Lernstand zurücksetzen") }
            }
        }
    }

    if (zeigeZuruecksetzen) {
        AlertDialog(
            onDismissRequest = { zeigeZuruecksetzen = false },
            title = { Text("Lernstand zurücksetzen?") },
            text = { Text("Alle Sterne, gelernten Wörter und gelösten Aufgaben werden gelöscht. Die Einstellungen bleiben.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.lernstandZuruecksetzen()
                    zeigeZuruecksetzen = false
                }) { Text("Zurücksetzen") }
            },
            dismissButton = { TextButton(onClick = { zeigeZuruecksetzen = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Gruppe(titel: String, inhalt: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titel, style = MaterialTheme.typography.titleLarge)
            inhalt()
        }
    }
}

/** Ganze Zeile ist antippbar, nicht nur der kleine Schalter. */
@Composable
private fun Schalter(titel: String, beschreibung: String, an: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = an, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.titleMedium)
            Text(beschreibung, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = an, onCheckedChange = null)
    }
}
