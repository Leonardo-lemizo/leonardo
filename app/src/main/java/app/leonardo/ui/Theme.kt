package app.leonardo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.leonardo.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Ruhige Palette: Salbeigrün als Hauptfarbe, Terrakotta als Akzent.
// Der Hintergrund ist bewusst cremefarben statt reinweiß – das blendet weniger.
private val HellesSchema = lightColorScheme(
    primary = Color(0xFF2F6B52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E8D6),
    onPrimaryContainer = Color(0xFF0B2A1D),
    secondary = Color(0xFFA4523A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6DCD2),
    onSecondaryContainer = Color(0xFF3B140A),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFAF7F2),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE7E2DA),
    onSurfaceVariant = Color(0xFF4E4A44),
    surfaceContainerLow = Color(0xFFF4F0EA),
    surfaceContainer = Color(0xFFEFEBE4),
    surfaceContainerHigh = Color(0xFFE9E4DC),
    outline = Color(0xFF7E786F),
)

private val DunklesSchema = darkColorScheme(
    primary = Color(0xFF93D3B2),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF18513D),
    onPrimaryContainer = Color(0xFFC8E8D6),
    secondary = Color(0xFFF2B39F),
    onSecondary = Color(0xFF561F10),
    secondaryContainer = Color(0xFF7A3826),
    onSecondaryContainer = Color(0xFFF6DCD2),
    background = Color(0xFF131513),
    onBackground = Color(0xFFE5E2DD),
    surface = Color(0xFF131513),
    onSurface = Color(0xFFE5E2DD),
    surfaceVariant = Color(0xFF3F3B36),
    onSurfaceVariant = Color(0xFFCBC5BC),
    surfaceContainerLow = Color(0xFF1A1C1A),
    surfaceContainer = Color(0xFF1F211F),
    surfaceContainerHigh = Color(0xFF292B29),
    outline = Color(0xFF959087),
)

/** Farben für die Rückmeldung im Quiz (richtig / fast richtig / falsch). */
data class Rueckmeldefarben(
    val richtig: Color, val aufRichtig: Color,
    val fast: Color, val aufFast: Color,
    val falsch: Color, val aufFalsch: Color,
)

private val HelleRueckmeldung = Rueckmeldefarben(
    richtig = Color(0xFFD3EDDC), aufRichtig = Color(0xFF0F3B25),
    fast = Color(0xFFFBE7B5), aufFast = Color(0xFF4A3500),
    falsch = Color(0xFFF9DAD6), aufFalsch = Color(0xFF5C1410),
)

private val DunkleRueckmeldung = Rueckmeldefarben(
    richtig = Color(0xFF1D4630), aufRichtig = Color(0xFFC9EBD4),
    fast = Color(0xFF4D3B0A), aufFast = Color(0xFFF7E0A6),
    falsch = Color(0xFF5A221C), aufFalsch = Color(0xFFF7D3CE),
)

val LocalRueckmeldefarben = staticCompositionLocalOf { HelleRueckmeldung }

/**
 * Lesefreundliche Schrift: etwas größer, mehr Zeilenabstand und leicht
 * gesperrte Buchstaben. Keine Kursivschrift, keine Versalien.
 */
private val Leseschrift = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

private fun TextStyle.lesbar(groesse: Int, zeilenhoehe: Int, gewicht: FontWeight = FontWeight.Normal) = copy(
    // Atkinson Hyperlegible: Buchstaben wie b/d, I/l/1 sind besonders gut unterscheidbar.
    fontFamily = Leseschrift,
    fontSize = groesse.sp,
    lineHeight = zeilenhoehe.sp,
    letterSpacing = 0.03.em,
    fontWeight = gewicht,
)

private val Basis = Typography()

private val LeonardoTypografie = Typography(
    headlineMedium = Basis.headlineMedium.lesbar(30, 42, FontWeight.SemiBold),
    headlineSmall = Basis.headlineSmall.lesbar(26, 36, FontWeight.SemiBold),
    titleLarge = Basis.titleLarge.lesbar(22, 32, FontWeight.SemiBold),
    titleMedium = Basis.titleMedium.lesbar(18, 28, FontWeight.SemiBold),
    bodyLarge = Basis.bodyLarge.lesbar(18, 28),
    bodyMedium = Basis.bodyMedium.lesbar(16, 26),
    bodySmall = Basis.bodySmall.lesbar(14, 22),
    labelLarge = Basis.labelLarge.lesbar(16, 24, FontWeight.SemiBold),
    labelMedium = Basis.labelMedium.lesbar(14, 20, FontWeight.Medium),
)

private val LeonardoFormen = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun LeonardoTheme(content: @Composable () -> Unit) {
    val dunkel = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalRueckmeldefarben provides if (dunkel) DunkleRueckmeldung else HelleRueckmeldung,
    ) {
        MaterialTheme(
            colorScheme = if (dunkel) DunklesSchema else HellesSchema,
            typography = LeonardoTypografie,
            shapes = LeonardoFormen,
            content = content,
        )
    }
}
