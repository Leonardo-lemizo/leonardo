package app.leonardo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.leonardo.data.ContentLoader
import app.leonardo.data.Einheit
import app.leonardo.data.Einstellungen
import app.leonardo.data.Inhalt
import app.leonardo.data.Kapitel
import app.leonardo.data.Lernstand
import app.leonardo.data.LernstandSpeicher
import app.leonardo.data.MAX_FACH
import app.leonardo.logic.Bewertung
import app.leonardo.logic.ErklaerRunde
import app.leonardo.logic.FragenBauer
import app.leonardo.logic.Fortschritt
import app.leonardo.logic.Frage
import app.leonardo.logic.Karte
import app.leonardo.logic.KartenRunde
import app.leonardo.logic.UebenRunde
import app.leonardo.logic.Vorleser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.pow
import kotlin.random.Random

sealed interface Screen {
    data object Start : Screen
    data object Einstellungen : Screen
    data class KapitelSeite(val kapitelId: String) : Screen
    data class EinheitSeite(val einheitId: String) : Screen
    data class Erklaerung(val runde: ErklaerRunde) : Screen
    data class Karten(val zurueck: Screen, val titel: String, val runde: KartenRunde) : Screen
    data class Ueben(val zurueck: Screen, val runde: UebenRunde) : Screen
}

class LeonardoViewModel(app: Application) : AndroidViewModel(app) {

    val inhalt: Inhalt = ContentLoader.laden(app)
    val vorleser = Vorleser(app)
    private val bauer = FragenBauer(inhalt)
    private val speicher = LernstandSpeicher(app)

    private val _stand = MutableStateFlow(speicher.laden())
    val stand: StateFlow<Lernstand> = _stand.asStateFlow()

    /** Schreibt im Hintergrund immer den neuesten Stand (ältere werden übersprungen). */
    private val schreibAuftraege = Channel<Lernstand>(Channel.CONFLATED)

    var screen by mutableStateOf<Screen>(Screen.Start); private set

