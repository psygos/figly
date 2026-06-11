package app.figly.probe

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.figly.data.FigRepository

/**
 * PROBE — the voice. An instrument that asks five questions, takes
 * ≤ 20 seconds and a handful of taps, seals, and shuts up.
 *
 * The asking face is questions only, full width, sized to be read at
 * arm's length — the day's stamp appears once the day is sealed, and
 * the living fig is always one flip away. Type and targets scale with
 * the widget: 4×3 is home, 4×2 still works.
 */
class ProbeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = Probe.state(context)
        val sealedBmp =
            if (state.kind == ProbeState.Kind.SEALED && state.sealedStamp.isNotEmpty()) {
                StampBitmaps.stamp(state.sealedStamp, 200)
            } else null
        val silhouetteBmp =
            if (state.kind == ProbeState.Kind.PRESSING) {
                StampBitmaps.silhouette(FigRepository.get(context).liveFig(), 220)
            } else null

        provideContent {
            ProbeContent(state, sealedBmp, silhouetteBmp)
        }
    }
}

class ProbeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProbeWidget()
}

// ── Ink ────────────────────────────────────────────────────────────────

private val PLATE = ColorProvider(Color(0xFF111317))
private val HAIRLINE = ColorProvider(Color(0xFF20232A))
private val INK = ColorProvider(Color(0xFFE7E2D5))
private val INK_DIM = ColorProvider(Color(0xFF6B675C))
private val INK_FAINT = ColorProvider(Color(0xFF3A3933))

/** Over budget is a climate, not an alarm: ashfall rust, not red. */
private val ASHFALL = ColorProvider(Color(0xFFA3653F))

private fun mono(c: ColorProvider, size: Int) = TextStyle(
    color = c,
    fontSize = size.sp,
    fontFamily = FontFamily.Monospace,
)

/** Everything that scales with the widget's real size. 4×3 is home. */
private data class Fit(
    val rowH: Int,      // question row height, dp
    val labelSp: Int,
    val valueSp: Int,
    val dotSp: Int,
    val sealH: Int,
    val pad: Int,
    val header: Boolean,
)

@Composable
private fun fit(): Fit {
    val h = LocalSize.current.height
    return when {
        h < 150.dp -> Fit(rowH = 25, labelSp = 10, valueSp = 12, dotSp = 15, sealH = 20, pad = 8, header = false)
        h < 210.dp -> Fit(rowH = 33, labelSp = 12, valueSp = 14, dotSp = 18, sealH = 26, pad = 12, header = false)
        else -> Fit(rowH = 40, labelSp = 13, valueSp = 15, dotSp = 21, sealH = 30, pad = 14, header = true)
    }
}

// ── Composition ────────────────────────────────────────────────────────

@Composable
private fun ProbeContent(
    s: ProbeState,
    sealed: Bitmap?,
    silhouette: Bitmap?,
) {
    val f = fit()
    // Hairline border: a 1 dp reveal of hairline under the plate.
    Box(GlanceModifier.fillMaxSize().background(HAIRLINE).padding(1.dp)) {
        Box(
            GlanceModifier.fillMaxSize().background(PLATE)
                .padding(horizontal = (f.pad + 2).dp, vertical = f.pad.dp),
        ) {
            when (s.kind) {
                ProbeState.Kind.SEALED -> SealedFace(s, sealed, f)
                ProbeState.Kind.PRESSING -> PressingFace(s, silhouette, f)
                else -> AskingFace(s, f)
            }
        }
    }
}

@Composable
private fun SealedFace(s: ProbeState, stamp: Bitmap?, f: Fit) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (stamp != null) {
            Image(
                provider = ImageProvider(stamp),
                contentDescription = "today's mark",
                modifier = GlanceModifier.size((f.rowH * 3).dp),
            )
            Spacer(GlanceModifier.width(16.dp))
        }
        Text(Probe.tracked("DAY SEALED · ${s.dayName}"), style = mono(INK_DIM, f.valueSp))
    }
}

