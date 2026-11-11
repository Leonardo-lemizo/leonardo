package app.leonardo.logic

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Liest italienische Wörter mit der Sprachausgabe des Geräts vor (offline).
 * Ist keine italienische Stimme installiert, bleibt [verfuegbar] false und
 * die App blendet die Vorlese-Knöpfe aus.
 */
class Vorleser(context: Context) {

    var verfuegbar by mutableStateOf(false); private set

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val sprache = tts?.setLanguage(Locale.ITALIAN)
                verfuegbar = sprache != null && sprache >= TextToSpeech.LANG_AVAILABLE
                tts?.setSpeechRate(0.9f)
            }
        }
    }

    fun sprich(text: String) {
        if (!verfuegbar) return
        tts?.speak(bereinige(text), TextToSpeech.QUEUE_FLUSH, null, "leonardo")
    }

    fun stopp() {
        tts?.stop()
    }

    fun beenden() {
        tts?.shutdown()
        tts = null
    }

    /** Klammern, Schrägstriche und Lücken stören beim Vorlesen. */
    private fun bereinige(text: String): String = text
        .replace(Regex("\\(.*?\\)"), " ")
        .replace("/", ", ")
        .replace("…", " ")
        .replace(Regex("_+"), " ")
        .trim()
}
