package app.figly.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.figly.core.CellType
import app.figly.core.Fig
import app.figly.core.Stamp
import app.figly.core.WeekKeys
import app.figly.data.FigRepository
import app.figly.data.PlateThumbs
import app.figly.data.WeekEntity
import app.figly.glyph.LoomFrames
import app.figly.ui.Ink
import app.figly.ui.Label
import app.figly.ui.Micro
import app.figly.ui.PlateDots
import app.figly.ui.plateDotsAspect
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This Week, then the drawer — one continuous vertical. The living plate
 * is the only place in Herbarium where anything moves: the awaiting tip.
 */
@Composable
fun HomeScreen(
    openPlate: (String) -> Unit,
    openSettings: () -> Unit,
    openKey: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { FigRepository.get(context) }

    val state by produceState<HomeState?>(initialValue = null) {
        val today = LocalDate.now()
        val weekKey = WeekKeys.isoWeekKey(today)
        val seed = repo.seedFor(weekKey)
        val fig = repo.liveFig()
        val todaySealed = repo.todayRow() != null
        val slots = repo.daySlots(weekKey, today)
        val dayMarks = withContext(Dispatchers.Default) {
            (1..7).map { d ->
                if (d <= slots.size) Stamp.sealedStamp(seed, slots.take(d)) else null
            }
        }
        // The drawer stays alive: every press repaints it.
        repo.observePressedWeeks().collect { weeks ->
            value = HomeState(
                weekKey = weekKey,
                seed = seed,
                fig = fig,
                dayIndex = WeekKeys.dayIndex(today),
                todaySealed = todaySealed,
                weeks = weeks,
                dayMarks = dayMarks,
            )
        }
    }

    val s = state ?: return

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(Ink.ground).statusBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(Ink.s2),
        verticalArrangement = Arrangement.spacedBy(Ink.s2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Ink.s4),
    ) {
        item(span = { GridItemSpan(3) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("figly", Ink.display(20.sp, Ink.ink))
                    Spacer(Modifier.weight(1f))
                    Micro(
                        "THE KEY",
                        Modifier.clickable(onClick = openKey).padding(Ink.s2),
                        color = Ink.inkDim,
                    )
                    Spacer(Modifier.size(Ink.s3))
                    Micro(
                        "SETTINGS",
                        Modifier.clickable(onClick = openSettings).padding(Ink.s2),
                        color = Ink.inkDim,
                    )
                }
                Spacer(Modifier.height(Ink.s6))
                ThisWeek(s)
                Spacer(Modifier.height(Ink.s8))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Micro("THE DRAWER", color = Ink.inkDim)
                    Spacer(Modifier.size(Ink.s3))
                    Box(Modifier.weight(1f).height(1.dp).background(Ink.hairline))
                }
                Spacer(Modifier.height(Ink.s3))
            }
        }

        if (s.weeks.isEmpty()) {
            item(span = { GridItemSpan(3) }) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = Ink.s12),
                    contentAlignment = Alignment.Center,
                ) {
                    Micro(
                        "THE DRAWER IS EMPTY. YOUR FIRST FIG IS GROWING.",
                        color = Ink.inkFaint,
                        size = 9.sp,
                    )
                }
            }
        } else {
            items(s.weeks, key = { it.isoWeek }) { week ->
                DrawerThumb(week) { openPlate(week.isoWeek) }
            }
        }
    }
}

private data class HomeState(
    val weekKey: String,
    val seed: UInt,
    val fig: Fig,
    val dayIndex: Int,
    val todaySealed: Boolean,
    val weeks: List<WeekEntity>,
    val dayMarks: List<List<Stamp.Dot>?>,
)

@Composable
private fun ThisWeek(s: HomeState) {
    val awaiting = if (!s.todaySealed) LoomFrames.awaitingPos(s.fig) else null
    Column(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = describeFig(s.weekKey, s.fig)
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Ink.plate)
                .padding(Ink.s4),
        ) {
            PlateDots(
                fig = s.fig,
                seed = s.seed,
                season = s.fig.stats.season,
                modifier = Modifier.fillMaxWidth().aspectRatio(plateDotsAspect()),
                livingTip = awaiting,
            )
        }
        Spacer(Modifier.height(Ink.s3))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Ink.s2),
        ) {
            for (d in 0 until 7) {
                DayMark(
                    mark = s.dayMarks.getOrNull(d),
                    name = WeekKeys.DAY_NAMES[d],
                    isToday = d + 1 == s.dayIndex,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Ink.s3))
        Micro(
            "DAY ${s.dayIndex} OF 7 · ${s.fig.stats.cells} CELLS",
            color = Ink.inkDim,
        )
    }
}

@Composable
private fun DayMark(
    mark: List<Stamp.Dot>?,
    name: String,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (mark != null && mark.isNotEmpty()) {
                Canvas(Modifier.size(20.dp)) {
                    val cell = size.width / Stamp.SIZE
                    for (dot in mark) {
                        val color = when (dot.type) {
                            CellType.SCAR -> Ink.inkFaint
                            CellType.LEAF -> Ink.ink.copy(alpha = 0.8f)
                            else -> Ink.ink
                        }
                        drawCircle(
                            color = color,
                            radius = cell * if (dot.type == CellType.DRUPE) 0.34f else 0.24f,
                            center = androidx.compose.ui.geometry.Offset(
                                (dot.col + 0.5f) * cell,
                                (dot.row + 0.5f) * cell,
                            ),
                        )
                    }
                }
            } else {
                Label("·", Ink.data(12.sp, if (isToday) Ink.ink else Ink.inkFaint))
            }
        }
        Micro(name, color = if (isToday) Ink.inkDim else Ink.inkFaint, size = 8.sp)
    }
}

@Composable
private fun DrawerThumb(week: WeekEntity, onTap: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(initialValue = null, week.isoWeek) {
        value = withContext(Dispatchers.IO) {
            val f = PlateThumbs.file(context, week.isoWeek)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else {
                // Re-press a missing thumbnail from the record.
                val repo = FigRepository.get(context)
                val fig = repo.figOf(week)
                PlateThumbs.write(context, week.isoWeek, fig, week.seed.toUInt())
                BitmapFactory.decodeFile(f.absolutePath)
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(456f / 624f)
            .background(Ink.plate)
            .clickable(onClick = onTap)
            .semantics { contentDescription = describeWeek(week) },
    ) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun describeFig(weekKey: String, fig: Fig): String {
    val s = fig.stats
    return "week ${WeekKeys.plateNumber(weekKey)}, ${s.season.name.lowercase(Locale.ROOT)}, " +
        "${s.cells} cells, ${s.leaves} leaves, ${s.thorns} thorns, ${s.drupes} drupes, " +
        if (s.scars == 1) "one scar" else "${s.scars} scars"
}

private fun describeWeek(week: WeekEntity): String =
    "${week.isoWeek}, ${week.seasonName.lowercase(Locale.ROOT)} — tap to open the plate"
