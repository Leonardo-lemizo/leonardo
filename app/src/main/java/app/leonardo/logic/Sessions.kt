package app.leonardo.logic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.leonardo.data.Einheit
import app.leonardo.data.Vokabel

/** Eine Karteikarte, wie sie angezeigt wird. */
data class Karte(
    val vokabel: Vokabel,
    val italienischVorne: Boolean,
) {
    val vorne: String get() = if (italienischVorne) vokabel.italienisch else vokabel.deutsch
    val hinten: String get() = if (italienischVorne) vokabel.deutsch else vokabel.italienisch
}

/**
 * Eine Karteikarten-Runde. Nicht gewusste Karten werden wenige Karten
 * später erneut eingereiht (höchstens zweimal pro Runde).
 */
class KartenRunde(
    karten: List<Karte>,
    private val beiBewertung: (Karte, gewusst: Boolean) -> Unit,
) {
    private val warteschlange = ArrayDeque(karten)
    private val wiederholungen = mutableMapOf<String, Int>()
    private val schonGesehen = mutableSetOf<String>()

    val anzahl = karten.size
    var aktuelle by mutableStateOf(warteschlange.firstOrNull()); private set
    var umgedreht by mutableStateOf(false)
    var verbleibend by mutableIntStateOf(warteschlange.size); private set
    var gewusstBeimErstenMal by mutableIntStateOf(0); private set

    /** Zählt jede Bewertung hoch, damit die Kartenansicht für jede neue Karte frisch startet. */
    var schritt by mutableIntStateOf(0); private set

    val anteilGeschafft: Float
        get() = if (schritt + verbleibend == 0) 0f else schritt / (schritt + verbleibend).toFloat()

    fun bewerte(gewusst: Boolean) {
        val karte = warteschlange.removeFirstOrNull() ?: return
        val schluessel = karte.vokabel.italienisch
        val ersterVersuch = schonGesehen.add(schluessel)
        if (ersterVersuch) {
            beiBewertung(karte, gewusst)
            if (gewusst) gewusstBeimErstenMal++
        }
        if (!gewusst) {
            val bisher = wiederholungen.getOrDefault(schluessel, 0)
            if (bisher < 2) {
                wiederholungen[schluessel] = bisher + 1
                warteschlange.add(minOf(3, warteschlange.size), karte)
            }
        }
        umgedreht = false
        schritt++
        aktuelle = warteschlange.firstOrNull()
        verbleibend = warteschlange.size
    }
}

/** Erklärung einer Einheit: Karte für Karte, nach jeder Karte ggf. ein Kurz-Check. */
class ErklaerRunde(val einheit: Einheit, private val checks: List<Frage.Aufgabe?>) {
    var karte by mutableIntStateOf(0); private set
    var imCheck by mutableStateOf(false); private set
    var gewaehlt by mutableStateOf<String?>(null); private set
    private val optionenCache = mutableMapOf<Int, List<String>>()

    val anzahl: Int get() = einheit.erklaerung.size
    val check: Frage.Aufgabe? get() = checks.getOrNull(karte)
    val fertig: Boolean get() = karte >= anzahl

    fun optionen(): List<String> =
        optionenCache.getOrPut(karte) { check?.uebung?.optionen?.shuffled().orEmpty() }

    fun waehle(option: String) {
        if (gewaehlt == null) gewaehlt = option
    }

    /** Weiter: erst zum Kurz-Check (falls vorhanden), dann zur nächsten Karte. */
    fun weiter() {
        if (!imCheck && check != null) {
            imCheck = true
            return
        }
        imCheck = false
        gewaehlt = null
        karte++
    }

    fun zurueck() {
        if (imCheck) {
            imCheck = false
            gewaehlt = null
        } else if (karte > 0) {
            karte--
        }
    }
}

/**
 * Eine Übungsrunde mit gemischten Fragen. Falsch beantwortete Fragen
 * kommen am Ende einmal als "zweiter Versuch" wieder.
 */
