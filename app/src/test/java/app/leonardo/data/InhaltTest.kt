package app.leonardo.data

import app.leonardo.logic.FragenBauer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Prüft die echten Kapitel-Dateien aus ./content. */
class InhaltTest {

    private val inhalt: Inhalt = run {
        val ordner = listOf(File("../content"), File("content")).first { it.isDirectory }
        val texte = ordner.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { it.readText(Charsets.UTF_8) }
        ContentLoader.ausTexten(texte)
    }

    @Test
    fun alleKapitelUndVerbenSindDa() {
        assertEquals(listOf("lezione_1", "lezione_2", "lezione_3", "lezione_4", "verbi"), inhalt.kapitel.map { it.id })
        assertTrue(inhalt.verben.size >= 50)
        inhalt.verben.values.forEach {
            assertEquals(6, it.formen.size)
            assertEquals(6, it.formenDe.size)
        }
    }

    @Test
    fun jedeEinheitHatErklaerungUndBekannteVerben() {
        inhalt.einheiten.forEach { e ->
            assertTrue("${e.id} ohne Erklärung", e.erklaerung.isNotEmpty())
            e.verben.forEach { v -> assertTrue("${e.id}: unbekanntes Verb $v", v in inhalt.verben) }
        }
    }

    @Test
    fun jedeEinheitErgibtEineVolleUebungsrunde() {
        val bauer = FragenBauer(inhalt, Random(7))
        inhalt.einheiten.forEach { e ->
            val fragen = bauer.fuerEinheit(e, Lernstand(), 10, mitHoeren = true)
            assertTrue("${e.id}: nur ${fragen.size} Fragen", fragen.size >= 7)
            fragen.filterIsInstance<app.leonardo.logic.Frage.Aufgabe>()
                .filter { it.uebung.istMultipleChoice }
                .forEach { assertTrue("${it.uebung.id}: Lösung fehlt in Optionen", it.uebung.loesung in it.uebung.optionen) }
        }
    }

    @Test
    fun uebungsIdsSindEindeutig() {
        val ids = inhalt.einheiten.flatMap { e -> e.uebungen.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }
}
