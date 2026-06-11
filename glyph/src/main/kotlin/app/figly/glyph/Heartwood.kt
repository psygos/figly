package app.figly.glyph

import app.figly.core.Matrix
import app.figly.core.Mulberry32
import app.figly.core.Pos
import app.figly.core.fnv1a32
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * HEARTWOOD — every ten minutes, a tree grows a heart and lets it go.
 *
 * A clock, not a movie: the tree is a pure function of wall time,
 * `f(epochSeconds mod 600)`. Heartbeat frames render the present moment;
 * the lively window animates it — budding cells bloom, falling leaves
 * drift, and for twenty seconds at the top of the cycle the completed
 * heart beats, but only while someone watches.
 *
 *   0–290    grow — trunk, fork, arms, then leaves bud outward
 *   290–310  the held heart, beating (lively only)
 *   310–580  leaves fall, one by one; the soil warms as they land
 *   580–595  bare branches — the armature remembers the heart
 *   595–600  everything sinks into the soil; a new seed next cycle
 */
object Heartwood {

    const val CYCLE_S = 600L
    private const val GROW_END = 290.0
    private const val HOLD_END = 310.0
    private const val FALL_END = 580.0
    private const val BARE_END = 595.0

    // ── The heart, hand-set on the 13×13 circular mask ─────────────────

    /** row to columns; lobes at the top, the point at (8,6). */
    private val HEART: List<Pos> = buildList {
        fun row(r: Int, cols: IntRange) = cols.forEach { add(Pos(r, it)) }
        row(1, 3..5); row(1, 7..9)        // lobes, dip at col 6
        row(2, 2..10)
        row(3, 2..10)
        row(4, 2..10)
        row(5, 3..9)
        row(6, 4..8)
        row(7, 5..7)
        add(Pos(8, 6))                     // the point
    }.filter { Matrix.inMask(it.row, it.col) }

    private val HEART_SET = HEART.toHashSet()

    // ── One cycle's tree ────────────────────────────────────────────────

    class Build(cycle: Long) {
        val wood: List<Pos>
        val leaves: List<Pos>
        /** leaf index → order in which it falls */
        val fallOrder: List<Int>
        /** per-leaf lateral drift while falling: -1, 0, +1 */
        val drift: List<Int>

        init {
            val rng = Mulberry32(fnv1a32("heartwood:$cycle"))

            // Trunk from the soil to the fork.
            val trunk = listOf(Pos(10, 6), Pos(9, 6), Pos(8, 6), Pos(7, 6), Pos(6, 6))

            // Two arms curve up into the lobes; small seeded wiggles keep
            // every cycle's tree its own. Cells must stay inside the heart.
            fun arm(side: Int): List<Pos> {
                val out = ArrayList<Pos>(6)
                var col = 6
                for (row in 5 downTo 1) {
                    val lean = when (row) {
                        5 -> 1
                        4 -> if (rng.next() < 0.5) 1 else 2
                        3 -> if (rng.next() < 0.5) 2 else 3
                        2 -> 3
                        else -> if (rng.next() < 0.5) 2 else 3
                    }
                    col = (6 + side * lean).coerceIn(0, 12)
                    val p = Pos(row, col)
                    if (p in HEART_SET) out += p
                }
                return out
            }

            val left = arm(-1)
            val right = arm(+1)
            // Interleave the arms so growth alternates sides.
            val arms = ArrayList<Pos>(left.size + right.size)
            for (i in 0 until maxOf(left.size, right.size)) {
                left.getOrNull(i)?.let { arms += it }
                right.getOrNull(i)?.let { arms += it }
            }
            wood = (trunk + arms).distinct()

            val woodSet = wood.toHashSet()
            val leafCells = HEART.filter { it !in woodSet }

            // Leaves bud outward from the wood: near branches first,
            // seeded noise breaking ties.
            fun nearestWood(p: Pos): Int = wood.minOf { w ->
                maxOf(kotlin.math.abs(w.row - p.row), kotlin.math.abs(w.col - p.col))
            }
            leaves = leafCells
                .map { it to (nearestWood(it) + rng.next() * 0.9) }
                .sortedBy { it.second }
                .map { it.first }

            // Autumn: higher leaves tend to let go first.
            fallOrder = leaves.indices
                .map { i -> i to (leaves[i].row * 0.22 + rng.next()) }
                .sortedBy { it.second }
                .map { it.first }

            drift = leaves.map { ((rng.next() * 3).toInt() - 1).coerceIn(-1, 1) }
        }
    }

    private var cachedCycle = -1L
    private var cachedBuild: Build? = null

    private fun buildFor(cycle: Long): Build {
        val c = cachedBuild
        if (c != null && cachedCycle == cycle) return c
        return Build(cycle).also { cachedBuild = it; cachedCycle = cycle }
    }

    // ── Rendering ───────────────────────────────────────────────────────

