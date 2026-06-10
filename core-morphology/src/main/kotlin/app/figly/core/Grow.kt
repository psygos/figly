package app.figly.core

/**
 * The morphology grammar. The fig is a pure deterministic function:
 *
 *     fig = grow(weekSeed, days)
 *
 * Same data → same fig, always. Growth is append-only day by day, so a
 * partially-lived week renders identically to the same days replayed at
 * week's end. Each day consumes its randomness in a fixed order (side
 * first, then one draw per candidate considered), which keeps the stream
 * aligned however the week unfolds.
 */
object Grow {

    private data class Move(val dc: Int, val dr: Int)

    /**
     * Grow a fig.
     *
     * @param seed  weekSeed(isoWeekKey, installSalt)
     * @param days  the week so far — at most 7 slots, Monday first.
     *              Days not yet lived are simply absent.
     */
    fun grow(seed: UInt, days: List<DaySlot>): Fig {
        require(days.size <= 7) { "a week holds seven days" }
        val rng = Mulberry32(seed)
        val cells = LinkedHashMap<Int, FigCell>()

        fun occupied(p: Pos) = cells.containsKey(p.key)
        fun place(p: Pos, type: CellType, day: Int) {
            cells[p.key] = FigCell(p, type, day)
        }
        fun free(p: Pos) = Matrix.inMask(p.row, p.col) && !occupied(p) && !Matrix.isSoil(p.row, p.col)
        fun neighbors8(p: Pos): Int {
            var n = 0
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                if (occupied(Pos(p.row + dr, p.col + dc))) n++
            }
            return n
        }

        // The seed, planted Monday 00:00 local. It is the base of the trunk.
        var tip = Pos(Matrix.SEED_ROW, Matrix.SEED_COL)
        place(tip, CellType.WOOD, day = 0)
        // Structural cells in placement order — the path a relocating bud
        // walks back down when the tip is jammed.
        val woodPath = ArrayList<Pos>(32).also { it += tip }

        fun lacy(p: Pos) = free(p) && neighbors8(p) <= 2
        fun hasRoom(p: Pos, side: Int): Boolean =
            lacy(Pos(p.row - 1, p.col)) ||
                lacy(Pos(p.row - 1, p.col + side)) ||
                lacy(Pos(p.row, p.col + side)) ||
                lacy(Pos(p.row, p.col - side))

        // Axillary budding: a day whose first step finds the tip shaded
        // resumes from the most recent structural node with room. A plant
        // does not waste a day because its tip is jammed; mid-day jams
        // still stop early — cramped weeks make cramped figs.
        fun anchorFor(side: Int): Pos? {
            if (hasRoom(tip, side)) return tip
            for (i in woodPath.indices.reversed()) {
                if (hasRoom(woodPath[i], side)) return woodPath[i]
            }
            return null
        }