class UebenRunde(
    start: List<Frage>,
    val titel: String,
    val einheitId: String?,
    private val beiErgebnis: (Frage, Bewertung, ersterVersuch: Boolean) -> Unit,
    private val beiPaar: (Vokabel, gewusst: Boolean) -> Unit,
) {
    val fragen = mutableStateListOf<Frage>().apply { addAll(start) }
    private val anzahlErstfragen = start.size
    private val optionenCache = mutableMapOf<Int, List<String>>()
    private val bausteinCache = mutableMapOf<Int, List<String>>()

    var index by mutableIntStateOf(0); private set
    var eingabe by mutableStateOf(TextFieldValue(""))
    var gewaehlt by mutableStateOf<String?>(null); private set
    var bewertung by mutableStateOf<Bewertung?>(null); private set
    var tippStufe by mutableIntStateOf(0); private set
    var deHilfe by mutableStateOf(false)

    /** Satzbau: Positionen der bereits gelegten Bausteine (in Reihenfolge). */
    val gelegt = mutableStateListOf<Int>()

    /** Ergebnisse der ersten Versuche (für Sterne und Auswertung). */
    val ergebnisse = mutableStateListOf<Pair<Frage, Bewertung>>()

    val aktuelle: Frage? get() = fragen.getOrNull(index)
    val fertig: Boolean get() = index >= fragen.size

    fun istZweiterVersuch(frageIndex: Int): Boolean = frageIndex >= anzahlErstfragen

    fun optionen(frageIndex: Int): List<String> = optionenCache.getOrPut(frageIndex) {
        (fragen[frageIndex] as? Frage.Aufgabe)?.uebung?.optionen?.distinct()?.shuffled().orEmpty()
    }

    fun bausteine(frageIndex: Int): List<String> = bausteinCache.getOrPut(frageIndex) {
        val woerter = (fragen[frageIndex] as? Frage.Aufgabe)?.uebung?.loesung?.split(" ")
            ?.filter { it.isNotBlank() }.orEmpty()
        var gemischt = woerter.shuffled()
        repeat(5) { if (gemischt == woerter && woerter.size > 1) gemischt = woerter.shuffled() }
        gemischt
    }

    fun naechsterTipp() {
        if (tippStufe < 2) tippStufe++
    }

    fun waehle(option: String) {
        val frage = aktuelle as? Frage.Aufgabe ?: return
        if (bewertung != null) return
        gewaehlt = option
        abschliessen(if (option == frage.uebung.loesung) Bewertung.RICHTIG else Bewertung.FALSCH)
    }

    fun pruefeEingabe() {
        val frage = aktuelle as? Frage.Aufgabe ?: return
        if (bewertung != null || eingabe.text.isBlank()) return
        abschliessen(AnswerChecker.pruefe(eingabe.text, listOf(frage.uebung.loesung) + frage.uebung.alternativen))
    }

    /** Fügt ein Zeichen (z. B. "è") an der Cursorposition ein. */
    fun zeichenEinfuegen(zeichen: String) {
        if (bewertung != null) return
        val auswahl = eingabe.selection
        val text = eingabe.text.replaceRange(auswahl.min, auswahl.max, zeichen)
        eingabe = TextFieldValue(text, TextRange(auswahl.min + zeichen.length))
    }

    fun legeBaustein(position: Int) {
        if (bewertung == null && position !in gelegt) gelegt.add(position)
    }

    fun nimmBaustein(stelle: Int) {
        if (bewertung == null && stelle in gelegt.indices) gelegt.removeAt(stelle)
    }

    fun pruefeSatzbau() {
        val frage = aktuelle as? Frage.Aufgabe ?: return
        if (bewertung != null || gelegt.isEmpty()) return
        val teile = bausteine(index)
        val satz = gelegt.joinToString(" ") { teile[it] }
        abschliessen(AnswerChecker.pruefe(satz, listOf(frage.uebung.loesung) + frage.uebung.alternativen))
    }

    fun paarBewertet(vokabel: Vokabel, gewusst: Boolean) {
        if (!istZweiterVersuch(index)) beiPaar(vokabel, gewusst)
    }

    fun paareFertig(fehler: Int) {
        if (bewertung != null) return
        abschliessen(
            when {
                fehler == 0 -> Bewertung.RICHTIG
                fehler <= 2 -> Bewertung.FAST_RICHTIG
                else -> Bewertung.FALSCH
            },
        )
    }

    fun weiter() {
        index++
        eingabe = TextFieldValue("")
        gewaehlt = null
        bewertung = null
        tippStufe = 0
        deHilfe = false
        gelegt.clear()
    }

    /** 1–3 Sterne je nach Anteil richtiger Antworten im ersten Versuch. */
    val sterne: Int
        get() {
            if (ergebnisse.isEmpty()) return 0
            val punkte = ergebnisse.sumOf {
                when (it.second) {
                    Bewertung.RICHTIG -> 2
                    Bewertung.FAST_RICHTIG -> 1
                    Bewertung.FALSCH -> 0
                }
            } / (2f * ergebnisse.size)
            return when {
                punkte >= 0.9f -> 3
                punkte >= 0.7f -> 2
                else -> 1
            }
        }

    private fun abschliessen(ergebnis: Bewertung) {
        val frage = aktuelle ?: return
        bewertung = ergebnis
        val erster = !istZweiterVersuch(index)
        beiErgebnis(frage, ergebnis, erster)
        if (erster) {
            ergebnisse += frage to ergebnis
            if (ergebnis == Bewertung.FALSCH && frage is Frage.Aufgabe) fragen += frage
        }
    }
}
