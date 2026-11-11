package app.leonardo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LernstandTest {

    @Test
    fun speichernUndLadenErgibtDenselbenStand() {
        val stand = Lernstand()
            .mitVokabel("ciao", gewusst = true)
            .mitVokabel("ciao", gewusst = true)
            .mitVokabel("la forchetta", gewusst = false)
            .mitUebung("l1-01", richtig = true)
            .mitUebung("k:essere:2", richtig = false)
            .mitSternen("l1-e1", 2)
            .mitErklaerungGelesen("l1-e2")
            .mitAntwort(20_000)
            .copy(
                einstellungen = Einstellungen(kurzeRunden = false, grosseSchrift = true, autoVorlesen = false, tagesziel = 40),
                letzteEinheit = "l1-e2",
            )

        val geladen = LernstandJson.lesen(LernstandJson.schreiben(stand))

        assertEquals(stand, geladen)
        assertEquals(2, geladen.vokabeln["ciao"])
        assertEquals(0, geladen.vokabeln["la forchetta"])
        assertEquals(2, geladen.einheit("l1-e1").sterne)
        assertEquals(true, geladen.einheit("l1-e2").erklaerungGelesen)
    }

    @Test
    fun kaputteDateiFuehrtZuLeeremStand() {
        val leer = runCatching { LernstandJson.lesen("{}") }.getOrNull()
        assertEquals(Lernstand(), leer)
    }

    @Test
    fun sterneSinkenNie() {
        val stand = Lernstand().mitSternen("e", 3).mitSternen("e", 1)
        assertEquals(3, stand.einheit("e").sterne)
    }

    @Test
    fun serieZaehltAufeinanderfolgendeTage() {
        val stand = Lernstand().mitAntwort(100).mitAntwort(100).mitAntwort(101).mitAntwort(102)
        assertEquals(3, stand.serie(102))
        assertEquals(1, stand.heute(102))
        assertEquals(0, stand.serie(105))
    }

    @Test
    fun falscheAntwortSetztVokabelZurueck() {
        val stand = Lernstand().mitVokabel("ciao", true).mitVokabel("ciao", true).mitVokabel("ciao", false)
        assertEquals(0, stand.vokabeln["ciao"])
    }
}
