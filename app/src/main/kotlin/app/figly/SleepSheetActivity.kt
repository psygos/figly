package app.figly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import app.figly.core.DayReading
import app.figly.data.FigRepository
import app.figly.data.UsageReadings
import app.figly.probe.ProbeWidget
import app.figly.ui.Ink
import app.figly.ui.Label
import app.figly.ui.Micro
import app.figly.ui.QuietIndication
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The slim sheet Probe opens for sleep — 15-minute steppers, confirm,
 * gone. In grace mode it takes all five of yesterday's readings and
 * seals them.
 */
class SleepSheetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "today"
        val date = intent.getStringExtra("date")?.let(LocalDate::parse)
            ?: if (mode == "grace") LocalDate.now().minusDays(1) else LocalDate.now()

        setContent {
            CompositionLocalProvider(LocalIndication provides QuietIndication) {
                Sheet(mode == "grace", date) { finish() }
            }
        }
    }
}

@Composable
private fun Sheet(grace: Boolean, date: LocalDate, close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { FigRepository.get(context) }
    val scope = rememberCoroutineScope()

    val draft = remember { repo.draft(date) }
    val suggestion = remember {
        if (draft.bedMin == null) UsageReadings.suggestSleep(context, date) else null
    }

    var bed by remember {
        mutableIntStateOf(draft.bedMin ?: suggestion?.bedMinutesAfterNoon ?: 690) // 23:30
    }
    var dur by remember {
        mutableIntStateOf(draft.durMin ?: suggestion?.durationMin ?: 480)
    }
    var mood by remember { mutableStateOf(draft.mood) }
    var effort by remember { mutableStateOf(draft.effort) }
    var under by remember { mutableStateOf(draft.underBudget) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.ground.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = close,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Ink.s3)
                .navigationBarsPadding()
                .border(1.dp, Ink.hairline)
                .background(Ink.plate)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(Ink.s4),
        ) {
            Micro(
                if (grace) "YESTERDAY · ${dayName(date)}" else "LAST NIGHT",
                color = Ink.inkDim,
            )
            Spacer(Modifier.height(Ink.s4))

            StepperRow("BED", clock(bed)) { d -> bed = wrap(bed + d) }
            StepperRow("WAKE", clock(bed + dur)) { d -> dur = (dur + d).coerceIn(0, 16 * 60) }
            Spacer(Modifier.height(Ink.s2))
            Label("≈ ${durLabel(dur)}", Ink.data(11.sp, Ink.inkDim))

            if (grace) {
                Spacer(Modifier.height(Ink.s4))
                DotsRow("MOOD", mood) { mood = it }
                DotsRow(repo.effortLabel, effort) { effort = it }
                Row(
                    Modifier.fillMaxWidth().height(28.dp)
                        .clickable { under = !(under ?: false) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Micro("SCREEN", color = Ink.inkDim)
                    Spacer(Modifier.weight(1f))
                    Micro(
                        when (under) {
                            true -> "UNDER ✓"
                            false -> "OVER ·"
                            null -> "— TAP"
                        },
                        color = if (under != null) Ink.ink else Ink.inkDim,
                    )
                }
            }

            Spacer(Modifier.height(Ink.s4))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.hairline))
            Spacer(Modifier.height(Ink.s3))

            val complete = !grace || (mood != null && effort != null && under != null)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clickable(enabled = complete) {
                        scope.launch {
                            if (grace) {
                                repo.sealDay(
                                    date,
                                    DayReading(
                                        mood = mood!!,
                                        bedMinutesAfterNoon = bed,
                                        durationMin = dur,
                                        effort = effort!!,
                                        underBudget = under!!,
                                    ),
                                )
                            } else {
                                repo.updateDraft(date) {
                                    copy(bedMin = bed, durMin = dur, sleepConfirmed = true)
                                }
                            }
                            ProbeWidget().updateAll(context)
                            close()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Micro(
                    if (grace) "[ SEAL YESTERDAY ]" else "[ CONFIRM ]",
                    color = if (complete) Ink.ink else Ink.inkFaint,
                    size = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun StepperRow(name: String, value: String, step: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Micro(name, Modifier.width(64.dp), color = Ink.inkDim)
        Spacer(Modifier.weight(1f))
        Micro("−15", Modifier.clickable { step(-15) }.padding(Ink.s3), color = Ink.ink)
        Label(value, Ink.data(14.sp, Ink.ink))
        Micro("+15", Modifier.clickable { step(+15) }.padding(Ink.s3), color = Ink.ink)
    }
}

@Composable
private fun DotsRow(name: String, selected: Int?, choose: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Micro(name, color = Ink.inkDim)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Ink.s1)) {
            for (v in 1..5) {
                Box(
                    Modifier.size(28.dp).clickable { choose(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Label(
                        if (selected != null && v <= selected) "●" else "·",
                        Ink.data(13.sp, if (selected != null && v <= selected) Ink.ink else Ink.inkDim),
                    )
                }
            }
        }
    }
}

private fun wrap(min: Int): Int = ((min % 1440) + 1440) % 1440

private fun clock(minAfterNoon: Int): String {
    val abs = (12 * 60 + wrap(minAfterNoon)) % (24 * 60)
    return "%02d:%02d".format(Locale.ROOT, abs / 60, abs % 60)
}

private fun durLabel(min: Int): String = "${min / 60}H%02d".format(Locale.ROOT, min % 60)

private fun dayName(date: LocalDate): String =
    app.figly.core.WeekKeys.DAY_NAMES[app.figly.core.WeekKeys.dayIndex(date) - 1]
