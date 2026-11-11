package app.leonardo.logic

import java.text.Normalizer

enum class Bewertung { RICHTIG, FAST_RICHTIG, FALSCH }

/**
 * Toleranter Vergleich für Freitext-Antworten:
 * - Groß-/Kleinschreibung, mehrfache Leerzeichen und Satzzeichen sind egal.
 * - "Fast richtig" sind Antworten, bei denen nur Akzente fehlen, zwei
 *   Nachbarbuchstaben vertauscht sind oder ein Doppelbuchstabe nicht stimmt.
 *   Andere Abweichungen zählen bewusst als falsch, weil im Italienischen oft
 *   ein einzelner Buchstabe die Verbform ändert (parlo / parla).
 */
object AnswerChecker {

    fun pruefe(eingabe: String, loesungen: List<String>): Bewertung {
        val antwort = normalisiere(eingabe)
        if (antwort.isEmpty()) return Bewertung.FALSCH

        val ziele = loesungen.map(::normalisiere)
        if (antwort in ziele) return Bewertung.RICHTIG

        val antwortOhne = ohneAkzente(antwort)
        val fast = ziele.map(::ohneAkzente).any { ziel ->
            antwortOhne == ziel || istBuchstabendreher(antwortOhne, ziel) || nurDoppelbuchstabeAnders(antwortOhne, ziel)
        }
        return if (fast) Bewertung.FAST_RICHTIG else Bewertung.FALSCH
    }

    fun normalisiere(text: String): String = text
        .lowercase()
        .replace(Regex("[’‘`´]"), "'")
        .replace(Regex("\\s*'\\s*"), "'")
        .replace(Regex("[.,!?;:¿¡…\"“”„«»()–-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun ohneAkzente(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** "paralre" statt "parlare": genau zwei benachbarte Buchstaben vertauscht. */
    private fun istBuchstabendreher(a: String, b: String): Boolean {
        if (a.length != b.length || a.length < 4) return false
        val i = a.indices.firstOrNull { a[it] != b[it] } ?: return false
        return i + 1 < a.length &&
            a[i] == b[i + 1] && a[i + 1] == b[i] &&
            a.substring(i + 2) == b.substring(i + 2)
    }

    /** "abiamo" statt "abbiamo": Unterschied nur bei verdoppelten Buchstaben. */
    private fun nurDoppelbuchstabeAnders(a: String, b: String): Boolean {
        if (minOf(a.length, b.length) < 4) return false
        val doppelt = Regex("(.)\\1+")
        return doppelt.replace(a, "$1") == doppelt.replace(b, "$1")
    }
}