@Composable
private fun PressingFace(s: ProbeState, silhouette: Bitmap?, f: Fit) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (silhouette != null) {
            Image(
                provider = ImageProvider(silhouette),
                contentDescription = "the week's fig, pressed",
                modifier = GlanceModifier.size((f.rowH * 3.4f).dp),
            )
            Spacer(GlanceModifier.width(14.dp))
        }
        Text(Probe.tracked("${s.figId} PRESSED"), style = mono(INK, f.valueSp))
    }
}

/** Questions only, full width. The fig lives on the matrix, not here. */
@Composable
private fun AskingFace(s: ProbeState, f: Fit) {
    Column(GlanceModifier.fillMaxSize()) {
        if (f.header) {
            Row(
                GlanceModifier.fillMaxWidth().height((f.rowH * 0.75f).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Probe.tracked(s.dayName), style = mono(INK, f.valueSp))
                Spacer(GlanceModifier.defaultWeight())
                Text(Probe.tracked("DAY ${s.dayIndex} OF 7"), style = mono(INK_FAINT, f.labelSp))
            }
            Rule()
            Spacer(GlanceModifier.height(2.dp))
        }
        if (s.grace != null) {
            GraceRow(s, f)
        }
        if (s.mood == null) {
            DotsRow("MOOD", null, f) { v ->
                actionRunCallback<SetMoodAction>(actionParametersOf(PARAM_VALUE to v))
            }
        } else {
            CollapsedRow("MOOD", "● ${s.mood}", "mood", f)
        }
        SleepRow(s, f)
        if (s.effort == null) {
            DotsRow(s.effortLabel, null, f) { v ->
                actionRunCallback<SetEffortAction>(actionParametersOf(PARAM_VALUE to v))
            }
        } else {
            CollapsedRow(s.effortLabel, "● ${s.effort}", "effort", f)
        }
        ScreenRow(s, f)
        Spacer(GlanceModifier.defaultWeight())
        SealRow(s, f)
    }
}

@Composable
private fun Rule() {
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(HAIRLINE)) {}
}

