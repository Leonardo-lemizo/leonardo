package app.leonardo.logic

import app.leonardo.data.Beispiel
import app.leonardo.data.Einheit
import app.leonardo.data.Erklaerkarte
import app.leonardo.data.Inhalt
import app.leonardo.data.Kapitel
import app.leonardo.data.Lernstand
import app.leonardo.data.Uebung
import app.leonardo.data.Verb
import app.leonardo.data.Vokabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FragenBauerTest {

    private val verb = Verb(
        "essere", "sein", "irr",
        listOf("sono", "sei", "è", "siamo", "siete", "sono"),
        listOf("ich bin", "du bist", "er/sie ist", "wir sind", "ihr seid", "sie sind"),
        false,
    )

    private val einheit = Einheit(
        id = "t-e1",
        kapitelId = "t",
        titel = "Test",
        emoji = "🧪",
        ziel = "",
        erklaerung = listOf(
            Erklaerkarte("Karte", beispiele = listOf(Beispiel("Ciao!", "Hallo!"), Beispiel("Buongiorno!", "Guten Tag!"))),
        ),
        vokabeln = listOf(
            Vokabel("ciao", "hallo", null, null),
            Vokabel("la forchetta", "die Gabel", "Substantiv", "f"),
            Vokabel("il coltello", "das Messer", "Substantiv", "m"),
            Vokabel("il sale", "das Salz", "Substantiv", "m"),
            Vokabel("il pepe", "der Pfeffer", "Substantiv", "m"),
            Vokabel("il pane", "das Brot", "Substantiv", "m"),
        ),
        uebungen = (1..8).map { i ->
            Uebung(id = "u$i", typ = "lueckentext", aufgabe = "Aufgabe $i", loesung = "lösung $i")
        },
        verben = listOf("essere"),
        mix = false,
    )

    private val inhalt = Inhalt(
        listOf(Kapitel("t", 1, "Test", "", "🧪", listOf(einheit))),
        mapOf("essere" to verb),
    )

    @Test
    fun rundeHatDieGewuenschteGroesseUndMischtArten() {
        val fragen = FragenBauer(inhalt, Random(1)).fuerEinheit(einheit, Lernstand(), 10, mitHoeren = true)
        val aufgaben = fragen.filterIsInstance<Frage.Aufgabe>()
        val anzahlMitPaaren = aufgaben.size + fragen.filterIsInstance<Frage.Paare>().sumOf { it.vokabeln.size }

        assertTrue("zu wenige Fragen: ${fragen.size}", anzahlMitPaaren >= 10)
        assertTrue(aufgaben.any { it.fortschritt is Fortschritt.Aufgabe })
        assertTrue(aufgaben.any { it.fortschritt is Fortschritt.Wort } || fragen.any { it is Frage.Paare })
        assertTrue(aufgaben.any { it.fortschritt is Fortschritt.Verbform })
    }

    @Test
    fun falscheAufgabenKommenZuerstDran() {
        val stand = Lernstand(uebungen = mapOf("u8" to 0, "u1" to 3, "u2" to 3, "u3" to 3))
        val fragen = FragenBauer(inhalt, Random(2)).fuerEinheit(einheit, stand, 10, mitHoeren = false)
        val ids = fragen.filterIsInstance<Frage.Aufgabe>().mapNotNull { (it.fortschritt as? Fortschritt.Aufgabe)?.id }
        assertTrue("u8 fehlt: $ids", "u8" in ids)
    }

    @Test
    fun auswahlFragenEnthaltenImmerDieLoesung() {
        repeat(20) { seed ->
            FragenBauer(inhalt, Random(seed)).fuerEinheit(einheit, Lernstand(), 12, mitHoeren = true)
                .filterIsInstance<Frage.Aufgabe>()
                .filter { it.uebung.istMultipleChoice }
                .forEach { assertTrue(it.uebung.loesung in it.uebung.optionen) }
        }
    }

    @Test
    fun verbfrageHatDeutscheHilfe() {
        val fragen = FragenBauer(inhalt, Random(3)).fuerEinheit(einheit, Lernstand(), 10, mitHoeren = false)
        val verbfrage = fragen.filterIsInstance<Frage.Aufgabe>().first { it.fortschritt is Fortschritt.Verbform }
        assertTrue(verbfrage.uebung.aufgabeDe!!.startsWith("sein – "))
        assertTrue(verbfrage.uebung.loesungDe != null)
    }

    @Test
    fun kurzCheckNutztBeispiele() {
        val check = FragenBauer(inhalt, Random(4)).kurzCheck(einheit.erklaerung[0], einheit)!!
        assertTrue(check.uebung.loesung in listOf("Hallo!", "Guten Tag!"))
        assertTrue(check.uebung.loesung in check.uebung.optionen)
    }

    @Test
    fun variantenOhneArtikelWerdenAkzeptiert() {
        val varianten = FragenBauer.varianten("la forchetta")
        assertTrue("forchetta" in varianten)
        assertEquals(Bewertung.RICHTIG, AnswerChecker.pruefe("forchetta", listOf("la forchetta") + varianten))
    }

    @Test
    fun tippsZeigenErstDeutschDannAnfangsbuchstaben() {
        val u = Uebung(id = "x", typ = "lueckentext", aufgabe = "Io ___ Sara.", loesung = "mi chiamo", loesungDe = "heiße")
        assertEquals("Auf Deutsch: heiße", Tipps.erster(u))
        assertEquals("Beginnt mit: m…   c…", Tipps.zweiter(u))
    }
}
