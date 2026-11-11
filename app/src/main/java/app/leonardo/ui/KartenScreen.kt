package app.leonardo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leonardo.LeonardoViewModel
import app.leonardo.R
import app.leonardo.Screen
import app.leonardo.logic.Karte

@Composable
fun KartenScreen(vm: LeonardoViewModel, screen: Screen.Karten) {
    val runde = screen.runde
    val karte = runde.aktuelle
    val stand by vm.stand.collectAsState()
    val haptik = LocalHapticFeedback.current
    val vorlesenMoeglich = vm.vorleser.verfuegbar

    // Automatisch vorlesen, sobald die italienische Seite zu sehen ist.
    LaunchedEffect(runde.schritt, runde.umgedreht, vorlesenMoeglich) {
        if (karte != null && stand.einstellungen.autoVorlesen && karte.italienischVorne != runde.umgedreht) {
            vm.vorleser.sprich(karte.vokabel.italienisch)
        }
    }

    Scaffold(
        topBar = { LeonardoTopBar("🃏 ${screen.titel}", onZurueck = vm::zurueck) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (karte == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    EmojiKreis("🎉", MaterialTheme.colorScheme.primaryContainer, groesse = 96.dp)
                    Text("Geschafft!", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${runde.gewusstBeimErstenMal} von ${runde.anzahl} Wörtern gleich gewusst.\n💾 Gespeichert.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GrosserKnopf("Weiter zur Einheit", onClick = vm::zurueck, modifier = Modifier.padding(top = 16.dp))
                }
                Konfetti()
                return@Box
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Fortschrittsbalken(runde.anteilGeschafft)

                key(runde.schritt) {
                    UmdrehKarte(
                        karte = karte,
                        umgedreht = runde.umgedreht,
                        onClick = { runde.umgedreht = !runde.umgedreht },
                        onVorlesen = if (vorlesenMoeglich) ({ vm.vorleser.sprich(karte.vokabel.italienisch) }) else null,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                if (runde.umgedreht) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GrosserKnopf(
                            text = "🤔 Noch nicht",
                            onClick = {
                                haptik.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                runde.bewerte(gewusst = false)
                            },
                            dezent = true,
                            modifier = Modifier.weight(1f),
                        )
                        GrosserKnopf(
                            text = "✅ Gewusst",
                            onClick = {
                                haptik.performHapticFeedback(HapticFeedbackType.LongPress)
                                runde.bewerte(gewusst = true)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    GrosserKnopf(text = "Umdrehen", onClick = { runde.umgedreht = true })
                }
            }
        }
    }
}

@Composable
private fun UmdrehKarte(
    karte: Karte,
    umgedreht: Boolean,
    onClick: () -> Unit,
    onVorlesen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val drehung by animateFloatAsState(
        targetValue = if (umgedreht) 180f else 0f,
        animationSpec = tween(durationMillis = 380),
        label = "umdrehen",
    )
    val rueckseite = drehung > 90f
    val farbe = if (rueckseite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val italienischSichtbar = karte.italienischVorne != rueckseite

    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            rotationY = drehung
            cameraDistance = 14f * density
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = farbe),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .graphicsLayer { rotationY = if (rueckseite) 180f else 0f },
        ) {
            if (onVorlesen != null && italienischSichtbar) {
                FilledTonalIconButton(onClick = onVorlesen, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(painterResource(R.drawable.ic_lautsprecher), contentDescription = "Anhören")
                }
            }
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!rueckseite) {
                    Text(karte.vorne, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                } else {
                    Text(
                        karte.vorne,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(karte.hinten, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    GenusEtikett(karte.vokabel.genus, karte.vokabel.wortart)
                }
            }
        }
    }
}

/** Farbiges Etikett fürs Genus: Blau = männlich, Terrakotta = weiblich. */
@Composable
fun GenusEtikett(genus: String?, wortart: String?) {
    if (genus == null) {
        if (wortart != null) {
            Etikett(wortart, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), MaterialTheme.colorScheme.onSurface)
        }
        return
    }
    val plural = if ("Plural" in genus) " · Mehrzahl" else ""
    val (text, hintergrund, schrift) = when {
        genus.startsWith("m/f") -> Triple("männlich / weiblich", Color(0xFFE3DDF3), Color(0xFF2B2148))
        genus.startsWith("m") -> Triple("männlich", Color(0xFFCFE3F7), Color(0xFF0E3355))
        else -> Triple("weiblich", Color(0xFFF6DCD2), Color(0xFF3B140A))
    }
    Etikett(text + plural, hintergrund, schrift)
}
