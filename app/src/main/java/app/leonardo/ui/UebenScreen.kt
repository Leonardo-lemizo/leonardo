package app.leonardo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.leonardo.LeonardoViewModel
import app.leonardo.R
import app.leonardo.data.Uebung
import app.leonardo.data.Vokabel
import app.leonardo.logic.Bewertung
import app.leonardo.logic.Frage
import app.leonardo.logic.Tipps
import app.leonardo.logic.UebenRunde
import kotlinx.coroutines.delay

private val AKZENTE = listOf("à", "è", "é", "ì", "ò", "ù")

@Composable
fun UebenScreen(vm: LeonardoViewModel, runde: UebenRunde) {
    val stand by vm.stand.collectAsState()
    val haptik = LocalHapticFeedback.current
    val vorlesen: ((String) -> Unit)? = if (vm.vorleser.verfuegbar) vm.vorleser::sprich else null

    // Rückmeldung zum Fühlen und Hören, sobald eine Antwort bewertet ist.
    LaunchedEffect(runde.index, runde.bewertung) {
        val bewertung = runde.bewertung ?: return@LaunchedEffect
        haptik.performHapticFeedback(
            if (bewertung == Bewertung.FALSCH) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress,
        )
        val text = (runde.aktuelle as? Frage.Aufgabe)?.uebung?.vorleseText
        if (text != null && stand.einstellungen.autoVorlesen) vm.vorleser.sprich(text)
    }
    // Sterne speichern, sobald die Runde zu Ende ist.
    LaunchedEffect(runde.fertig) {
        if (runde.fertig) vm.rundeFertig(runde)
    }

    Scaffold(
        topBar = { LeonardoTopBar("✏️ ${runde.titel}", onZurueck = vm::zurueck) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            Fortschrittsbalken(
                anteil = runde.index / runde.fragen.size.coerceAtLeast(1).toFloat(),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            AnimatedContent(
                targetState = runde.index,
                transitionSpec = {
                    (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260, delayMillis = 80))) togetherWith fadeOut(tween(80))
                },
                label = "frage",
            ) { frageIndex ->
                val inhalt = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)
                when (val frage = runde.fragen.getOrNull(frageIndex)) {
                    null -> Box(Modifier.fillMaxSize()) {
                        Ergebnis(runde, onFertig = vm::zurueck, modifier = inhalt)
                        if (runde.sterne >= 2) Konfetti()
                    }
                    is Frage.Paare -> PaareSpiel(runde, frage, frageIndex, vorlesen, inhalt)
                    is Frage.Aufgabe -> AufgabeAnsicht(runde, frage, frageIndex, vorlesen, inhalt)
                }
            }
        }
    }
}

@Composable
private fun Kopfzeile(runde: UebenRunde, frageIndex: Int, typ: String) {
    val farben = LocalRueckmeldefarben.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Etikett(typ, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        if (runde.istZweiterVersuch(frageIndex)) Etikett("Zweiter Versuch", farben.fast, farben.aufFast)
    }
}

