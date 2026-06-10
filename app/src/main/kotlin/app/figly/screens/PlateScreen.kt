package app.figly.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.figly.FiglyEasing
import app.figly.core.CellType
import app.figly.core.Fig
import app.figly.core.FigCell
import app.figly.core.PlateLabel
import app.figly.core.WeekKeys
import app.figly.data.DayReadingEntity
import app.figly.data.FigRepository
import app.figly.data.WeekEntity
import app.figly.data.statsFromJson
import app.figly.export.PlateExport
import app.figly.ui.Ink
import app.figly.ui.Label
import app.figly.ui.Micro
import app.figly.ui.PlateDots
import app.figly.ui.plateDotsAspect
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Plate — the full specimen. Indistinguishable in style from the
 * reference plate: dot field upper two-thirds, label block lower-left,
 * corner ticks. Tap a node and the label's last line becomes that day's
 * annotation; tap elsewhere and it reverts.
 */
@Composable
fun PlateScreen(isoWeek: String, back: () -> Unit, openKey: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FigRepository.get(context) }
    val scope = rememberCoroutineScope()

    val loaded by produceState<Pair<WeekEntity, List<DayReadingEntity>>?>(null, isoWeek) {
        val w = repo.pressedWeek(isoWeek) ?: return@produceState
        value = w to repo.dayRows(isoWeek)
    }
    val (week, days) = loaded ?: return
    val fig = remember(week) { repo.figOf(week) }
    val label = remember(week) {
        PlateLabel(
            figId = WeekKeys.figId(week.isoWeek),
            dateRange = WeekKeys.dateRangeLabel(week.isoWeek),
            collected = week.collected,
            seasonName = week.seasonName,
            seasonHex = week.seasonTint,
            stats = statsFromJson(week.statsJson),
            plateNumber = WeekKeys.plateNumber(week.isoWeek),
        )
    }

    var annotation by remember { mutableStateOf<FigCell?>(null) }
    var more by remember { mutableStateOf(false) }
    var burning by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<String?>(null) }

    val seasonColor = Color(android.graphics.Color.parseColor(week.seasonTint))

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Ink.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Micro("BACK", Modifier.clickable(onClick = back).padding(Ink.s2))
            Spacer(Modifier.weight(1f))
            if (more) {
                if (burning) {
                    Micro("BURN THIS SPECIMEN?", color = Ink.inkDim, size = 9.sp)
                    Spacer(Modifier.width(Ink.s3))
                    Micro(
                        "YES",
                        Modifier.clickable {
                            scope.launch {
                                repo.burnSpecimen(week.isoWeek)
                                back()
                            }
                        }.padding(Ink.s2),
                        color = Ink.ink,
                    )
                    Micro(
                        "KEEP",
                        Modifier.clickable { burning = false; more = false }.padding(Ink.s2),
                        color = Ink.inkDim,
                    )
                } else {
                    Micro(
                        "EXPORT",
                        Modifier.clickable {
                            scope.launch {
                                exported = PlateExport.exportBoth(context, fig, week, label)
                            }
                        }.padding(Ink.s2),
                    )
                    Spacer(Modifier.width(Ink.s2))
                    Micro("BURN", Modifier.clickable { burning = true }.padding(Ink.s2))
                    Spacer(Modifier.width(Ink.s2))
                    Micro("THE KEY", Modifier.clickable(onClick = openKey).padding(Ink.s2))
                }
            } else {
                Micro("MORE", Modifier.clickable { more = true }.padding(Ink.s2))
            }
        }

        Spacer(Modifier.height(Ink.s3))

        // The plate itself.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(Ink.ground)
                .border(1.dp, Ink.hairline)
                .background(Ink.plate)
                .padding(Ink.s6),
        ) {
            CornerTicks {
                PlateDots(
                    fig = fig,
                    seed = week.seed.toUInt(),
                    season = app.figly.core.Season.valueOf(week.seasonName),
                    modifier = Modifier.fillMaxWidth().aspectRatio(plateDotsAspect()),
                    onCellTap = { annotation = it },
                )
            }

            Spacer(Modifier.height(Ink.s8))

            // Label block — the reference plate's typography.
            Label(label.figId, Ink.display(26.sp, Ink.ink, tracking = 0.12.em))
            Spacer(Modifier.height(Ink.s1))
            Box(Modifier.width(190.dp).height(2.dp).background(seasonColor))
            Spacer(Modifier.height(Ink.s4))
            Label(label.collectedLine, Ink.data(12.sp, Ink.inkDim))
            Spacer(Modifier.height(Ink.s2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(18.dp).height(3.dp).background(seasonColor))
                Spacer(Modifier.width(Ink.s2))
                Label(label.seasonLine, Ink.data(12.sp, Ink.inkDim))
            }
            Spacer(Modifier.height(Ink.s3))

            // The last line: counts, or a tapped node's day annotation.
            AnimatedContent(
                targetState = annotation,
                transitionSpec = {
                    fadeIn(tween(120, easing = FiglyEasing))
                        .togetherWith(fadeOut(tween(120, easing = FiglyEasing)))
                },
                label = "annotation",
            ) { cell ->
                if (cell == null) {
                    Label(label.countsLine, Ink.data(12.sp, Ink.inkDim))
                } else {
                    Label(
                        annotate(cell, days),
                        Ink.data(12.sp, Ink.ink),
                    )
                }
            }

            Spacer(Modifier.height(Ink.s6))
            Row(Modifier.fillMaxWidth()) {
                exported?.let { Micro(it, color = Ink.inkFaint, size = 8.sp) }
                Spacer(Modifier.weight(1f))
                Micro(label.footer, color = Ink.inkDim, size = 9.sp)
            }
        }
    }
}

