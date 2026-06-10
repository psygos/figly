package app.figly.data

import app.figly.core.CellType
import app.figly.core.Fig
import app.figly.core.FigCell
import app.figly.core.FigStats
import app.figly.core.Pos
import app.figly.core.Season
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serialization lives here so :core-morphology stays dependency-free.
 * Cells persist in placement order — the order growth was witnessed.
 */

@Serializable
data class CellDto(val r: Int, val c: Int, val t: String, val d: Int)

@Serializable
data class StatsDto(
    val cells: Int,
    val wood: Int,
    val leaves: Int,
    val thorns: Int,
    val drupes: Int,
    val scars: Int,
    val heightRow: Int,
    val meanMood: Double?,
    val season: String,
)

private val json = Json { ignoreUnknownKeys = true }

fun Fig.cellsToJson(): String =
    json.encodeToString(cells.map { CellDto(it.pos.row, it.pos.col, it.type.name, it.day) })

fun cellsFromJson(s: String): List<FigCell> =
    json.decodeFromString<List<CellDto>>(s).map {
        FigCell(Pos(it.r, it.c), CellType.valueOf(it.t), it.d)
    }

fun FigStats.toJson(): String = json.encodeToString(
    StatsDto(cells, wood, leaves, thorns, drupes, scars, heightRow, meanMood, season.name),
)

fun statsFromJson(s: String): FigStats {
    val d = json.decodeFromString<StatsDto>(s)
    return FigStats(
        cells = d.cells, wood = d.wood, leaves = d.leaves, thorns = d.thorns,
        drupes = d.drupes, scars = d.scars, heightRow = d.heightRow,
        meanMood = d.meanMood, season = Season.valueOf(d.season),
    )
}
