package app.leonardo.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerCheckerTest {

    private fun pruefe(eingabe: String, vararg loesungen: String) =
        AnswerChecker.pruefe(eingabe, loesungen.toList())

    @Test
    fun exakteAntwortIstRichtig() {
        assertEquals(Bewertung.RICHTIG, pruefe("sono", "sono"))
    }

    @Test
    fun grossKleinschreibungUndLeerzeichenSindEgal() {
        assertEquals(Bewertung.RICHTIG, pruefe("  Di   SOLITO ", "Di solito"))
    }

    @Test
    fun satzzeichenSindEgal() {
        assertEquals(Bewertung.RICHTIG, pruefe("il conto per cortesia", "Il conto, per cortesia."))
        assertEquals(Bewertung.RICHTIG, pruefe("Come sta", "Come sta?"))
    }

    @Test
    fun apostropheWerdenVereinheitlicht() {
        assertEquals(Bewertung.RICHTIG, pruefe("l’operaio", "l'operaio"))
        assertEquals(Bewertung.RICHTIG, pruefe("l' operaio", "l'operaio"))
        assertEquals(Bewertung.RICHTIG, pruefe("un'", "un'"))
    }

    @Test
    fun fehlendeAkzenteSindFastRichtig() {
        assertEquals(Bewertung.FAST_RICHTIG, pruefe("perche", "perché"))
        assertEquals(Bewertung.FAST_RICHTIG, pruefe("Lei e di qui?", "Lei è di qui?"))
        assertEquals(Bewertung.FAST_RICHTIG, pruefe("é", "è"))
    }

    @Test
    fun buchstabendreherUndDoppelbuchstabenSindFastRichtig() {
        assertEquals(Bewertung.FAST_RICHTIG, pruefe("paralre", "parlare"))
        assertEquals(Bewertung.FAST_RICHTIG, pruefe("abiamo", "abbiamo"))
        assertEquals(Bewertung.FAST_RICHTIG, pruefe("formagio", "formaggio"))
    }

    @Test
    fun andereVerbformBleibtFalsch() {
        assertEquals(Bewertung.FALSCH, pruefe("parla", "parlo"))
        assertEquals(Bewertung.FALSCH, pruefe("lavori", "lavora"))
        assertEquals(Bewertung.FALSCH, pruefe("ha", "ho"))
    }

    @Test
    fun alternativenWerdenAkzeptiert() {
        assertEquals(Bewertung.RICHTIG, pruefe("Sono le due e trenta", "Sono le due e mezza.", "Sono le due e trenta."))
    }

    @Test
    fun falscheOderLeereAntwortIstFalsch() {
        assertEquals(Bewertung.FALSCH, pruefe("sei", "sono"))
        assertEquals(Bewertung.FALSCH, pruefe("   ", "sono"))
        assertEquals(Bewertung.FALSCH, pruefe("un", "un'"))
    }
}