    /** true: Italienisch vorne auf der Karteikarte. */
    var italienischVorne by mutableStateOf(true)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (s in schreibAuftraege) speicher.speichern(s)
        }
    }

    val heute: Long get() = LocalDate.now().toEpochDay()
    private val einstellungen: Einstellungen get() = _stand.value.einstellungen
    private val fragenProRunde: Int get() = if (einstellungen.kurzeRunden) 10 else 18
    private val kartenProRunde: Int get() = if (einstellungen.kurzeRunden) 10 else 20

    private fun aendern(aenderung: (Lernstand) -> Lernstand) {
        val neu = _stand.updateAndGet(aenderung)
        schreibAuftraege.trySend(neu)
    }

    /** Sofort speichern, z. B. wenn die App in den Hintergrund geht. */
    fun sichernJetzt() {
        speicher.speichern(_stand.value)
    }

    // --- Fortschritt ---

    /** Anteil (0–1) der Wörter und Aufgaben einer Einheit, die schon einmal richtig waren. */
    fun fortschritt(einheit: Einheit, s: Lernstand = _stand.value): Float {
        var gesamt = einheit.uebungen.size + einheit.vokabeln.size
        var geschafft = einheit.uebungen.count { (s.uebungen[it.id] ?: 0) >= 1 } +
            einheit.vokabeln.count { (s.vokabeln[it.italienisch] ?: 0) >= 1 }
        if (einheit.kapitelId == "verbi") {
            val verben = einheit.verben.mapNotNull { inhalt.verben[it] }
            gesamt += verben.size * 6
            geschafft += verben.sumOf { v -> (0..5).count { p -> (s.uebungen[FragenBauer.verbSchluessel(v, p)] ?: 0) >= 1 } }
        }
        if (gesamt == 0) return if (s.einheit(einheit.id).sterne > 0) 1f else 0f
        return geschafft / gesamt.toFloat()
    }

    fun fortschritt(kapitel: Kapitel, s: Lernstand = _stand.value): Float =
        if (kapitel.einheiten.isEmpty()) 0f else kapitel.einheiten.map { fortschritt(it, s) }.average().toFloat()

    /** Die Einheit, mit der es sinnvoll weitergeht. */
    fun naechsteEinheit(s: Lernstand = _stand.value): Einheit? {
        val lernpfad = inhalt.kapitel.filter { it.id != "verbi" }.flatMap { it.einheiten }
        val zuletzt = inhalt.einheit(s.letzteEinheit)
        if (zuletzt != null && s.einheit(zuletzt.id).sterne == 0) return zuletzt
        return lernpfad.firstOrNull { s.einheit(it.id).sterne == 0 } ?: lernpfad.lastOrNull()
    }

    fun anzahlZumWiederholen(s: Lernstand = _stand.value): Int =
        s.uebungen.count { it.value == 0 } + s.vokabeln.count { it.value <= 1 }

    // --- Navigation ---

    fun zeige(ziel: Screen) {
        vorleser.stopp()
        screen = ziel
    }

    fun zurueck() {
        vorleser.stopp()
        screen = when (val s = screen) {
            is Screen.Karten -> s.zurueck
            is Screen.Ueben -> s.zurueck
            is Screen.Erklaerung -> Screen.EinheitSeite(s.runde.einheit.id)
            is Screen.EinheitSeite -> inhalt.einheit(s.einheitId)?.let { Screen.KapitelSeite(it.kapitelId) } ?: Screen.Start
            else -> Screen.Start
        }
    }

    fun oeffneKapitel(kapitel: Kapitel) = zeige(Screen.KapitelSeite(kapitel.id))

    fun oeffneEinheit(einheit: Einheit) {
        aendern { it.copy(letzteEinheit = einheit.id) }
        zeige(Screen.EinheitSeite(einheit.id))
    }

    /** "Weiter lernen": Erklärung, falls noch nicht gelesen, sonst direkt üben. */
    fun weiterLernen() {
        val einheit = naechsteEinheit() ?: return
        aendern { it.copy(letzteEinheit = einheit.id) }
        if (!_stand.value.einheit(einheit.id).erklaerungGelesen) starteErklaerung(einheit)
        else starteUeben(einheit)
    }

    // --- Erklärung ---

    fun starteErklaerung(einheit: Einheit) {
        val checks = einheit.erklaerung.map { bauer.kurzCheck(it, einheit) }
        zeige(Screen.Erklaerung(ErklaerRunde(einheit, checks)))
    }

    /** Nach der letzten Erklärkarte direkt selbst üben. */
    fun erklaerungFertig(einheit: Einheit) {
        aendern { it.mitErklaerungGelesen(einheit.id) }
        starteUeben(einheit)
    }

    // --- Üben ---

    fun starteUeben(einheit: Einheit) {
        val fragen = bauer.fuerEinheit(einheit, _stand.value, fragenProRunde, vorleser.verfuegbar)
        val runde = UebenRunde(fragen, einheit.titel, einheit.id, ::speichereErgebnis, ::speichereWort)
        zeige(Screen.Ueben(Screen.EinheitSeite(einheit.id), runde))
    }

    fun starteWiederholung() {
        val fragen = bauer.wiederholung(_stand.value, fragenProRunde, vorleser.verfuegbar)
        if (fragen.isEmpty()) {
            weiterLernen()
            return
        }
        zeige(Screen.Ueben(Screen.Start, UebenRunde(fragen, "Wiederholen", null, ::speichereErgebnis, ::speichereWort)))
    }

    fun rundeFertig(runde: UebenRunde) {
        val id = runde.einheitId ?: return
        aendern { it.mitSternen(id, runde.sterne) }
    }

    private fun speichereErgebnis(frage: Frage, bewertung: Bewertung, ersterVersuch: Boolean) {
        val tag = heute
        aendern { alt ->
            var neu = alt.mitAntwort(tag)
            if (ersterVersuch && frage is Frage.Aufgabe) {
                val richtig = bewertung != Bewertung.FALSCH
                neu = when (val f = frage.fortschritt) {
                    is Fortschritt.Aufgabe -> neu.mitUebung(f.id, richtig)
                    is Fortschritt.Verbform -> neu.mitUebung(f.schluessel, richtig)
                    is Fortschritt.Wort -> neu.mitVokabel(f.italienisch, richtig)
                    Fortschritt.Keiner -> neu
                }
            }
            neu
        }
    }

    private fun speichereWort(vokabel: app.leonardo.data.Vokabel, gewusst: Boolean) {
        aendern { it.mitVokabel(vokabel.italienisch, gewusst) }
    }

    // --- Karteikarten ---

    fun starteKarten(einheit: Einheit) {
        val s = _stand.value
        // Gewichtete Auswahl: neue und nicht gewusste Wörter kommen öfter dran.
        val karten = einheit.vokabeln
            .map { v ->
                val fach = s.vokabeln[v.italienisch] ?: 0
                v to Random.nextDouble().pow(1.0 / (MAX_FACH + 1.0 - fach).pow(2))
            }
            .sortedByDescending { it.second }
            .take(kartenProRunde)
            .map { Karte(it.first, italienischVorne) }
        val runde = KartenRunde(karten) { karte, gewusst ->
            aendern { it.mitVokabel(karte.vokabel.italienisch, gewusst).mitAntwort(heute) }
        }
        zeige(Screen.Karten(Screen.EinheitSeite(einheit.id), einheit.titel, runde))
    }

    // --- Einstellungen ---

    fun setzeEinstellungen(aenderung: (Einstellungen) -> Einstellungen) {
        aendern { it.copy(einstellungen = aenderung(it.einstellungen)) }
    }

    fun lernstandZuruecksetzen() {
        val einstellungen = _stand.value.einstellungen
        aendern { Lernstand(einstellungen = einstellungen) }
    }

    override fun onCleared() {
        sichernJetzt()
        vorleser.beenden()
    }
}
