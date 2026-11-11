package app.leonardo.logic

import app.leonardo.data.Einheit
import app.leonardo.data.Erklaerkarte
import app.leonardo.data.Inhalt
import app.leonardo.data.Lernstand
import app.leonardo.data.Uebung
import app.leonardo.data.Verb
import app.leonardo.data.Vokabel
import kotlin.math.roundToInt
import kotlin.random.Random

/** Wohin das Ergebnis einer Frage gespeichert wird. */
sealed interface Fortschritt {
    data class Aufgabe(val id: String) : Fortschritt
    data class Wort(val italienisch: String) : Fortschritt
    data class Verbform(val schluessel: String) : Fortschritt
    data object Keiner : Fortschritt
}

sealed interface Frage {
    /** Eine normale Aufgabe. satzbau = Lösung aus Wort-Bausteinen zusammensetzen. */
    data class Aufgabe(
        val uebung: Uebung,
        val fortschritt: Fortschritt,
        val satzbau: Boolean = false,
    ) : Frage

    /** Paare finden: Italienisch und Deutsch einander zuordnen. */
    data class Paare(val vokabeln: List<Vokabel>) : Frage
}

private val PERSONEN = listOf("io", "tu", "lui / lei / Lei", "noi", "voi", "loro")
private val PERSONEN_DE = listOf("ich", "du", "er / sie", "wir", "ihr", "sie")

/** Tipps: zuerst die deutsche Bedeutung, dann die Anfangsbuchstaben. */
object Tipps {
    fun erster(u: Uebung): String? {
        val de = u.loesungDe?.takeIf { !u.aufgabe.contains(it, ignoreCase = true) }
        return when {
            de != null -> "Auf Deutsch: $de"
            u.hinweis != null -> u.hinweis
            else -> null
        }
    }

    fun zweiter(u: Uebung): String =
        "Beginnt mit: " + u.loesung.split(" ").filter { it.isNotBlank() }
            .joinToString("   ") { wort -> wort.first() + "…" }
}

/**
 * Baut abwechslungsreiche Übungsrunden: Aufgaben aus den Kursunterlagen,
 * automatisch erzeugte Wortfragen (Auswahl, Hören, Schreiben, Paare)
 * und Verbformen. Schwache und neue Inhalte kommen bevorzugt dran.
 */
class FragenBauer(private val inhalt: Inhalt, private val zufall: Random = Random.Default) {

    fun fuerEinheit(einheit: Einheit, stand: Lernstand, anzahl: Int, mitHoeren: Boolean): List<Frage> {
        val kapitel = inhalt.kapitelVon(einheit)
        val pool = if (einheit.mix && kapitel != null) kapitel.einheiten else listOf(einheit)
        val uebungen = (einheit.uebungen + pool.flatMap { it.uebungen }).distinctBy { it.id }
        val vokabeln = pool.flatMap { it.vokabeln }.distinctBy { it.italienisch }
        val verben = pool.flatMap { it.verben }.distinct().mapNotNull { inhalt.verben[it] }
        return mischen(uebungen, vokabeln, verben, stand, anzahl, mitHoeren, nurSchwache = false)
    }

    /** Wiederholung über alles, was schon einmal geübt wurde. */
    fun wiederholung(stand: Lernstand, anzahl: Int, mitHoeren: Boolean): List<Frage> {
        val begonnen = inhalt.einheiten.filter { e ->
            stand.einheit(e.id).let { it.sterne > 0 || it.erklaerungGelesen } ||
                e.uebungen.any { stand.uebungen.containsKey(it.id) }
        }
        val uebungen = begonnen.flatMap { it.uebungen }.filter { stand.uebungen.containsKey(it.id) }.distinctBy { it.id }
        val vokabeln = begonnen.flatMap { it.vokabeln }.filter { stand.vokabeln.containsKey(it.italienisch) }
            .distinctBy { it.italienisch }
        val verben = begonnen.flatMap { it.verben }.distinct().mapNotNull { inhalt.verben[it] }
        return mischen(uebungen, vokabeln, verben, stand, anzahl, mitHoeren, nurSchwache = true)
    }

    /** Kurz-Check direkt nach einer Erklärkarte (wird nicht gespeichert). */
    fun kurzCheck(karte: Erklaerkarte, einheit: Einheit): Frage.Aufgabe? {
        val beispiel = karte.beispiele.randomOrNull(zufall) ?: return null
        val andere = (einheit.erklaerung.flatMap { it.beispiele }.map { it.deutsch } +
            einheit.vokabeln.map { it.deutsch })
            .filter { it != beispiel.deutsch }
            .distinct()
            .shuffled(zufall)
            .take(3)
        if (andere.size < 2) return null
        val uebung = Uebung(
            id = "check:${einheit.id}:${beispiel.italienisch}",
            typ = "multiple_choice",
            aufgabe = "Kurz-Check: Was passt zu „${beispiel.italienisch}“?",
            loesung = beispiel.deutsch,
            optionen = andere + beispiel.deutsch,
            vorlesen = beispiel.italienisch,
        )
        return Frage.Aufgabe(uebung, Fortschritt.Keiner)
    }