@Composable
private fun GraceRow(s: ProbeState, f: Fit) {
    val pkg = LocalContext.current.packageName
    Row(
        GlanceModifier.fillMaxWidth().height((f.rowH * 0.8f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Probe.tracked("YESTERDAY?"), style = mono(INK_DIM, f.labelSp))
        Spacer(GlanceModifier.defaultWeight())
        Text(
            Probe.tracked("FILL"),
            style = mono(INK, f.labelSp),
            modifier = GlanceModifier.clickable(
                actionStartActivity(
                    Intent(SLEEP_SHEET_ACTION).setPackage(pkg).apply {
                        putExtra("date", s.grace.toString())
                        putExtra("mode", "grace")
                    },
                ),
            ).padding(horizontal = 8.dp),
        )
        Text(
            Probe.tracked("SCAR IT"),
            style = mono(INK_DIM, f.labelSp),
            modifier = GlanceModifier.clickable(
                actionRunCallback<GraceScarAction>(
                    actionParametersOf(PARAM_DATE to s.grace.toString()),
                ),
            ).padding(horizontal = 8.dp),
        )
    }
    Rule()
}

/** Five fat cells across the full remaining width. ○ waits, ● answers. */
@Composable
private fun DotsRow(
    name: String,
    selected: Int?,
    f: Fit,
    action: (Int) -> androidx.glance.action.Action,
) {
    Row(
        GlanceModifier.fillMaxWidth().height(f.rowH.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Probe.tracked(name),
            style = mono(INK_DIM, f.labelSp),
            maxLines = 1,
            modifier = GlanceModifier.width(92.dp),
        )
        for (v in 1..5) {
            val filled = selected != null && v <= selected
            Box(
                GlanceModifier.defaultWeight().fillMaxHeight().clickable(action(v)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (filled) "●" else "○",
                    style = mono(if (filled) INK else INK_DIM, f.dotSp),
                )
            }
        }
    }
}

/** An answered row, collapsed to its mark. Tap the mark to amend. */
@Composable
private fun CollapsedRow(name: String, mark: String, channel: String, f: Fit) {
    Row(
        GlanceModifier.fillMaxWidth().height((f.rowH * 0.8f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Probe.tracked(name),
            style = mono(INK_FAINT, f.labelSp),
            maxLines = 1,
            modifier = GlanceModifier.width(92.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            mark,
            style = mono(INK_DIM, f.valueSp),
            modifier = GlanceModifier
                .clickable(actionRunCallback<AmendAction>(actionParametersOf(PARAM_CHANNEL to channel)))
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun SleepRow(s: ProbeState, f: Fit) {
    if (s.sleepConfirmed && s.bedMin != null && s.durMin != null) {
        CollapsedRow(
            "SLEPT",
            "${Probe.clockOf(s.bedMin)} → ${Probe.wakeOf(s.bedMin, s.durMin)}",
            "sleep",
            f,
        )
        return
    }
    Row(
        GlanceModifier.fillMaxWidth().height(f.rowH.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Probe.tracked("SLEPT"),
            style = mono(INK_DIM, f.labelSp),
            maxLines = 1,
            modifier = GlanceModifier.width(92.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        val sheet = actionStartActivity(
            Intent(SLEEP_SHEET_ACTION).setPackage(LocalContext.current.packageName)
                .apply { putExtra("mode", "today") },
        )
        if (s.bedMin != null && s.durMin != null) {
            // A suggested night: tap the times to adjust, OK takes them.
            Text(
                "${Probe.clockOf(s.bedMin)} → ${Probe.wakeOf(s.bedMin, s.durMin)}",
                style = mono(INK, f.valueSp),
                modifier = GlanceModifier.clickable(sheet),
            )
            Text(
                Probe.tracked("[ OK ]"),
                style = mono(INK, f.valueSp),
                modifier = GlanceModifier
                    .clickable(actionRunCallback<ConfirmSleepAction>())
                    .padding(horizontal = 6.dp),
            )
        } else {
            Text(
                Probe.tracked("[ SET ]"),
                style = mono(INK, f.valueSp),
                modifier = GlanceModifier.clickable(sheet).padding(horizontal = 8.dp),
            )
        }
    }
}

/**
 * Screen is data, not a question: the instrument reads its own minutes
 * and pre-answers. Over budget wears ashfall rust. A tap overrules.
 */
@Composable
private fun ScreenRow(s: ProbeState, f: Fit) {
    Row(
        GlanceModifier.fillMaxWidth().height((f.rowH * 0.8f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Probe.tracked("SCREEN"),
            style = mono(INK_DIM, f.labelSp),
            maxLines = 1,
            modifier = GlanceModifier.width(92.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        val minutes = s.screenMin?.let { m ->
            if (m >= 60) "${m / 60}H%02d".format(java.util.Locale.ROOT, m % 60) else "${m}M"
        }
        val (text, color) = when (s.underBudget) {
            true -> listOfNotNull(minutes, "UNDER").joinToString(" · ") to INK
            false -> listOfNotNull(minutes, "OVER").joinToString(" · ") to ASHFALL
            null -> "[ TAP TO SET ]" to INK_DIM
        }
        Text(
            Probe.tracked(text),
            style = mono(color, f.valueSp),
            modifier = GlanceModifier
                .clickable(actionRunCallback<ToggleScreenAction>())
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun SealRow(s: ProbeState, f: Fit) {
    Rule()
    if (s.sealable) {
        Box(
            GlanceModifier.fillMaxWidth().height(f.sealH.dp)
                .clickable(actionRunCallback<SealDayAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(Probe.tracked("[ SEAL DAY ]"), style = mono(INK, f.valueSp))
        }
    } else {
        // The record knows how far it is. No urging, just the count.
        Box(
            GlanceModifier.fillMaxWidth().height(f.sealH.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                Probe.tracked("READINGS OPEN · ${s.answered} OF 5"),
                style = mono(INK_FAINT, f.labelSp),
            )
        }
    }
}

const val SLEEP_SHEET_ACTION = "app.figly.SLEEP_SHEET"
