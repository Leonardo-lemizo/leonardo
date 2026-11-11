package app.leonardo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.leonardo.R
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeonardoTopBar(titel: String, onZurueck: () -> Unit) {
    TopAppBar(
        title = { Text(titel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onZurueck) {
                Icon(painterResource(R.drawable.ic_zurueck), contentDescription = "Zurück")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** Großer, gut treffbarer Knopf. Pro Bildschirm gibt es möglichst nur einen gefüllten. */
@Composable
fun GrosserKnopf(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dezent: Boolean = false,
) {
    val form = RoundedCornerShape(20.dp)
    val groesse = modifier.fillMaxWidth().heightIn(min = 60.dp)
    val inhalt: @Composable () -> Unit = {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
    if (dezent) {
        OutlinedButton(onClick = onClick, modifier = groesse, enabled = enabled, shape = form) { inhalt() }
    } else {
        Button(onClick = onClick, modifier = groesse, enabled = enabled, shape = form) { inhalt() }
    }
}

/** Dicker Fortschrittsbalken, der sich weich füllt. */
@Composable
fun Fortschrittsbalken(anteil: Float, modifier: Modifier = Modifier) {
    val gefuellt by animateFloatAsState(anteil.coerceIn(0f, 1f), tween(400), label = "balken")
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(gefuellt)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Ring, der sich beim Erscheinen und bei Änderungen weich füllt. */
@Composable
fun Fortschrittsring(
    anteil: Float,
    modifier: Modifier = Modifier,
    dicke: Dp = 8.dp,
    inhalt: @Composable () -> Unit = {},
) {
    val gefuellt by animateFloatAsState(anteil.coerceIn(0f, 1f), tween(700), label = "ring")
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    val farbe = MaterialTheme.colorScheme.primary

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strich = dicke.toPx()
            val ecke = Offset(strich / 2, strich / 2)
            val flaeche = Size(size.width - strich, size.height - strich)
            drawArc(spur, 0f, 360f, false, ecke, flaeche, style = Stroke(strich))
            if (gefuellt > 0f) {
                drawArc(farbe, -90f, 360f * gefuellt, false, ecke, flaeche, style = Stroke(strich, cap = StrokeCap.Round))
            }
        }
        inhalt()
    }
}

/** Emoji in einem farbigen Kreis – als schnelles Wiedererkennungszeichen. */
@Composable
fun EmojiKreis(emoji: String, farbe: Color, modifier: Modifier = Modifier, groesse: Dp = 52.dp) {
    Box(modifier.size(groesse).clip(CircleShape).background(farbe), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (groesse.value * 0.48f).sp)
    }
}

/** Kleines Etikett, z. B. für den Übungstyp. */
@Composable
fun Etikett(text: String, hintergrund: Color, schrift: Color, modifier: Modifier = Modifier) {
    Row(modifier.clip(CircleShape).background(hintergrund).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = schrift)
    }
}

private class Konfettiteil(
    val x: Float,
    val verzoegerung: Float,
    val tempo: Float,
    val radius: Float,
    val schwung: Float,
    val farbe: Int,
)

/** Einmaliger, kurzer Konfetti-Regen als Belohnung am Ende einer Runde. */
@Composable
fun Konfetti(modifier: Modifier = Modifier) {
    val verlauf = remember { Animatable(0f) }
    LaunchedEffect(Unit) { verlauf.animateTo(1f, tween(2200, easing = LinearOutSlowInEasing)) }

    val teile = remember {
        List(30) {
            Konfettiteil(
                x = Random.nextFloat(),
                verzoegerung = Random.nextFloat() * 0.25f,
                tempo = 0.7f + Random.nextFloat() * 0.5f,
                radius = 6f + Random.nextFloat() * 10f,
                schwung = 20f + Random.nextFloat() * 40f,
                farbe = it % 4,
            )
        }
    }
    val farben = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        Color(0xFFE9B949),
        MaterialTheme.colorScheme.primaryContainer,
    )

    Canvas(modifier.fillMaxSize()) {
        val t = verlauf.value
        if (t >= 1f) return@Canvas
        teile.forEach { teil ->
            val eigen = ((t - teil.verzoegerung) / (1f - teil.verzoegerung)).coerceIn(0f, 1f) * teil.tempo
            if (eigen <= 0f) return@forEach
            val x = teil.x * size.width + sin(eigen * 7f + teil.x * 10f) * teil.schwung
            val y = eigen * size.height * 0.85f - 20f
            drawCircle(farben[teil.farbe].copy(alpha = (1f - eigen).coerceIn(0f, 1f)), teil.radius, Offset(x, y))
        }
    }
}
