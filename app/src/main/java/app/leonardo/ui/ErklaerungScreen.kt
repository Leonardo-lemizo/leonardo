package app.leonardo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.leonardo.LeonardoViewModel
import app.leonardo.R
import app.leonardo.data.Erklaerkarte
import app.leonardo.logic.ErklaerRunde

@Composable
fun ErklaerungScreen(vm: LeonardoViewModel, runde: ErklaerRunde) {
    val einheit = runde.einheit
    val vorlesen: ((String) -> Unit)? = if (vm.vorleser.verfuegbar) vm.vorleser::sprich else null

    Scaffold(
        topBar = { LeonardoTopBar("📖 ${einheit.titel}", onZurueck = vm::zurueck) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Fortschrittsbalken(
                anteil = (runde.karte + if (runde.imCheck) 0.5f else 0f) / runde.anzahl.coerceAtLeast(1),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            AnimatedContent(
                targetState = runde.karte to runde.imCheck,
                transitionSpec = {
                    (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260, delayMillis = 60))) togetherWith fadeOut(tween(80))
                },
                label = "erklaerkarte",
                modifier = Modifier.weight(1f),
            ) { (kartenIndex, imCheck) ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val karte = einheit.erklaerung.getOrNull(kartenIndex)
                    if (karte != null && !imCheck) {
                        Text(
                            "Karte ${kartenIndex + 1} von ${runde.anzahl}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ErklaerKarteAnsicht(karte, vorlesen)
                    } else if (karte != null) {
                        KurzCheck(runde, vorlesen)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (runde.karte > 0 || runde.imCheck) {
                    GrosserKnopf("Zurück", onClick = runde::zurueck, dezent = true, modifier = Modifier.weight(1f))
                }
                val letzte = runde.karte == runde.anzahl - 1 && (runde.imCheck || runde.check == null)
                GrosserKnopf(
                    text = when {
                        letzte -> "Verstanden – jetzt üben"
                        !runde.imCheck && runde.check != null -> "Kurz prüfen"
                        else -> "Weiter"
                    },
                    enabled = !runde.imCheck || runde.gewaehlt != null,
                    onClick = {
                        if (letzte) vm.erklaerungFertig(einheit) else runde.weiter()
                    },
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}

@Composable
fun ErklaerKarteAnsicht(karte: Erklaerkarte, vorlesen: ((String) -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(karte.titel, style = MaterialTheme.typography.headlineSmall)

            karte.punkte.forEach { punkt ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(punkt, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (karte.tabelle.isNotEmpty()) Tabelle(karte.tabelle)

            if (karte.beispiele.isNotEmpty()) {
                Text("Beispiele", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                karte.beispiele.forEach { b ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(b.italienisch, style = MaterialTheme.typography.titleMedium)
                                Text(b.deutsch, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (vorlesen != null) {
                                FilledTonalIconButton(onClick = { vorlesen(b.italienisch) }) {
                                    Icon(painterResource(R.drawable.ic_lautsprecher), contentDescription = "Anhören")
                                }
                            }
                        }
                    }
                }
            }

            if (karte.merke != null) {
                val farben = LocalRueckmeldefarben.current
                Surface(color = farben.fast, contentColor = farben.aufFast, shape = MaterialTheme.shapes.medium) {
                    Text(
                        "💡 ${karte.merke}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    )
                }
            }
        }
    }
}

/** Einfache Tabelle; die erste Zeile ist die Kopfzeile. */
@Composable
private fun Tabelle(zeilen: List<List<String>>) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
        zeilen.forEachIndexed { i, zeile ->
            val kopf = i == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (kopf) MaterialTheme.colorScheme.primaryContainer
                        else if (i % 2 == 0) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surface,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                zeile.forEach { zelle ->
                    Text(
                        zelle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (kopf) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun KurzCheck(runde: ErklaerRunde, vorlesen: ((String) -> Unit)?) {
    val check = runde.check ?: return
    val u = check.uebung
    val farben = LocalRueckmeldefarben.current
    Text("✅ Kurz-Check", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(u.aufgabe, style = MaterialTheme.typography.headlineSmall)
    runde.optionen().forEach { option ->
        val beantwortet = runde.gewaehlt != null
        val (hintergrund, schrift) = when {
            beantwortet && option == u.loesung -> farben.richtig to farben.aufRichtig
            beantwortet && option == runde.gewaehlt -> farben.falsch to farben.aufFalsch
            else -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface
        }
        Card(
            onClick = {
                runde.waehle(option)
                u.vorlesen?.let { text -> vorlesen?.invoke(text) }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = hintergrund, contentColor = schrift),
        ) {
            Text(option, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        }
    }
    if (runde.gewaehlt != null) {
        Text(
            if (runde.gewaehlt == u.loesung) "Richtig! 🎉" else "Fast – richtig ist: ${u.loesung}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
