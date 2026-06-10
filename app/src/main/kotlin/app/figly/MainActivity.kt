package app.figly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.figly.data.FigRepository
import app.figly.probe.Reminder
import app.figly.screens.HomeScreen
import app.figly.screens.KeyScreen
import app.figly.screens.PlateScreen
import app.figly.screens.SettingsScreen
import app.figly.ui.Ink
import app.figly.ui.QuietIndication

/**
 * HERBARIUM — ink. One activity, one vertical stack: this week, the
 * drawer, a plate, the key, settings. No bottom bar, no tabs, dark only.
 */
sealed interface Screen {
    data object Home : Screen
    data class Plate(val isoWeek: String) : Screen
    data object Key : Screen
    data object Settings : Screen
}

/** Standard motion: 180 ms, cubic-bezier(0.2, 0, 0, 1); plates open in 240. */
val FiglyEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DemoSeed.plantIfAsked(this, intent)
        setContent {
            CompositionLocalProvider(LocalIndication provides QuietIndication) {
                Herbarium()
            }
        }
    }
}

@Composable
private fun Herbarium() {
    val context = LocalContext.current
    val repo = remember { FigRepository.get(context) }
    var screen by remember {
        mutableStateOf<Screen>(if (repo.keySeen) Screen.Home else Screen.Key)
    }

    LaunchedEffect(Unit) {
        // App open is a pressing event.
        repo.resolveLapsedGraces()
        repo.pressIfDue()
        Reminder.schedule(context)
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = Screen.Home
    }

    AnimatedContent(
        targetState = screen,
        modifier = Modifier.fillMaxSize().background(Ink.ground),
        transitionSpec = {
            val opening = targetState != Screen.Home
            val duration = if (targetState is Screen.Plate) 240 else 180
            (fadeIn(tween(duration, easing = FiglyEasing)) +
                slideInVertically(tween(duration, easing = FiglyEasing)) {
                    if (opening) it / 24 else -it / 24
                })
                .togetherWith(fadeOut(tween(120, easing = FiglyEasing)))
        },
        label = "screens",
    ) { s ->
        when (s) {
            is Screen.Home -> HomeScreen(
                openPlate = { screen = Screen.Plate(it) },
                openSettings = { screen = Screen.Settings },
                openKey = { screen = Screen.Key },
            )
            is Screen.Plate -> PlateScreen(
                isoWeek = s.isoWeek,
                back = { screen = Screen.Home },
                openKey = { screen = Screen.Key },
            )
            is Screen.Key -> KeyScreen(
                firstRun = !repo.keySeen,
                done = {
                    repo.keySeen = true
                    screen = Screen.Home
                },
            )
            is Screen.Settings -> SettingsScreen(back = { screen = Screen.Home })
        }
    }
}
