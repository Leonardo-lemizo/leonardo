package app.leonardo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.leonardo.LeonardoViewModel

@Composable
fun EinheitScreen(vm: LeonardoViewModel, einheitId: String) {
    val einheit = vm.inhalt.einheit(einheitId) ?: return
    val stand by vm.stand.collectAsState()
    val einheitStand = stand.einheit(einheit.id)
    val anteil = vm.fortschritt(einheit, stand)
    val woerterGelernt = einheit.vokabeln.count { (stand.vokabeln[it.italienisch] ?: 0) >= 1 }
    val kapitel = vm.inhalt.kapitelVon(einheit)

    Scaffold(
        topBar = { LeonardoTopBar(kapitel?.let { titelTeile(it.titel).first } ?: "Einheit", onZurueck = vm::zurueck) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Fortschrittsring(anteil = anteil, modifier = Modifier.size(76.dp)) {
                    Text(einheit.emoji, style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(einheit.titel, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (einheitStand.sterne > 0) Sterne.text(einheitStand.sterne) else "${(anteil * 100).toInt()} % geübt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("🎯 ${einheit.ziel}", style = MaterialTheme.typography.bodyLarge)

            GrosserKnopf(
                text = if (!einheitStand.erklaerungGelesen) "Los geht's: erst verstehen, dann üben" else "Jetzt üben",
                onClick = {
                    if (!einheitStand.erklaerungGelesen) vm.starteErklaerung(einheit) else vm.starteUeben(einheit)
                },
            )

            Schritt(
                nummer = "1",
                emoji = "📖",
                titel = "Erklärung",
                text = (if (einheit.erklaerung.size == 1) "1 kurze Karte" else "${einheit.erklaerung.size} kurze Karten") + " mit Beispielen" + if (einheitStand.erklaerungGelesen) " · ✓ gelesen" else "",
                onClick = { vm.starteErklaerung(einheit) },
            )
            if (einheit.vokabeln.isNotEmpty()) {
                Schritt(
                    nummer = "2",
                    emoji = "🃏",
                    titel = "Wörter ansehen",
                    text = "$woerterGelernt von ${einheit.vokabeln.size} Wörtern gewusst",
                    onClick = { vm.starteKarten(einheit) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Zuerst:", style = MaterialTheme.typography.bodyMedium)
                        FilterChip(selected = vm.italienischVorne, onClick = { vm.italienischVorne = true }, label = { Text("Italienisch") })
                        FilterChip(selected = !vm.italienischVorne, onClick = { vm.italienischVorne = false }, label = { Text("Deutsch") })
                    }
                }
            }
            Schritt(
                nummer = if (einheit.vokabeln.isNotEmpty()) "3" else "2",
                emoji = "✏️",
                titel = "Üben",
                text = buildString {
                    append("Gemischte Übungen")
                    if (einheit.uebungen.isNotEmpty()) append(" · ${einheit.uebungen.size} Kursaufgaben")
                    if (einheit.verben.isNotEmpty()) append(" · Verben: ${einheit.verben.joinToString(", ")}")
                    if (einheit.mix) append(" · mit allem aus dem Kapitel")
                },
                onClick = { vm.starteUeben(einheit) },
            )
            Text(
                "💾 Dein Fortschritt wird nach jeder Antwort automatisch gespeichert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Schritt(
    nummer: String,
    emoji: String,
    titel: String,
    text: String,
    onClick: () -> Unit,
    zusatz: @Composable () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EmojiKreis(emoji, MaterialTheme.colorScheme.primaryContainer, groesse = 52.dp)
                Column(Modifier.weight(1f)) {
                    Text("$nummer · $titel", style = MaterialTheme.typography.titleMedium)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            zusatz()
        }
    }
}