        days.forEachIndexed { index, slot ->
            val day = index + 1
            // One side per day, chosen once and persisted — drawn for missed
            // days too, so the stream never shifts.
            val side = if (rng.next() < 0.5) -1 else +1

            when (slot) {
                is DaySlot.Missed -> {
                    // One dim cell straight up; no ornaments. Honest gaps —
                    // never lost: a jammed tip relocates like any bud.
                    val from = anchorFor(side) ?: tip
                    val scar = listOf(
                        Pos(from.row - 1, from.col),
                        Pos(from.row - 1, from.col + side),
                        Pos(from.row - 1, from.col - side),
                    ).firstOrNull { free(it) }
                    if (scar != null) {
                        place(scar, CellType.SCAR, day)
                        tip = scar // the record continues from the gap
                    }
                }

                is DaySlot.Sealed -> {
                    val r = slot.reading
                    val late = lateness(r.bedMinutesAfterNoon) / 270.0
                    // Early nights climb, late nights droop sideways. Up is
                    // tempered against diagonal so vigorous weeks zigzag
                    // instead of arrowing — a trunk that wanders leaves room
                    // for its own leaves. The diagonal weakens with lateness
                    // too: drooping means drooping.
                    val upW = lerp(2.1, 0.25, late)
                    val diagW = lerp(1.15, 0.6, late)
                    val latW = lerp(0.15, 2.0, late)

                    val placedToday = ArrayList<Pos>(4)
                    val movesToday = ArrayList<Move>(4)
                    var steps = internodeLength(r.mood)

                    anchorFor(side)?.let { tip = it }
                    while (steps > 0) {
                        val candidates = mutableListOf(
                            Move(0, -1) to upW,
                            Move(side, -1) to diagW,
                            Move(side, 0) to latW,
                            // Escape valve: a faint opposite lateral, so a
                            // crown that has reached the sky spreads where
                            // the light is instead of strangling itself.
                            Move(-side, 0) to latW * 0.35,
                        )
                        var move: Move? = null
                        while (candidates.isNotEmpty()) {
                            val picked = pickWeighted(rng, candidates)
                            val next = Pos(tip.row + picked.dr, tip.col + picked.dc)
                            if (free(next) && neighbors8(next) <= 2) {
                                move = picked
                                break
                            }
                            candidates.removeAll { it.first == picked }
                        }
                        if (move == null) break // cramped weeks make cramped figs; keep it
                        val next = Pos(tip.row + move.dr, tip.col + move.dc)
                        place(next, CellType.WOOD, day)
                        placedToday += next
                        movesToday += move
                        woodPath += next
                        tip = next
                        steps--
                    }

                    if (placedToday.isNotEmpty()) {
                        // Leaves sit perpendicular to the mid-node's travel.
                        val midIndex = (placedToday.size + 1) / 2 - 1
                        val mid = placedToday[midIndex]
                        val midTravel = movesToday[midIndex]
                        val perpA = Move(-midTravel.dr, midTravel.dc)
                        val perpB = Move(midTravel.dr, -midTravel.dc)
                        // The day-side leaf: the perpendicular that leans with
                        // the day's side; for lateral travel, the one that
                        // reaches up — leaves reach for light.
                        val (daySide, offSide) =
                            if (perpA.dc * side > perpB.dc * side) perpA to perpB
                            else if (perpA.dc == perpB.dc) {
                                if (perpA.dr < perpB.dr) perpA to perpB else perpB to perpA
                            } else perpB to perpA

                        // The ≤2-neighbor law applies to wood only — ornaments
                        // merely skip when blocked.
                        fun placeLeaf(m: Move) {
                            val p = Pos(mid.row + m.dr, mid.col + m.dc)
                            if (free(p)) place(p, CellType.LEAF, day)
                        }
                        when (leafHabit(r.durationMin)) {
                            LeafHabit.PAIR -> { placeLeaf(daySide); placeLeaf(offSide) }
                            LeafHabit.SINGLE -> placeLeaf(daySide)
                            LeafHabit.BARE -> {}
                        }

                        val last = placedToday.last()
                        if (growsThorn(r.effort)) {
                            // One spike off the day's last node, pointing
                            // down-outward — falling back outward, then down,
                            // so real exertion is never silently erased.
                            val thorn = listOf(
                                Pos(last.row + 1, last.col - side),
                                Pos(last.row, last.col - side),
                                Pos(last.row + 1, last.col),
                            ).firstOrNull { free(it) }
                            if (thorn != null) place(thorn, CellType.THORN, day)
                        }
                        if (r.underBudget) {
                            // The day's terminal node bears the discipline fruit.
                            cells[last.key] = FigCell(last, CellType.DRUPE, day)
                        }
                    }
                }
            }
        }

        val all = cells.values.toList()
        val sealedMoods = days.filterIsInstance<DaySlot.Sealed>().map { it.reading.mood }
        val meanMood = if (sealedMoods.isEmpty()) null else sealedMoods.average()
        val stats = FigStats(
            cells = all.size,
            wood = all.count { it.type == CellType.WOOD },
            leaves = all.count { it.type == CellType.LEAF },
            thorns = all.count { it.type == CellType.THORN },
            drupes = all.count { it.type == CellType.DRUPE },
            scars = all.count { it.type == CellType.SCAR },
            heightRow = all.minOf { it.pos.row },
            meanMood = meanMood,
            season = Season.fromMeanMood(meanMood),
        )
        return Fig(cells = all, tip = tip, daysGrown = days.size, stats = stats)
    }

    private fun pickWeighted(rng: Mulberry32, options: List<Pair<Move, Double>>): Move {
        val total = options.sumOf { it.second }
        var roll = rng.next() * total
        for ((move, w) in options) {
            roll -= w
            if (roll < 0) return move
        }
        return options.last().first
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