    /**
     * Render the present moment into [frames].
     * [epochMs] places us in the cycle; [animMs] drives lively motion.
     */
    fun render(frames: LoomFrames, epochMs: Long, lively: Boolean, animMs: Long) {
        val cycle = epochMs / 1000 / CYCLE_S
        val s = (epochMs / 1000.0) % CYCLE_S
        val b = buildFor(cycle)

        frames.clearAll()

        // Dissolve: the whole tree sinks; soil takes it back.
        val dissolve = if (s >= BARE_END) ((s - BARE_END) / (CYCLE_S - BARE_END)).coerceIn(0.0, 1.0) else 0.0
        val global = 1.0 - dissolve

        // Autumn ground: warms as leaves land, cools at the end.
        val fallen = fallenCount(b, s)
        val soil = (LoomFrames.SOIL + 26.0 * fallen / b.leaves.size.coerceAtLeast(1)) * global
        frames.soil(soil.toInt().coerceAtLeast(8))

        // Wood.
        val woodUp = woodGrown(b, s)
        val breath = if (lively) 1.0 + 0.035 * sin(animMs / 2900.0) else 1.0
        for (i in 0 until woodUp) {
            val v = LoomFrames.WOOD * breath * global
            frames.set(frames.mid, b.wood[i], bloomed(v, growStartOf(b, i, wood = true), s, lively))
        }

        // Leaves: present = grown and not yet fallen.
        val leafUp = leavesGrown(b, s)
        val beat = if (lively && s in GROW_END..HOLD_END) heartbeat(animMs) else 1.0
        for (i in 0 until leafUp) {
            val fallAt = fallStartOf(b, i)
            if (s >= fallAt) {
                // Falling or gone — drawn only while watched, mid-flight.
                if (lively && s < fallAt + FLIGHT_S) {
                    val p = (s - fallAt) / FLIGHT_S
                    val leaf = b.leaves[i]
                    val row = leaf.row + (p * 3.5).toInt()
                    val col = leaf.col + if (p > 0.45) b.drift[i] else 0
                    if (Matrix.inMask(row, col) && row <= Matrix.SOIL_ROW) {
                        frames.set(frames.top, Pos(row, col), (LoomFrames.LEAF * (1.0 - p) * global).toInt())
                    }
                }
                continue
            }
            val shimmer = if (lively) 8.0 * sin(animMs / 700.0 + (leaf(b, i))) else 0.0
            val v = (LoomFrames.LEAF + shimmer) * beat * global
            frames.set(frames.mid, b.leaves[i], bloomed(v, growStartOf(b, i, wood = false), s, lively))
        }
    }

    private const val FLIGHT_S = 1.6

    private fun leaf(b: Build, i: Int): Double = (b.leaves[i].row * 7 + b.leaves[i].col) * 0.7

    // Growth schedule: wood over the first 36% of grow, leaves from 22% on.
    private fun growStartOf(b: Build, i: Int, wood: Boolean): Double =
        if (wood) (i.toDouble() / b.wood.size) * (GROW_END * 0.36)
        else GROW_END * 0.22 + (i.toDouble() / b.leaves.size) * (GROW_END * 0.78 - 6)

    private fun woodGrown(b: Build, s: Double): Int =
        b.wood.indices.count { growStartOf(b, it, wood = true) <= s }

    private fun leavesGrown(b: Build, s: Double): Int =
        b.leaves.indices.count { growStartOf(b, it, wood = false) <= s }

    private fun fallStartOf(b: Build, leafIndex: Int): Double {
        val slot = b.fallOrder.indexOf(leafIndex)
        return HOLD_END + (slot.toDouble() / b.fallOrder.size) * (FALL_END - HOLD_END)
    }

    private fun fallenCount(b: Build, s: Double): Int =
        b.leaves.indices.count { s >= fallStartOf(b, it) + FLIGHT_S }

    /** A new cell blooms in over its first moments — while watched. */
    private fun bloomed(base: Double, bornAt: Double, s: Double, lively: Boolean): Int {
        if (!lively) return base.toInt()
        val age = s - bornAt
        return when {
            age < 0 -> 0
            age < 0.8 -> (255 * (age / 0.8)).toInt()
            age < 1.6 -> (255 + (base - 255) * ((age - 0.8) / 0.8)).toInt()
            else -> base.toInt()
        }.coerceIn(0, 255)
    }

    /** Lub-dub: two soft pulses on a 1.6 s period. ±8 %, watched only. */
    private fun heartbeat(animMs: Long): Double {
        val t = (animMs % 1600) / 1000.0
        fun pulse(at: Double, w: Double, a: Double): Double {
            val d = t - at
            return a * exp(-(d * d) / (2 * w * w)) * sin((d / w) * PI / 2 + PI / 2).coerceAtLeast(0.0)
        }
        val lub = pulse(0.12, 0.09, 1.0)
        val dub = pulse(0.42, 0.10, 0.6)
        return 1.0 + 0.08 * (lub + dub)
    }
}