    private fun mischen(
        uebungen: List<Uebung>,
        vokabeln: List<Vokabel>,
        verben: List<Verb>,
        stand: Lernstand,
        anzahl: Int,
        mitHoeren: Boolean,
        nurSchwache: Boolean,
    ): List<Frage> {
        // Reihenfolge nach Dringlichkeit: zuletzt falsch → neu → unsicher → sicher.
        fun prio(wert: Int?): Double = when (wert) {
            0 -> 0.0
            null -> 1.0
            else -> 1.0 + wert
        } + zufall.nextDouble() * 0.9

        // Wichtig: Der Zufallsanteil wird pro Eintrag nur einmal gezogen, sonst ist die Sortierung widersprüchlich.
        fun <T> List<T>.nachPrio(wert: (T) -> Int?): List<T> =
            map { it to prio(wert(it)) }.sortedBy { it.second }.map { it.first }

        val uebKandidaten = uebungen.nachPrio { stand.uebungen[it.id] }
            .let { if (nurSchwache) it.filter { u -> (stand.uebungen[u.id] ?: 0) < 3 } else it }
        val vokKandidaten = vokabeln.nachPrio { stand.vokabeln[it.italienisch] }
            .let { if (nurSchwache) it.filter { v -> (stand.vokabeln[v.italienisch] ?: 0) < 3 } else it }
        val verbKandidaten = verben.flatMap { v -> (0..5).map { p -> v to p } }
            .nachPrio { (v, p) -> stand.uebungen[verbSchluessel(v, p)] }
            .let { if (nurSchwache) it.filter { (v, p) -> (stand.uebungen[verbSchluessel(v, p)] ?: 0) < 2 } else it }

        // Anteile: Kursaufgaben 50 %, Wörter 30 %, Verben 20 % – je nachdem, was es gibt.
        val gewichte = listOf(
            if (uebKandidaten.isNotEmpty()) 5.0 else 0.0,
            if (vokKandidaten.isNotEmpty()) 3.0 else 0.0,
            if (verbKandidaten.isNotEmpty()) 2.0 else 0.0,
        )
        val summe = gewichte.sum()
        if (summe == 0.0) return emptyList()
        val verfuegbar = listOf(uebKandidaten.size, vokKandidaten.size, verbKandidaten.size)
        val soll = gewichte.mapIndexed { i, g -> minOf((anzahl * g / summe).roundToInt(), verfuegbar[i]) }.toMutableList()
        // Rest auffüllen, falls eine Gruppe zu klein war.
        var rest = anzahl - soll.sum()
        while (rest > 0 && (0..2).any { soll[it] < verfuegbar[it] }) {
            for (i in 0..2) if (rest > 0 && soll[i] < verfuegbar[i]) { soll[i]++; rest-- }
        }

        val fragen = mutableListOf<Frage>()
        uebKandidaten.take(soll[0]).forEach { u ->
            val satzbauMoeglich = u.typ == "uebersetzung" && u.loesung.split(" ").size in 3..8 &&
                !u.aufgabe.startsWith("Trenne")
            val satzbau = satzbauMoeglich && (stand.uebungen[u.id] ?: 0) < 2 && zufall.nextBoolean()
            fragen += Frage.Aufgabe(u, Fortschritt.Aufgabe(u.id), satzbau)
        }

        val alleWoerter = vokabeln.ifEmpty { inhalt.einheiten.flatMap { it.vokabeln } }
        val gewaehlteWoerter = vokKandidaten.take(soll[1])
        var paareGenutzt = false
        val paarWoerter = mutableListOf<Vokabel>()
        if (gewaehlteWoerter.size >= 5 && anzahl >= 6) {
            // Vier Wörter werden zu einem "Paare finden"-Spiel zusammengefasst.
            paarWoerter += gewaehlteWoerter.take(4)
            paareGenutzt = true
        }
        gewaehlteWoerter.drop(if (paareGenutzt) 4 else 0).forEach { v ->
            fragen += wortFrage(v, stand.vokabeln[v.italienisch], alleWoerter, mitHoeren)
        }
        verbKandidaten.take(soll[2]).forEach { (v, p) -> fragen += verbFrage(v, p) }

        fragen.shuffle(zufall)
        // Leichter Einstieg: eine Auswahlfrage zuerst.
        val leicht = fragen.indexOfFirst { it is Frage.Aufgabe && it.uebung.istMultipleChoice }
        if (leicht > 0) fragen.add(0, fragen.removeAt(leicht))
        if (paareGenutzt) fragen.add(minOf(2, fragen.size), Frage.Paare(paarWoerter))
        return fragen
    }

