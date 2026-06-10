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
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
 * ≤ 20 seconds and ≤ 6 taps, seals, and shuts up.
 */
class ProbeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = Probe.state(context)
        val stampBmp = StampBitmaps.stamp(state.stamp, 160)
        val sealedBmp =
            if (state.kind == ProbeState.Kind.SEALED && state.sealedStamp.isNotEmpty()) {
                StampBitmaps.stamp(state.sealedStamp, 160)
            } else null
        val silhouetteBmp =
            if (state.kind == ProbeState.Kind.PRESSING) {
                StampBitmaps.silhouette(FigRepository.get(context).liveFig(), 200)
            } else null

        provideContent {
            ProbeContent(state, stampBmp, sealedBmp, silhouetteBmp)
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

private fun micro(c: ColorProvider, size: Int = 10) = TextStyle(
    color = c,
    fontSize = size.sp,
    fontFamily = FontFamily.Monospace,
)

// ── Composition ────────────────────────────────────────────────────────

@Composable
private fun ProbeContent(
    s: ProbeState,
    stamp: Bitmap,
    sealed: Bitmap?,
    silhouette: Bitmap?,
) {
    // Hairline border: a 1 dp reveal of hairline under the plate.
    Box(GlanceModifier.fillMaxSize().background(HAIRLINE).padding(1.dp)) {
        Box(GlanceModifier.fillMaxSize().background(PLATE).padding(horizontal = 12.dp, vertical = 8.dp)) {
            when (s.kind) {
                ProbeState.Kind.SEALED -> SealedFace(s, sealed ?: stamp)
                ProbeState.Kind.PRESSING -> PressingFace(s, silhouette)
                else -> AskingFace(s, stamp)
            }
        }
    }
}

@Composable
private fun SealedFace(s: ProbeState, stamp: Bitmap) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(stamp),
            contentDescription = "today's mark",
            modifier = GlanceModifier.size(64.dp),
        )
        Spacer(GlanceModifier.width(14.dp))
        Text(Probe.tracked("DAY SEALED · ${s.dayName}"), style = micro(INK_DIM, 11))
    }
}

@Composable
private fun PressingFace(s: ProbeState, silhouette: Bitmap?) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (silhouette != null) {
            Image(
                provider = ImageProvider(silhouette),
                contentDescription = "the week's fig, pressed",
                modifier = GlanceModifier.size(84.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
        }
        Text(Probe.tracked("${s.figId} PRESSED"), style = micro(INK, 11))
    }
}

@Composable
private fun AskingFace(s: ProbeState, stamp: Bitmap) {
    val early = s.kind == ProbeState.Kind.EARLY
    val label = if (early) INK_FAINT else INK_DIM
    val value = if (early) INK_DIM else INK

    Row(GlanceModifier.fillMaxSize()) {
        Column(
            GlanceModifier.width(76.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(stamp),
                contentDescription = "what today will add",
                modifier = GlanceModifier.size(72.dp),
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.fillMaxHeight().defaultWeight()) {
            if (s.grace != null) {
                GraceRow(s)
                Rule()
            }
            if (s.mood == null) {
                DotsRow("MOOD", null, label, value) { v ->
                    actionRunCallback<SetMoodAction>(actionParametersOf(PARAM_VALUE to v))
                }
            } else {
                CollapsedRow("MOOD", "${s.mood}", label)
            }
            SleepRow(s, label, value)
            if (s.effort == null) {
                DotsRow(s.effortLabel, null, label, value) { v ->
                    actionRunCallback<SetEffortAction>(actionParametersOf(PARAM_VALUE to v))
                }
            } else {
                CollapsedRow(s.effortLabel, "${s.effort}", label)
            }
            ScreenRow(s, label, value)
            Spacer(GlanceModifier.defaultWeight())
            SealRow(s, early)
        }
    }
}

@Composable
private fun Rule() {
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(HAIRLINE)) {}
}