@Composable
private fun AufgabeAnsicht(
    runde: UebenRunde,
    frage: Frage.Aufgabe,
    frageIndex: Int,
    vorlesen: ((String) -> Unit)?,
    modifier: Modifier,
) {
    val u = frage.uebung
    val fokus = LocalFocusManager.current
    val eingabeFokus = remember { FocusRequester() }
    val aktiv = frageIndex == runde.index
    val bewertung = if (aktiv) runde.bewertung else null
    val beantwortet = bewertung != null
    val freitext = !u.istMultipleChoice && !frage.satzbau

    fun pruefen() {
        runde.pruefeEingabe()
        if (runde.bewertung != null) fokus.clearFocus()
    }

    LaunchedEffect(Unit) {
        if (freitext) eingabeFokus.requestFocus()
        if (u.typ == "hoeren") u.vorlesen?.let { text -> vorlesen?.invoke(text) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Kopfzeile(runde, frageIndex, if (frage.satzbau) "Sätze bauen" else u.typName)

        // Aufgabe + deutsche Hilfe auf Knopfdruck.
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(u.aufgabe, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (u.aufgabeDe != null) {
                FilledTonalButton(
                    onClick = { if (aktiv) runde.deHilfe = !runde.deHilfe },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text("DE") }
            }
        }
        AnimatedVisibility(visible = aktiv && runde.deHilfe && u.aufgabeDe != null, enter = expandVertically() + fadeIn()) {
            Text(
                "🇩🇪 ${u.aufgabeDe}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (u.typ == "hoeren" && vorlesen != null && u.vorlesen != null) {
            FilledTonalButton(
                onClick = { vorlesen(u.vorlesen) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) {
                Icon(painterResource(R.drawable.ic_lautsprecher), contentDescription = null, modifier = Modifier.size(32.dp))
                Text("  Nochmal anhören", style = MaterialTheme.typography.titleMedium)
            }
        }

        when {
            u.istMultipleChoice -> runde.optionen(frageIndex).forEach { option ->
                OptionKarte(
                    text = option,
                    richtig = beantwortet && option == u.loesung,
                    falschGewaehlt = beantwortet && option == runde.gewaehlt && option != u.loesung,
                    onClick = { if (aktiv) runde.waehle(option) },
                )
            }

            frage.satzbau -> {
                val teile = runde.bausteine(frageIndex)
                // Antwortzeile: gelegte Bausteine (antippen = zurücklegen).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                ) {
                    FlowRow(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (runde.gelegt.isEmpty()) {
                            Text("Tippe die Wörter in der richtigen Reihenfolge an.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        runde.gelegt.forEachIndexed { stelle, pos ->
                            Baustein(teile[pos], hervorgehoben = true) { if (aktiv) runde.nimmBaustein(stelle) }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    teile.forEachIndexed { pos, wort ->
                        if (pos !in runde.gelegt) Baustein(wort, hervorgehoben = false) { if (aktiv) runde.legeBaustein(pos) }
                    }
                }
                if (!beantwortet) {
                    GrosserKnopf("Prüfen", onClick = runde::pruefeSatzbau, enabled = runde.gelegt.size == teile.size)
                }
            }

            else -> {
                OutlinedTextField(
                    value = runde.eingabe,
                    onValueChange = { if (aktiv && !beantwortet) runde.eingabe = it },
                    placeholder = { Text("Deine Antwort", style = MaterialTheme.typography.titleLarge) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    readOnly = beantwortet,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { pruefen() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(eingabeFokus),
                )
                if (!beantwortet) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AKZENTE.forEach { zeichen ->
                            FilledTonalButton(
                                onClick = { runde.zeichenEinfuegen(zeichen) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(zeichen, style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                    GrosserKnopf("Prüfen", onClick = { pruefen() }, enabled = runde.eingabe.text.isNotBlank())
                }
            }
        }

        // Tipps: 1. deutsche Bedeutung, 2. Anfangsbuchstaben.
        if (!beantwortet && !u.istMultipleChoice && aktiv) TippBereich(runde, u)

        AnimatedVisibility(visible = beantwortet, enter = fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.92f)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Rueckmeldung(u, bewertung ?: Bewertung.RICHTIG, vorlesen)
                GrosserKnopf("Weiter", onClick = runde::weiter)
            }
        }
    }
}

@Composable
private fun TippBereich(runde: UebenRunde, u: Uebung) {
    val erster = Tipps.erster(u)
    val stufen = listOfNotNull(erster, Tipps.zweiter(u))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stufen.take(runde.tippStufe).forEach { tipp ->
            Text("💡 $tipp", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (runde.tippStufe < stufen.size) {
            TextButton(onClick = {
                runde.naechsterTipp()
                if (erster == null && runde.tippStufe == 1) runde.naechsterTipp()
            }) {
                Text(if (runde.tippStufe == 0) "💡 Tipp zeigen" else "💡 Noch ein Tipp")
            }
        }
    }
}

@Composable
private fun Baustein(text: String, hervorgehoben: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (hervorgehoben) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
private fun OptionKarte(text: String, richtig: Boolean, falschGewaehlt: Boolean, onClick: () -> Unit) {
    val farben = LocalRueckmeldefarben.current
    val (hintergrund, schrift, zeichen) = when {
        richtig -> Triple(farben.richtig, farben.aufRichtig, "✓  ")
        falschGewaehlt -> Triple(farben.falsch, farben.aufFalsch, "✗  ")
        else -> Triple(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurface, "")
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        colors = CardDefaults.cardColors(containerColor = hintergrund, contentColor = schrift),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 20.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
            Text(zeichen + text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Paare finden: links Italienisch, rechts Deutsch. Erst links, dann rechts antippen. */
@Composable
private fun PaareSpiel(
    runde: UebenRunde,
    frage: Frage.Paare,
    frageIndex: Int,
    vorlesen: ((String) -> Unit)?,
    modifier: Modifier,
) {
    val links = remember(frageIndex) { frage.vokabeln.shuffled() }
    val rechts = remember(frageIndex) { frage.vokabeln.shuffled() }
    val gefunden = remember(frageIndex) { mutableStateListOf<Vokabel>() }
    val mitFehler = remember(frageIndex) { mutableStateListOf<Vokabel>() }
    var auswahl by remember(frageIndex) { mutableStateOf<Vokabel?>(null) }
    var falschRechts by remember(frageIndex) { mutableStateOf<Vokabel?>(null) }
    var fehler by remember(frageIndex) { mutableIntStateOf(0) }
    val farben = LocalRueckmeldefarben.current
    val aktiv = frageIndex == runde.index

    LaunchedEffect(falschRechts) {
        if (falschRechts != null) {
            delay(600)
            falschRechts = null
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Kopfzeile(runde, frageIndex, "Paare finden")
        Text("Was gehört zusammen?", style = MaterialTheme.typography.headlineSmall)
        Text("Tippe erst links ein italienisches Wort an, dann rechts die Bedeutung.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                links.forEach { v ->
                    val fertig = v in gefunden
                    PaarKarte(
                        text = v.italienisch,
                        hintergrund = when {
                            fertig -> farben.richtig
                            auswahl == v -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        },
                        aktiv = aktiv && !fertig,
                    ) {
                        auswahl = v
                        vorlesen?.invoke(v.italienisch)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rechts.forEach { v ->
                    val fertig = v in gefunden
                    PaarKarte(
                        text = v.deutsch,
                        hintergrund = when {
                            fertig -> farben.richtig
                            falschRechts == v -> farben.falsch
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        },
                        aktiv = aktiv && !fertig && auswahl != null,
                    ) {
                        val gewaehlt = auswahl ?: return@PaarKarte
                        if (gewaehlt == v) {
                            gefunden += v
                            runde.paarBewertet(v, gewusst = v !in mitFehler)
                            auswahl = null
                            if (gefunden.size == frage.vokabeln.size) runde.paareFertig(fehler)
                        } else {
                            fehler++
                            if (gewaehlt !in mitFehler) mitFehler += gewaehlt
                            falschRechts = v
                        }
                    }
                }
            }
        }

        if (aktiv && runde.bewertung != null) {
            Text(
                if (fehler == 0) "✅ Alle Paare ohne Fehler!" else "Geschafft – mit $fehler ${if (fehler == 1) "Fehler" else "Fehlern"}.",
                style = MaterialTheme.typography.titleMedium,
            )
            GrosserKnopf("Weiter", onClick = runde::weiter)
        }
    }
}

@Composable
private fun PaarKarte(text: String, hintergrund: androidx.compose.ui.graphics.Color, aktiv: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = aktiv,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        colors = CardDefaults.cardColors(
            containerColor = hintergrund,
            disabledContainerColor = hintergrund,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Rueckmeldung(u: Uebung, bewertung: Bewertung, vorlesen: ((String) -> Unit)?) {
    val farben = LocalRueckmeldefarben.current
    val (hintergrund, schrift, titel) = when (bewertung) {
        Bewertung.RICHTIG -> Triple(farben.richtig, farben.aufRichtig, "✅ Richtig!")
        Bewertung.FAST_RICHTIG -> Triple(farben.fast, farben.aufFast, "🟡 Fast! Schau auf die Schreibweise.")
        Bewertung.FALSCH -> Triple(farben.falsch, farben.aufFalsch, "Noch nicht ganz.")
    }
    Surface(color = hintergrund, contentColor = schrift, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titel, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(u.loesung, style = MaterialTheme.typography.headlineSmall)
                    // Übersetzung der richtigen Lösung.
                    if (u.loesungDe != null) Text("= ${u.loesungDe}", style = MaterialTheme.typography.bodyLarge)
                    if (u.typ == "hoeren" && u.vorlesen != null) Text("🔊 ${u.vorlesen}", style = MaterialTheme.typography.bodyLarge)
                }
                val text = u.vorleseText
                if (vorlesen != null && text != null) {
                    FilledTonalIconButton(onClick = { vorlesen(text) }) {
                        Icon(painterResource(R.drawable.ic_lautsprecher), contentDescription = "Anhören")
                    }
                }
            }
            if (u.alternativen.isNotEmpty() && !u.istMultipleChoice && !u.id.startsWith("w:")) {
                Text("Auch richtig: ${u.alternativen.joinToString(" / ")}", style = MaterialTheme.typography.bodyMedium)
            }
            if (u.hinweis != null) Text("💡 ${u.hinweis}", style = MaterialTheme.typography.bodyMedium)
            Hinweise(u)
        }
    }
}

/** Dezente Zusatzinfos: "ohne Gewähr" bei generierten Lösungen und die Quelle. */
@Composable
private fun Hinweise(u: Uebung) {
    val dezent = LocalContentColor.current.copy(alpha = 0.7f)
    if (u.loesungGeneriert) Text("Lösung ohne Gewähr", style = MaterialTheme.typography.bodySmall, color = dezent)
    if (u.quelle != null) Text("Quelle: ${u.quelle}", style = MaterialTheme.typography.bodySmall, color = dezent)
}

@Composable
private fun Ergebnis(runde: UebenRunde, onFertig: () -> Unit, modifier: Modifier) {
    val richtig = runde.ergebnisse.count { it.second == Bewertung.RICHTIG }
    val fast = runde.ergebnisse.count { it.second == Bewertung.FAST_RICHTIG }
    val gesamt = runde.ergebnisse.size
    val zumWiederholen = runde.ergebnisse.filter { it.second != Bewertung.RICHTIG }.mapNotNull { (it.first as? Frage.Aufgabe)?.uebung }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EmojiKreis(if (runde.sterne >= 2) "🎉" else "💪", MaterialTheme.colorScheme.primaryContainer, groesse = 96.dp, modifier = Modifier.padding(top = 16.dp))
        Text("Geschafft!", style = MaterialTheme.typography.headlineMedium)
        if (runde.einheitId != null) Text(Sterne.text(runde.sterne), style = MaterialTheme.typography.headlineSmall)
        Text(
            "$richtig von $gesamt richtig" + (if (fast > 0) ", $fast fast richtig" else "") + ".\n💾 Dein Fortschritt ist gespeichert.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GrosserKnopf("Fertig", onClick = onFertig, modifier = Modifier.padding(top = 12.dp))

        if (zumWiederholen.isNotEmpty()) {
            Text("Das schauen wir uns nochmal an", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            zumWiederholen.forEach { u ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(u.aufgabe, style = MaterialTheme.typography.bodyMedium)
                        Text(u.loesung + (u.loesungDe?.let { " = $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                        Hinweise(u)
                    }
                }
            }
        }
    }
}