/** `THU · SLEPT 01:12 · 5H40 · MOOD 4 · EFFORT 2 · DRUPE` */
private fun annotate(cell: FigCell, days: List<DayReadingEntity>): String {
    if (cell.day == 0) return "SEED · MONDAY 00:00"
    val name = WeekKeys.DAY_NAMES[cell.day - 1]
    val row = days.firstOrNull { it.dayIndex == cell.day }
        ?: return "$name · UNREAD"
    if (row.missed) return "$name · MISSED — SCAR"

    val bedAbs = (12 * 60 + row.bedMinutesAfterNoon) % (24 * 60)
    val bed = "%02d:%02d".format(Locale.ROOT, bedAbs / 60, bedAbs % 60)
    val dur = "${row.durationMin / 60}H%02d".format(Locale.ROOT, row.durationMin % 60)
    val marks = buildList {
        if (cell.type == CellType.DRUPE || row.underBudget) add("DRUPE")
        if (row.effort >= 4) add("THORN")
    }.joinToString(" · ")

    return listOf(
        name,
        "SLEPT $bed",
        dur,
        "MOOD ${row.mood}",
        "EFFORT ${row.effort}",
    ).joinToString(" · ") + if (marks.isEmpty()) "" else " · $marks"
}

@Composable
private fun CornerTicks(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        content()
        val tick = Ink.inkDim
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio(plateDotsAspect())) {
            val a = 12f * (size.width / 760f)
            val w = size.width
            val h = size.height
            val stroke = 1f
            // ⌐ ¬ corners, hairline.
            drawLine(tick, androidx.compose.ui.geometry.Offset(0f, a), androidx.compose.ui.geometry.Offset(0f, 0f), stroke)
            drawLine(tick, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(a, 0f), stroke)
            drawLine(tick, androidx.compose.ui.geometry.Offset(w - a, 0f), androidx.compose.ui.geometry.Offset(w, 0f), stroke)
            drawLine(tick, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, a), stroke)
            drawLine(tick, androidx.compose.ui.geometry.Offset(0f, h - a), androidx.compose.ui.geometry.Offset(0f, h), stroke)
            drawLine(tick, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(a, h), stroke)
            drawLine(tick, androidx.compose.ui.geometry.Offset(w - a, h), androidx.compose.ui.geometry.Offset(w, h), stroke)
            drawLine(tick, androidx.compose.ui.geometry.Offset(w, h - a), androidx.compose.ui.geometry.Offset(w, h), stroke)
        }
    }
}
