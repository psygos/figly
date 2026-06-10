package app.figly.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.figly.core.CellType
import app.figly.probe.ProbeWidgetReceiver
import app.figly.ui.Ink
import app.figly.ui.Label
import app.figly.ui.Micro

/**
 * The Key — how to read a fig. One screen, one labeled specimen.
 * This is also the onboarding, shown once after setup.
 */
@Composable
fun KeyScreen(firstRun: Boolean, done: () -> Unit) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Ink.s4),
    ) {
        if (!firstRun) {
            Micro("BACK", Modifier.clickable(onClick = done).padding(Ink.s2))
            Spacer(Modifier.height(Ink.s4))
        } else {
            Spacer(Modifier.height(Ink.s8))
            Label("figly", Ink.display(28.sp, Ink.ink))
            Spacer(Modifier.height(Ink.s3))
            Label(
                "one fig grows each week. its shape is a record\nof how the week was lived. nothing more.",
                Ink.data(12.sp, Ink.inkDim),
            )
            Spacer(Modifier.height(Ink.s6))
        }

        Micro("HOW TO READ A FIG", color = Ink.inkDim)
        Spacer(Modifier.height(Ink.s4))

        KeyDiagram()

        Spacer(Modifier.height(Ink.s6))
        Micro(
            "THE FIG NEVER JUDGES. IT ONLY RECORDS.",
            color = Ink.inkFaint,
            size = 9.sp,
        )

        if (firstRun) {
            Spacer(Modifier.height(Ink.s8))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.hairline))
            Spacer(Modifier.height(Ink.s6))
            Label(
                "loom is on the back of your phone.\nface down, the fig is awake.",
                Ink.data(12.sp, Ink.inkDim),
            )
            Spacer(Modifier.height(Ink.s3))
            Micro(
                "OPEN GLYPH TOYS",
                Modifier.clickable { openGlyphToys(context) }.padding(Ink.s2),
                color = Ink.ink,
            )
            Spacer(Modifier.height(Ink.s4))
            Label(
                "probe lives on your home screen.\nit asks five questions a day.",
                Ink.data(12.sp, Ink.inkDim),
            )
            Spacer(Modifier.height(Ink.s3))
            Micro(
                "PLACE PROBE",
                Modifier.clickable { requestProbePin(context) }.padding(Ink.s2),
                color = Ink.ink,
            )
            Spacer(Modifier.height(Ink.s8))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Ink.plate)
                    .clickable(onClick = done)
                    .padding(vertical = Ink.s3),
                contentAlignment = Alignment.Center,
            ) {
                Micro("BEGIN", color = Ink.ink, size = 11.sp)
            }
            Spacer(Modifier.height(Ink.s8))
        }
    }
}

private data class KeyRow(
    val cellCol: Int,
    val cellRow: Int,
    val title: String,
    val sub: String,
)

/**
 * A small synthetic specimen with a callout hairline from each feature to
 * its meaning. Hand-authored; not a grown fig.
 */
@Composable
private fun KeyDiagram() {
    // The teaching specimen, on a 7-wide × 9-tall mini grid.
    val cells = listOf(
        Triple(3, 8, CellType.WOOD),  // seed
        Triple(3, 7, CellType.WOOD),
        Triple(3, 6, CellType.WOOD),
        Triple(2, 6, CellType.LEAF),
        Triple(4, 6, CellType.LEAF),
        Triple(4, 5, CellType.WOOD),
        Triple(4, 4, CellType.WOOD),
        Triple(5, 5, CellType.THORN),
        Triple(4, 3, CellType.DRUPE),
        Triple(3, 2, CellType.SCAR),
    )
    val rows = listOf(
        KeyRow(3, 6, "INTERNODE — DAY RATING", "MORE SEGMENTS, BETTER DAY (1–5)"),
        KeyRow(4, 6, "LEAF — SLEEP", "PAIR ≥ 7H · ONE 5–7H · BARE BELOW"),
        KeyRow(4, 4, "BEND — BEDTIME", "EARLY NIGHTS CLIMB · LATE NIGHTS DROOP"),
        KeyRow(5, 5, "THORN — EFFORT", "GROWN AT 4 AND ABOVE (1–5)"),
        KeyRow(4, 3, "DRUPE — SCREEN", "UNDER BUDGET BEARS THE FRUIT"),
        KeyRow(3, 2, "SCAR — MISSED DAY", "HONEST GAPS, STRAIGHT UP"),
    )

    val density = LocalDensity.current
    val rowH = 54.dp
    val topPad = 8.dp
    val gridLeft = 18.dp
    val gridTop = 26.dp
    val pitch = 26.dp
    val labelX = 200.dp

    Box(Modifier.fillMaxWidth().height(topPad + rowH * rows.size)) {
        Canvas(Modifier.fillMaxSize()) {
            val px = with(density) { pitch.toPx() }
            val gx = with(density) { gridLeft.toPx() }
            val gy = with(density) { gridTop.toPx() }
            val lx = with(density) { labelX.toPx() }
            val rh = with(density) { rowH.toPx() }
            val tp = with(density) { topPad.toPx() }

            fun cx(c: Int) = gx + c * px
            fun cy(r: Int) = gy + r * px

            // Soil under the specimen.
            for (c in 1..5) {
                drawCircle(Ink.inkFaint.copy(alpha = 0.9f), px * 0.13f, Offset(cx(c), cy(9)))
            }
            // The specimen.
            for ((c, r, t) in cells) {
                val color = when (t) {
                    CellType.SCAR -> Ink.inkFaint
                    CellType.LEAF -> Ink.ink.copy(alpha = 0.8f)
                    else -> Ink.ink
                }
                val radius = when (t) {
                    CellType.WOOD -> px * 0.22f
                    CellType.LEAF -> px * 0.18f
                    CellType.THORN -> px * 0.13f
                    CellType.SCAR -> px * 0.18f
                    CellType.DRUPE -> px * 0.24f
                }
                drawCircle(color, radius, Offset(cx(c), cy(r)))
            }
            // Callout hairlines: cell → elbow → label row.
            rows.forEachIndexed { i, row ->
                val from = Offset(cx(row.cellCol) + px * 0.35f, cy(row.cellRow))
                val rowY = tp + i * rh + rh * 0.32f
                val elbowX = lx - 14.dp.toPx()
                drawLine(Ink.inkFaint, from, Offset(elbowX, rowY), 1f)
                drawLine(Ink.inkFaint, Offset(elbowX, rowY), Offset(lx - 6.dp.toPx(), rowY), 1f)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = labelX, top = topPad)) {
            rows.forEach { row ->
                Column(Modifier.height(rowH)) {
                    Micro(row.title, color = Ink.ink, size = 9.sp)
                    Spacer(Modifier.height(2.dp))
                    Micro(row.sub, color = Ink.inkDim, size = 8.sp)
                }
            }
        }
    }
}

/** Deep-link to the system's Glyph Toys manager; quiet fallback copy. */
fun openGlyphToys(context: android.content.Context): Boolean = runCatching {
    context.startActivity(
        Intent().setComponent(
            ComponentName(
                "com.nothing.thirdparty",
                "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
            ),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
}.isSuccess

fun requestProbePin(context: android.content.Context): Boolean = runCatching {
    val awm = context.getSystemService(AppWidgetManager::class.java)
    awm.requestPinAppWidget(
        ComponentName(context, ProbeWidgetReceiver::class.java),
        null,
        null,
    )
}.getOrDefault(false)