@Composable
private fun GraceRow(s: ProbeState) {
    val pkg = LocalContext.current.packageName
    Row(GlanceModifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Probe.tracked("YESTERDAY?"), style = micro(INK_DIM, 9))
        Spacer(GlanceModifier.defaultWeight())
        Text(
            Probe.tracked("FILL"),
            style = micro(INK, 9),
            modifier = GlanceModifier.clickable(
                actionStartActivity(
                    Intent(SLEEP_SHEET_ACTION).setPackage(pkg).apply {
                        putExtra("date", s.grace.toString())
                        putExtra("mode", "grace")
                    },
                ),
            ).padding(horizontal = 4.dp),
        )
        Text(
            Probe.tracked("SCAR IT"),
            style = micro(INK_DIM, 9),
            modifier = GlanceModifier.clickable(
                actionRunCallback<GraceScarAction>(
                    actionParametersOf(PARAM_DATE to s.grace.toString()),
                ),
            ).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun DotsRow(
    name: String,
    selected: Int?,
    label: ColorProvider,
    value: ColorProvider,
    action: (Int) -> androidx.glance.action.Action,
) {
    Row(GlanceModifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Probe.tracked(name), style = micro(label, 9), maxLines = 1, modifier = GlanceModifier.width(76.dp))
        Spacer(GlanceModifier.defaultWeight())
        for (v in 1..5) {
            val filled = selected != null && v <= selected
            Box(
                GlanceModifier.size(20.dp).clickable(action(v)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (filled) "●" else "·", style = micro(if (filled) value else INK_DIM, 11))
            }
        }
    }
}

@Composable
private fun CollapsedRow(name: String, mark: String, label: ColorProvider) {
    Row(GlanceModifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Probe.tracked(name), style = micro(INK_FAINT, 9), maxLines = 1, modifier = GlanceModifier.width(76.dp))
        Spacer(GlanceModifier.defaultWeight())
        Text(mark, style = micro(label, 9))
    }
}

@Composable
private fun SleepRow(s: ProbeState, label: ColorProvider, value: ColorProvider) {
    if (s.sleepConfirmed && s.bedMin != null && s.durMin != null) {
        CollapsedRow("SLEPT", "${Probe.clockOf(s.bedMin)} → ${Probe.wakeOf(s.bedMin, s.durMin)}", label)
        return
    }
    Row(GlanceModifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Probe.tracked("SLEPT"), style = micro(label, 9), maxLines = 1, modifier = GlanceModifier.width(76.dp))
        Spacer(GlanceModifier.defaultWeight())
        val sheet = actionStartActivity(
            Intent(SLEEP_SHEET_ACTION).setPackage(LocalContext.current.packageName)
                .apply { putExtra("mode", "today") },
        )
        if (s.bedMin != null && s.durMin != null) {
            Text(
                "${Probe.clockOf(s.bedMin)} → ${Probe.wakeOf(s.bedMin, s.durMin)}",
                style = micro(value, 10),
                modifier = GlanceModifier.clickable(sheet),
            )
            Spacer(GlanceModifier.width(8.dp))
            Box(
                GlanceModifier.size(22.dp).clickable(actionRunCallback<ConfirmSleepAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = micro(value, 11))
            }
        } else {
            Text(
                "--:-- → --:--",
                style = micro(INK_DIM, 10),
                modifier = GlanceModifier.clickable(sheet),
            )
        }
    }
}

@Composable
private fun ScreenRow(s: ProbeState, label: ColorProvider, value: ColorProvider) {
    Row(GlanceModifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Probe.tracked("SCREEN"), style = micro(label, 9), maxLines = 1, modifier = GlanceModifier.width(76.dp))
        Spacer(GlanceModifier.defaultWeight())
        val text = when (s.underBudget) {
            true -> "UNDER ✓" + if (s.budgetIsAuto) " · AUTO" else ""
            false -> "OVER ·" + if (s.budgetIsAuto) " · AUTO" else ""
            null -> "BY HAND —"
        }
        Text(
            Probe.tracked(text),
            style = micro(if (s.underBudget != null) value else INK_DIM, 9),
            modifier = GlanceModifier.clickable(actionRunCallback<ToggleScreenAction>()),
        )
    }
}

@Composable
private fun SealRow(s: ProbeState, early: Boolean) {
    Rule()
    Spacer(GlanceModifier.height(3.dp))
    if (early && !s.sealable) {
        Text(
            Probe.tracked("READINGS OPEN"),
            style = micro(INK_FAINT, 9),
            modifier = GlanceModifier.fillMaxWidth(),
        )
        return
    }
    Box(
        GlanceModifier.fillMaxWidth().height(18.dp)
            .clickable(actionRunCallback<SealDayAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Probe.tracked("[ SEAL DAY ]"),
            style = micro(if (s.sealable) INK else INK_FAINT, 10),
        )
    }
}

const val SLEEP_SHEET_ACTION = "app.figly.SLEEP_SHEET"
