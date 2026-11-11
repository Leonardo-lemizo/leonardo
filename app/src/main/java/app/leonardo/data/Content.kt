package app.leonardo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Vokabel(
    val italienisch: String,
    val deutsch: String,
    val wortart: String?,
    val genus: String?,
)

data class Beispiel(val italienisch: String, val deutsch: String)

/** Eine Erklärkarte: kurze Punkte, Beispiele, Tabelle und ein Merksatz. */
data class Erklaerkarte(
    val titel: String,
    val punkte: List<String> = emptyList(),
    val beispiele: List<Beispiel> = emptyList(),
    val tabelle: List<List<String>> = emptyList(),
    val merke: String? = null,
)

data class Uebung(
    val id: String,
    val typ: String,
    val aufgabe: String,
    val loesung: String,
    /** Deutsche Hilfe zur Aufgabe (auf Wunsch einblendbar). */
    val aufgabeDe: String? = null,
    /** Deutsche Bedeutung der Lösung. */
    val loesungDe: String? = null,
    val alternativen: List<String> = emptyList(),
    val optionen: List<String> = emptyList(),
    /** Kurze Erklärung, warum die Lösung so ist. */
    val hinweis: String? = null,
    val loesungGeneriert: Boolean = false,
    val quelle: String? = null,
    /** Italienischer Text, der vorgelesen werden kann. */
    val vorlesen: String? = null,
) {
    val istMultipleChoice: Boolean get() = optionen.isNotEmpty() && (typ == "multiple_choice" || typ == "hoeren")

    val typName: String
        get() = when (typ) {
            "lueckentext" -> "Lückentext"
            "uebersetzung" -> "Übersetzung"
            "konjugation" -> "Verbform"
            "multiple_choice" -> "Auswahl"
            "hoeren" -> "Hören"
            else -> typ
        }

    /** Was nach dem Beantworten vorgelesen wird (nur italienischer Text). */
    val vorleseText: String?
        get() = vorlesen ?: if (!istMultipleChoice || loesungDe != null) loesung else null
}

data class Einheit(
    val id: String,
    val kapitelId: String,
    val titel: String,
    val emoji: String,
    val ziel: String,
    val erklaerung: List<Erklaerkarte>,
    val vokabeln: List<Vokabel>,
    val uebungen: List<Uebung>,
    val verben: List<String>,
    /** Wiederholungs-Einheit: übt zusätzlich alles aus dem ganzen Kapitel. */
    val mix: Boolean,
)

data class Kapitel(
    val id: String,
    val nummer: Int,
    val titel: String,
    val untertitel: String,
    val emoji: String,
    val einheiten: List<Einheit>,
)

data class Verb(
    val infinitiv: String,
    val deutsch: String,
    val gruppe: String,
    val formen: List<String>,
    val formenDe: List<String>,
    val loesungGeneriert: Boolean,
)

class Inhalt(val kapitel: List<Kapitel>, val verben: Map<String, Verb>) {
    val einheiten: List<Einheit> = kapitel.flatMap { it.einheiten }
    private val einheitNachId = einheiten.associateBy { it.id }
    private val kapitelNachId = kapitel.associateBy { it.id }

    fun einheit(id: String?): Einheit? = id?.let { einheitNachId[it] }
    fun kapitel(id: String?): Kapitel? = id?.let { kapitelNachId[it] }
    fun kapitelVon(einheit: Einheit): Kapitel? = kapitelNachId[einheit.kapitelId]
}

/** Liest die Kapitel-JSONs aus den Assets (Quelle: Ordner ./content im Projekt). */
object ContentLoader {

    fun laden(context: Context): Inhalt {
        val assets = context.assets
        val dateien = assets.list("").orEmpty().filter { it.endsWith(".json") }
        return ausTexten(dateien.map { datei -> assets.open(datei).bufferedReader(Charsets.UTF_8).use { it.readText() } })
    }

    /** Baut den Inhalt aus den Texten der JSON-Dateien (auch für Tests). */
    fun ausTexten(texte: List<String>): Inhalt {
        val jsons = texte.map { JSONObject(it) }
        val verben = jsons.flatMap { it.optJSONArray("verben").objekte() }
            .map(::verbAus)
            .associateBy { it.infinitiv }
        val kapitel = jsons.map(::kapitelAus).sortedBy { it.nummer }
        return Inhalt(kapitel, verben)
    }

    private fun kapitelAus(json: JSONObject): Kapitel {
        val id = json.getString("kapitel_id")
        return Kapitel(
            id = id,
            nummer = json.optInt("nummer", 99),
            titel = json.getString("titel"),
            untertitel = json.optString("untertitel"),
            emoji = json.optString("emoji", "📘"),
            einheiten = json.optJSONArray("einheiten").objekte().map { einheitAus(it, id) },
        )
    }

    private fun einheitAus(e: JSONObject, kapitelId: String) = Einheit(
        id = e.getString("id"),
        kapitelId = kapitelId,
        titel = e.getString("titel"),
        emoji = e.optString("emoji", "📘"),
        ziel = e.optString("ziel"),
        erklaerung = e.optJSONArray("erklaerung").objekte().map { k ->
            Erklaerkarte(
                titel = k.getString("titel"),
                punkte = k.optJSONArray("punkte").texte(),
                beispiele = k.optJSONArray("beispiele").objekte().map { Beispiel(it.getString("it"), it.getString("de")) },
                tabelle = k.optJSONArray("tabelle").let { t ->
                    if (t == null) emptyList() else List(t.length()) { t.getJSONArray(it).texte() }
                },
                merke = k.text("merke"),
            )
        },
        vokabeln = e.optJSONArray("vokabeln").objekte().map { v ->
            Vokabel(v.getString("it"), v.getString("de"), v.text("wortart"), v.text("genus"))
        },
        uebungen = e.optJSONArray("uebungen").objekte().map { u ->
            Uebung(
                id = u.getString("id"),
                typ = u.getString("typ"),
                aufgabe = u.getString("aufgabe"),
                loesung = u.getString("loesung"),
                aufgabeDe = u.text("aufgabe_de"),
                loesungDe = u.text("loesung_de"),
                alternativen = u.optJSONArray("alternativen").texte(),
                optionen = u.optJSONArray("optionen").texte(),
                hinweis = u.text("hinweis"),
                loesungGeneriert = u.optBoolean("loesung_generiert", false),
                quelle = u.text("quelle"),
            )
        },
        verben = e.optJSONArray("verben").texte(),
        mix = e.optBoolean("mix", false),
    )

    private fun verbAus(v: JSONObject) = Verb(
        infinitiv = v.getString("inf"),
        deutsch = v.getString("de"),
        gruppe = v.optString("gruppe"),
        formen = v.getJSONArray("formen").texte(),
        formenDe = v.getJSONArray("formen_de").texte(),
        loesungGeneriert = v.optBoolean("loesung_generiert", false),
    )

    private fun JSONArray?.objekte(): List<JSONObject> =
        if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    private fun JSONArray?.texte(): List<String> =
        if (this == null) emptyList() else List(length()) { getString(it) }

    private fun JSONObject.text(name: String): String? =
        if (has(name) && !isNull(name)) getString(name).ifBlank { null } else null
}