    private fun wortFrage(v: Vokabel, fach: Int?, alle: List<Vokabel>, mitHoeren: Boolean): Frage.Aufgabe {
        val fortschritt = Fortschritt.Wort(v.italienisch)
        val andere = alle.filter { it.deutsch != v.deutsch && it.italienisch != v.italienisch }
            .shuffled(zufall).take(3).ifEmpty {
                inhalt.einheiten.flatMap { it.vokabeln }.filter { it.deutsch != v.deutsch }.shuffled(zufall).take(3)
            }
        val stufe = fach ?: -1
        val schreibbar = v.italienisch.length <= 30 && !v.italienisch.contains("…") &&
            v.italienisch.split(" ").size <= 4

        val uebung = when {
            stufe >= 3 && schreibbar -> Uebung(
                id = "w:${v.italienisch}",
                typ = "uebersetzung",
                aufgabe = "Schreib auf Italienisch: „${v.deutsch}“",
                loesung = v.italienisch,
                alternativen = varianten(v.italienisch),
                hinweis = listOfNotNull(v.wortart, genusText(v.genus)).joinToString(" · ").ifEmpty { null },
                vorlesen = v.italienisch,
            )
            stufe >= 1 -> Uebung(
                id = "w:${v.italienisch}",
                typ = "multiple_choice",
                aufgabe = "Wie sagt man „${v.deutsch}“ auf Italienisch?",
                loesung = v.italienisch,
                loesungDe = v.deutsch,
                optionen = andere.map { it.italienisch } + v.italienisch,
                vorlesen = v.italienisch,
            )
            mitHoeren && zufall.nextInt(3) == 0 -> Uebung(
                id = "w:${v.italienisch}",
                typ = "hoeren",
                aufgabe = "Hör genau hin: Was bedeutet das?",
                loesung = v.deutsch,
                optionen = andere.map { it.deutsch } + v.deutsch,
                vorlesen = v.italienisch,
            )
            else -> Uebung(
                id = "w:${v.italienisch}",
                typ = "multiple_choice",
                aufgabe = "Was heißt „${v.italienisch}“?",
                loesung = v.deutsch,
                optionen = andere.map { it.deutsch } + v.deutsch,
                vorlesen = v.italienisch,
            )
        }
        return Frage.Aufgabe(uebung, fortschritt)
    }

    private fun verbFrage(v: Verb, person: Int): Frage.Aufgabe {
        val deutsch = v.deutsch.substringBefore(";").substringBefore(",").trim()
        val uebung = Uebung(
            id = verbSchluessel(v, person),
            typ = "konjugation",
            aufgabe = "${v.infinitiv} – ${PERSONEN[person]}",
            aufgabeDe = "$deutsch – ${PERSONEN_DE[person]}",
            loesung = v.formen[person],
            loesungDe = v.formenDe[person],
            loesungGeneriert = v.loesungGeneriert,
            quelle = "Verben-Trainer",
            vorlesen = v.formen[person],
        )
        return Frage.Aufgabe(uebung, Fortschritt.Verbform(uebung.id))
    }

    companion object {
        fun verbSchluessel(v: Verb, person: Int) = "k:${v.infinitiv}:$person"

        fun genusText(genus: String?): String? = when {
            genus == null -> null
            genus.startsWith("m/f") -> "männlich / weiblich"
            genus.startsWith("m") -> "männlich" + if ("Plural" in genus) ", Mehrzahl" else ""
            genus.startsWith("f") -> "weiblich" + if ("Plural" in genus) ", Mehrzahl" else ""
            else -> genus
        }

        /** Akzeptierte Schreibweisen: ohne Artikel, ohne Klammer, einzelne Teile bei „a / b“. */
        fun varianten(italienisch: String): List<String> {
            val teile = italienisch.split(" / ").map { it.trim() }
            val basis = (teile + italienisch).flatMap { t ->
                val ohneKlammer = t.replace(Regex("\\s*\\(.*?\\)"), "").trim()
                val mitKlammer = t.replace("(", "").replace(")", "").trim()
                listOf(t, ohneKlammer, mitKlammer)
            }
            val ohneArtikel = basis.map { it.replace(Regex("^(il|lo|la|i|gli|le)\\s+|^l'"), "").trim() }
            return (basis + ohneArtikel).filter { it.isNotBlank() && it != italienisch }.distinct()
        }
    }
}
