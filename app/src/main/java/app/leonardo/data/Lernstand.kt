package app.leonardo.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Maximales Fach einer Vokabel (Leitner-System, 0 = neu/nicht gewusst). */
const val MAX_FACH = 5

/** Ab diesem Stand gilt eine Übung als "sicher". */
const val UEBUNG_SICHER = 2

data class Einstellungen(
    val kurzeRunden: Boolean = true,
    val grosseSchrift: Boolean = false,
    val autoVorlesen: Boolean = true,
    val tagesziel: Int = 20,
)

data class EinheitStand(
    val sterne: Int = 0,
    val erklaerungGelesen: Boolean = false,
)

/**
 * Der komplette Lernstand. Er wird nach jeder Antwort als eine kleine
 * JSON-Datei im internen App-Speicher gesichert.
 */
data class Lernstand(
    /** Fach (0–5) pro Vokabel, Schlüssel = italienisches Wort. */
    val vokabeln: Map<String, Int> = emptyMap(),
    /** Stand (0–3) pro Übung bzw. Verbform: 0 = zuletzt falsch, ab 1 richtig. */
    val uebungen: Map<String, Int> = emptyMap(),
    val einheiten: Map<String, EinheitStand> = emptyMap(),
    val einstellungen: Einstellungen = Einstellungen(),
    val letzterTag: Long = 0,
    val antwortenAmLetztenTag: Int = 0,
    val serieBisLetztenTag: Int = 0,
    val letzteEinheit: String? = null,
) {
    fun heute(tag: Long): Int = if (letzterTag == tag) antwortenAmLetztenTag else 0

    /** Tage in Folge. Bleibt bis zum Ende des Folgetags erhalten. */
    fun serie(tag: Long): Int = if (letzterTag >= tag - 1) serieBisLetztenTag else 0

    fun einheit(id: String): EinheitStand = einheiten[id] ?: EinheitStand()

    fun mitAntwort(tag: Long): Lernstand = when {
        letzterTag == tag -> copy(antwortenAmLetztenTag = antwortenAmLetztenTag + 1)
        letzterTag == tag - 1 -> copy(letzterTag = tag, antwortenAmLetztenTag = 1, serieBisLetztenTag = serieBisLetztenTag + 1)
        else -> copy(letzterTag = tag, antwortenAmLetztenTag = 1, serieBisLetztenTag = 1)
    }

    fun mitVokabel(wort: String, gewusst: Boolean): Lernstand {
        val bisher = vokabeln[wort] ?: 0
        val neu = if (gewusst) minOf(bisher + 1, MAX_FACH) else 0
        return copy(vokabeln = vokabeln + (wort to neu))
    }

    fun mitUebung(schluessel: String, richtig: Boolean): Lernstand {
        val bisher = uebungen[schluessel] ?: 0
        val neu = if (richtig) minOf(bisher + 1, 3) else 0
        return copy(uebungen = uebungen + (schluessel to neu))
    }

    fun mitSternen(einheitId: String, sterne: Int): Lernstand {
        val alt = einheit(einheitId)
        return copy(einheiten = einheiten + (einheitId to alt.copy(sterne = maxOf(alt.sterne, sterne))))
    }

    fun mitErklaerungGelesen(einheitId: String): Lernstand =
        copy(einheiten = einheiten + (einheitId to einheit(einheitId).copy(erklaerungGelesen = true)))
}

/** Wandelt den Lernstand in JSON um und zurück. */
object LernstandJson {

    fun schreiben(s: Lernstand): String = JSONObject().apply {
        put("version", 1)
        put("vokabeln", JSONObject(s.vokabeln))
        put("uebungen", JSONObject(s.uebungen))
        put("einheiten", JSONObject().apply {
            s.einheiten.forEach { (id, e) ->
                put(id, JSONObject().put("sterne", e.sterne).put("erklaerung", e.erklaerungGelesen))
            }
        })
        put("einstellungen", JSONObject().apply {
            put("kurzeRunden", s.einstellungen.kurzeRunden)
            put("grosseSchrift", s.einstellungen.grosseSchrift)
            put("autoVorlesen", s.einstellungen.autoVorlesen)
            put("tagesziel", s.einstellungen.tagesziel)
        })
        put("letzterTag", s.letzterTag)
        put("antwortenAmLetztenTag", s.antwortenAmLetztenTag)
        put("serie", s.serieBisLetztenTag)
        s.letzteEinheit?.let { put("letzteEinheit", it) }
    }.toString()

    fun lesen(text: String): Lernstand {
        val j = JSONObject(text)
        val e = j.optJSONObject("einstellungen") ?: JSONObject()
        val standard = Einstellungen()
        return Lernstand(
            vokabeln = j.optJSONObject("vokabeln").zahlen(),
            uebungen = j.optJSONObject("uebungen").zahlen(),
            einheiten = j.optJSONObject("einheiten")?.let { obj ->
                obj.keys().asSequence().associateWith { id ->
                    val x = obj.getJSONObject(id)
                    EinheitStand(x.optInt("sterne", 0), x.optBoolean("erklaerung", false))
                }
            } ?: emptyMap(),
            einstellungen = Einstellungen(
                kurzeRunden = e.optBoolean("kurzeRunden", standard.kurzeRunden),
                grosseSchrift = e.optBoolean("grosseSchrift", standard.grosseSchrift),
                autoVorlesen = e.optBoolean("autoVorlesen", standard.autoVorlesen),
                tagesziel = e.optInt("tagesziel", standard.tagesziel),
            ),
            letzterTag = j.optLong("letzterTag", 0),
            antwortenAmLetztenTag = j.optInt("antwortenAmLetztenTag", 0),
            serieBisLetztenTag = j.optInt("serie", 0),
            letzteEinheit = if (j.has("letzteEinheit")) j.getString("letzteEinheit") else null,
        )
    }

    private fun JSONObject?.zahlen(): Map<String, Int> =
        if (this == null) emptyMap() else keys().asSequence().associateWith { optInt(it, 0) }
}

/**
 * Speichert den Lernstand sicher: AtomicFile schreibt erst in eine
 * Zwischendatei und ersetzt die alte Datei erst, wenn alles geschrieben ist.
 */
class LernstandSpeicher(context: Context) {

    private val datei = AtomicFile(File(context.filesDir, "lernstand.json"))

    fun laden(): Lernstand = try {
        LernstandJson.lesen(String(datei.readFully(), Charsets.UTF_8))
    } catch (e: Exception) {
        Lernstand()
    }

    @Synchronized
    fun speichern(stand: Lernstand) {
        val ausgabe = try { datei.startWrite() } catch (e: Exception) { return }
        try {
            ausgabe.write(LernstandJson.schreiben(stand).toByteArray(Charsets.UTF_8))
            datei.finishWrite(ausgabe)
        } catch (e: Exception) {
            datei.failWrite(ausgabe)
        }
    }

    fun loeschen() {
        datei.delete()
    }
}
