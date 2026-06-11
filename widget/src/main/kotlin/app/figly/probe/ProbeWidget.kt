package app.figly.probe

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.cornerRadius
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
 * PROBE — the voice. An instrument that asks five questions and shuts up.
 *
 * Three groups, hairline rhythm: the day · the two hand-scales (always
 * visible, filled to your answer — change it by tapping another dot) ·
 * the two sensed lines (sleep suggested, screen inferred) · the verb.
 * Corners follow the OS; type follows the widget's true size. 4×3 is
 * home, 4×4 breathes, 4×2 still answers.
 */
class ProbeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = Probe.state(context)
        val sealedBmp =
            if (state.kind == ProbeState.Kind.SEALED && state.sealedStamp.isNotEmpty()) {
                StampBitmaps.stamp(state.sealedStamp, 220)
            } else null
        val silhouetteBmp =
            if (state.kind == ProbeState.Kind.PRESSING) {
                StampBitmaps.silhouette(FigRepository.get(context).liveFig(), 240)
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

/** Everything that scales with the widget's true size. */
private data class Fit(
    val header: Boolean,
    val scaleRowH: Int,   // the dot banks — the heroes
    val senseRowH: Int,   // sleep, screen, grace
    val headerH: Int,
    val labelSp: Int,
    val valueSp: Int,
    val daySp: Int,
    val dotSp: Int,
    val sealH: Int,
    val pad: Int,
)

/**
 * Fluid fit: the rows divide the height the launcher actually gave us,
 * so the bottom line is never clipped — on any grid, any cell size.
 * Type steps in three sizes; space flows continuously.
 */
@Composable
private fun fit(hasGrace: Boolean): Fit {
    val h = LocalSize.current.height.value
    val (labelSp, valueSp, daySp, dotSp, pad, header) = when {
        h < 150f -> listOf(10, 12, 13, 16, 8, 0)
        h < 210f -> listOf(12, 14, 16, 20, 12, 1)
        else -> listOf(13, 15, 18, 23, 14, 1)
    }
    val showHeader = header == 1
    val sealH = if (h < 150f) 20f else 26f

    // Units of height: scales 1.0 each, senses 0.7, header 0.8, grace 0.7.
    var units = 3f * 1.0f + 2f * 0.7f
    if (showHeader) units += 0.8f
    if (hasGrace) units += 0.7f
    val rules = 2 + (if (showHeader) 1 else 0) + (if (hasGrace) 1 else 0)
    val available = (h - 2 * pad - sealH - rules).coerceAtLeast(60f)
    val unit = available / units

    return Fit(
        header = showHeader,
        scaleRowH = unit.coerceIn(24f, 56f).toInt(),
        senseRowH = (unit * 0.7f).coerceIn(18f, 38f).toInt(),
        headerH = (unit * 0.8f).coerceIn(20f, 44f).toInt(),
        labelSp = labelSp,
        valueSp = valueSp,
        daySp = daySp,
        dotSp = dotSp,
        sealH = sealH.toInt(),
        pad = pad,
    )
}

private operator fun <T> List<T>.component6(): T = this[5]

/** The launcher's own widget rounding, so the plate sits native. */
@Composable
private fun systemCornerRadius(): Dp {
    val context = LocalContext.current
    return remember {
        runCatching {
            val px = context.resources
                .getDimension(android.R.dimen.system_app_widget_background_radius)
            (px / context.resources.displayMetrics.density).dp
        }.getOrDefault(16.dp).coerceAtMost(28.dp)
    }
}

// ── Composition ────────────────────────────────────────────────────────

@Composable
private fun ProbeContent(
    s: ProbeState,
    sealed: Bitmap?,
    silhouette: Bitmap?,
) {
    val f = fit(
        hasGrace = s.grace != null &&
            s.kind != ProbeState.Kind.SEALED && s.kind != ProbeState.Kind.PRESSING,
    )
    val r = systemCornerRadius()
    // Hairline ring: a 1 dp reveal of hairline under the plate, both
    // rounded to the OS radius.
    Box(
        GlanceModifier.fillMaxSize().background(HAIRLINE)
            .cornerRadius(r).padding(1.dp),
    ) {
        Box(
            GlanceModifier.fillMaxSize().background(PLATE)
                .cornerRadius(r - 1.dp)
                .padding(horizontal = (f.pad + 4).dp, vertical = f.pad.dp),
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
                modifier = GlanceModifier.size((f.scaleRowH * 2.6f).dp),
            )
            Spacer(GlanceModifier.width(16.dp))
        }
        Column {
            Text(Probe.tracked(s.dayName), style = mono(INK, f.daySp))
            Spacer(GlanceModifier.height(4.dp))
            Text(Probe.tracked("DAY SEALED"), style = mono(INK_DIM, f.labelSp))
        }
    }
}

