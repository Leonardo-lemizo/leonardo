package app.leonardo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.leonardo.ui.EinheitScreen
import app.leonardo.ui.EinstellungenScreen
import app.leonardo.ui.ErklaerungScreen
import app.leonardo.ui.KapitelScreen
import app.leonardo.ui.KartenScreen
import app.leonardo.ui.LeonardoTheme
import app.leonardo.ui.StartScreen
import app.leonardo.ui.UebenScreen

class MainActivity : ComponentActivity() {

    private val vm: LeonardoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeonardoTheme {
                LeonardoApp(vm)
            }
        }
    }

    /** Beim Verlassen der App den Lernstand sofort sichern. */
    override fun onStop() {
        super.onStop()
        vm.sichernJetzt()
    }
}

@Composable
fun LeonardoApp(vm: LeonardoViewModel) {
    val stand by vm.stand.collectAsState()
    BackHandler(enabled = vm.screen != Screen.Start) { vm.zurueck() }

    // "Größere Schrift" vergrößert alle Texte der App um 15 %.
    val dichte = LocalDensity.current
    val schrift = if (stand.einstellungen.grosseSchrift) dichte.fontScale * 1.15f else dichte.fontScale

    CompositionLocalProvider(LocalDensity provides Density(dichte.density, schrift)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = vm.screen,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(100)) },
                label = "screen",
            ) { screen ->
                when (screen) {
                    Screen.Start -> StartScreen(vm)
                    Screen.Einstellungen -> EinstellungenScreen(vm)
                    is Screen.KapitelSeite -> KapitelScreen(vm, screen.kapitelId)
                    is Screen.EinheitSeite -> EinheitScreen(vm, screen.einheitId)
                    is Screen.Erklaerung -> ErklaerungScreen(vm, screen.runde)
                    is Screen.Karten -> KartenScreen(vm, screen)
                    is Screen.Ueben -> UebenScreen(vm, screen.runde)
                }
            }
        }
    }
}