@Composable
private fun PressingFace(s: ProbeState, silhouette: Bitmap?, f: Fit) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (silhouette != null) {
            Image(
                provider = ImageProvider(silhouette),
                contentDescription = "the week's fig, pressed",
                modifier = GlanceModifier.size((f.scaleRowH * 3f).dp),
            )
            Spacer(GlanceModifier.width(14.dp))
        }
        Column {
            Text(Probe.tracked(s.figId), style = mono(INK, f.valueSp))
            Spacer(GlanceModifier.height(4.dp))
            Text(Probe.tracked("PRESSED"), style = mono(INK_DIM, f.labelSp))
        }
    }
}

@Composable
private fun AskingFace(s: ProbeState, f: Fit) {
    Column(GlanceModifier.fillMaxSize()) {
        if (f.header) {
            Row(
                GlanceModifier.fillMaxWidth().height(f.headerH.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Probe.tracked(s.dayName), style = mono(INK, f.daySp))
                Spacer(GlanceModifier.defaultWeight())
                Text(Probe.tracked("DAY ${s.dayIndex} OF 7"), style = mono(INK_FAINT, f.labelSp))
            }
            Rule()
        }
        if (s.grace != null) GraceRow(s, f)

        // The three hand-scales: always visible, filled to the answer.
        ScaleRow("MOOD", s.mood, f) { v ->
            actionRunCallback<SetMoodAction>(actionParametersOf(PARAM_VALUE to v))
        }
        ScaleRow("BODY", s.body, f) { v ->
            actionRunCallback<SetBodyAction>(actionParametersOf(PARAM_VALUE to v))
        }
        ScaleRow("MIND", s.mind, f) { v ->
            actionRunCallback<SetMindAction>(actionParametersOf(PARAM_VALUE to v))
        }

        Rule()

        // The two sensed lines.
        SleepRow(s, f)
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
        GlanceModifier.fillMaxWidth().height(f.senseRowH.dp),
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

/**
 * A 1–5 scale as five fat cells across the full width. ○ waits, ● holds
 * the answer; tap any cell, any time before the seal, to change it.
 */
@Composable
private fun ScaleRow(
    name: String,
    selected: Int?,
    f: Fit,
    action: (Int) -> androidx.glance.action.Action,
) {
    Row(
        GlanceModifier.fillMaxWidth().height(f.scaleRowH.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Probe.tracked(name),
            style = mono(if (selected == null) INK_DIM else INK_FAINT, f.labelSp),
            maxLines = 1,
            modifier = GlanceModifier.width(96.dp),
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

@Composable
private fun SleepRow(s: ProbeState, f: Fit) {
    Row(
        GlanceModifier.fillMaxWidth().height(f.senseRowH.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Probe.tracked("SLEPT"),
            style = mono(if (s.sleepConfirmed) INK_FAINT else INK_DIM, f.labelSp),
            maxLines = 1,
            modifier = GlanceModifier.width(96.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        val sheet = actionStartActivity(
            Intent(SLEEP_SHEET_ACTION).setPackage(LocalContext.current.packageName)
                .apply { putExtra("mode", "today") },
        )
        when {
            s.sleepConfirmed && s.bedMin != null && s.durMin != null -> {
                // Confirmed; tap the times to adjust.
                Text(
                    "${Probe.clockOf(s.bedMin)} → ${Probe.wakeOf(s.bedMin, s.durMin)} ✓",
                    style = mono(INK_DIM, f.valueSp),
                    modifier = GlanceModifier.clickable(sheet).padding(horizontal = 6.dp),
                )
            }
            s.bedMin != null && s.durMin != null -> {
                // The night the instrument heard: OK takes it.
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
            }
            else -> {
                Text(
                    Probe.tracked("[ SET ]"),
                    style = mono(INK, f.valueSp),
                    modifier = GlanceModifier.clickable(sheet).padding(horizontal = 8.dp),
                )
            }
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
        GlanceModifier.fillMaxWidth().height(f.senseRowH.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Probe.tracked("SCREEN"),
            style = mono(if (s.underBudget != null) INK_FAINT else INK_DIM, f.labelSp),
            maxLines = 1,
            modifier = GlanceModifier.width(96.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        val minutes = s.screenMin?.let { m ->
            if (m >= 60) "${m / 60}H%02d".format(java.util.Locale.ROOT, m % 60) else "${m}M"
        }
        val (text, color) = when (s.underBudget) {
            true -> listOfNotNull(minutes, "UNDER").joinToString(" · ") to INK_DIM
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
                "READINGS · ${s.answered} OF 6",
                style = mono(INK_FAINT, f.labelSp),
                maxLines = 1,
            )
        }
    }
}

const val SLEEP_SHEET_ACTION = "app.figly.SLEEP_SHEET"
