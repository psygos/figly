package app.figly.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import app.figly.core.Fig
import app.figly.core.FigCell
import app.figly.core.Matrix
import app.figly.core.Pos
import app.figly.core.WeekKeys
import app.figly.data.FigRepository
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * LOOM — the living fig, on the back of the phone. Face down, the fig is
 * awake.
 *
 * The service holds no background state: on every bind and every heartbeat
 * it recomputes from Room — Schrödinger architecture, the fig doesn't
 * exist until observed. Two regimes: a lively window just after the flip
 * (≤ 8 fps: breath, shimmer, blooms, ceremony), then per-minute EVENT_AOD
 * heartbeats pushing one static frame.
 *
 * Growth must be witnessed: a sealed day blooms in on the first flip after
 * its seal, cell by cell. A pressed week holds its old fig on the matrix
 * until its ceremony has played — death is witnessed too.
 */
class LoomToyService : Service() {

    private var gm: GlyphMatrixManager? = null
    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())

    private var frames = LoomFrames()
    private var matrixLen = Matrix.SIZE

    // ── Session snapshot (rebuilt on bind and on every heartbeat) ──────
    private data class Snapshot(
        val fig: Fig,
        val weekKey: String,
        val todayIndex: Int,
        val todayResolved: Boolean,
        val witnessedThrough: Int,
        /** A pressed week whose ceremony hasn't played: its fig, replayed. */
        val ceremonyFig: Fig?,
        val ceremonyWeekKey: String?,
    )

    private var snapshot: Snapshot? = null
    private var sessionStart = 0L
    private var ceremonyStart = -1L
    private var bloomStart = -1L
    private var bloomCells: List<FigCell> = emptyList()
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            // The lively window never cuts a ritual: ceremonies and blooms
            // finish before the matrix goes still.
            val animating = snapshot?.ceremonyFig != null || bloomCells.isNotEmpty()
            if (animating || SystemClock.uptimeMillis() - sessionStart < LIVELY_MS) {
                main.postDelayed(this, FRAME_MS)
            } else {
                pushStill()
            }
        }
    }

    // ── Toy plumbing ───────────────────────────────────────────────────

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                GlyphToy.MSG_GLYPH_TOY -> {
                    when (msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                        GlyphToy.EVENT_AOD -> heartbeat()
                        else -> Unit // no glyph button on the (4a) Pro
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        init()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(ticker)
        runCatching {
            gm?.turnOff()
            gm?.unInit()
        }
        gm = null
        registered = false
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun init() {
        if (gm != null) return
        gm = GlyphMatrixManager.getInstance(applicationContext)
        gm?.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: android.content.ComponentName?) {
                runCatching {
                    gm?.register(Glyph.DEVICE_25111p)
                    registered = true
                    matrixLen = runCatching { Common.getDeviceMatrixLength() }
                        .getOrDefault(Matrix.SIZE).takeIf { it >= Matrix.SIZE } ?: Matrix.SIZE
                    if (frames.low.size != matrixLen * matrixLen) frames = LoomFrames(matrixLen)
                }
                startSession()
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                registered = false
            }
        })
    }

    // ── Session ────────────────────────────────────────────────────────

    private fun startSession() {
        scope.launch {
            val snap = loadSnapshot()
            main.post {
                snapshot = snap
                sessionStart = SystemClock.uptimeMillis()
                ceremonyStart = if (snap.ceremonyFig != null) 0L else -1L
                bloomCells =
                    if (snap.ceremonyFig == null && snap.witnessedThrough < snap.fig.daysGrown) {
                        snap.fig.cells.filter { it.day > snap.witnessedThrough }
                    } else emptyList()
                bloomStart = -1L
                main.removeCallbacks(ticker)
                main.post(ticker)
            }
        }
    }

    private fun heartbeat() {
        scope.launch {
            val snap = loadSnapshot()
            main.post {
                snapshot = snap
                pushStill()
            }
        }
    }

    private suspend fun loadSnapshot(): Snapshot {
        val repo = FigRepository.get(applicationContext)
        val now = ZonedDateTime.now()
        repo.resolveLapsedGraces(now)
        repo.pressIfDue(now)

        val pending = repo.pendingCeremony()
        val today = now.toLocalDate()
        val weekKey = WeekKeys.isoWeekKey(today)
        val fig = repo.liveFig(now)
        return Snapshot(
            fig = fig,
            weekKey = weekKey,
            todayIndex = WeekKeys.dayIndex(today),
            todayResolved = repo.todayRow(now) != null,
            witnessedThrough = repo.witnessedThrough(weekKey),
            ceremonyFig = pending?.let { repo.figOf(it) },
            ceremonyWeekKey = pending?.isoWeek,
        )
    }

    // ── Rendering ──────────────────────────────────────────────────────

    private fun tick() {
        val snap = snapshot ?: return
        val t = SystemClock.uptimeMillis() - sessionStart

        when {
            snap.ceremonyFig != null -> ceremonyFrame(snap, snap.ceremonyFig, t)
            bloomCells.isNotEmpty() -> bloomFrame(snap, t)
            else -> liveFrame(snap, t, lively = true)
        }
    }

    /** The week replayed, drained into the soil, an ember, a new seed. */
    private fun ceremonyFrame(snap: Snapshot, pressed: Fig, t: Long) {
        frames.clearAll()
        val replayEnd = 7L * REPLAY_DAY_MS
        val drainEnd = replayEnd + DRAIN_MS
        val emberEnd = drainEnd + EMBER_MS
        val sproutEnd = emberEnd + SPROUT_MS

        when {
            t < replayEnd -> {
                val day = (t / REPLAY_DAY_MS).toInt() + 1
                frames.soil()
                frames.figBody(pressed, t, lively = false, throughDay = day)
            }
            t < drainEnd -> {
                val p = (t - replayEnd).toDouble() / DRAIN_MS
                // The light drains downward: a front sweeps from the sky to
                // the soil; what it passes goes dark, the soil swells as it
                // drinks.
                val front = -1.0 + p * (Matrix.SOIL_ROW + 1)
                frames.soil((LoomFrames.SOIL + 34 * kotlin.math.sin(p * Math.PI)).toInt())
                for (cell in pressed.cells) {
                    val fade = (cell.pos.row - front).coerceIn(0.0, 1.0)
                    val natural = frames.cellBrightness(cell.type, cell.pos, t, lively = false, breath = 1.0)
                    frames.set(frames.mid, cell.pos, (natural * fade).toInt())
                }
            }
            t < emberEnd -> {
                // A single ember in the dark.
                frames.set(frames.mid, Pos(Matrix.SEED_ROW, Matrix.SEED_COL), LoomFrames.SOIL)
            }
            t < sproutEnd -> {
                val p = (t - emberEnd).toDouble() / SPROUT_MS
                frames.soil((LoomFrames.SOIL * p).toInt())
                frames.set(
                    frames.mid,
                    Pos(Matrix.SEED_ROW, Matrix.SEED_COL),
                    (LoomFrames.SEED * p).toInt().coerceAtLeast(8),
                )
            }
            else -> {
                // Ceremony complete: the new week begins.
                snap.ceremonyWeekKey?.let { key ->
                    scope.launch {
                        FigRepository.get(applicationContext).markCeremonyPlayed(key)
                    }
                }
                snapshot = snap.copy(ceremonyFig = null, ceremonyWeekKey = null)
                bloomCells = if (snap.witnessedThrough < snap.fig.daysGrown) {
                    snap.fig.cells.filter { it.day > snap.witnessedThrough }
                } else emptyList()
                bloomStart = -1L
                liveFrame(snapshot!!, t, lively = true)
                return
            }
        }
        push()
    }

    /** Sealed-but-unwitnessed days bloom in, cell by cell, 140 ms apart. */
    private fun bloomFrame(snap: Snapshot, t: Long) {
        if (bloomStart < 0) bloomStart = t
        val bt = t - bloomStart
        frames.clearAll()
        frames.soil()
        frames.figBody(snap.fig, t, lively = true, throughDay = snap.witnessedThrough)

        var allDone = true
        for ((i, cell) in bloomCells.withIndex()) {
            val start = i * BLOOM_STAGGER_MS
            val ct = bt - start
            val natural = frames.cellBrightness(cell.type, cell.pos, t, lively = true, breath = 1.0)
            when {
                ct < 0 -> allDone = false
                ct < BLOOM_RISE_MS -> {
                    allDone = false
                    frames.set(frames.top, cell.pos, (255.0 * ct / BLOOM_RISE_MS).toInt())
                }
                ct < BLOOM_RISE_MS + BLOOM_SETTLE_MS -> {
                    allDone = false
                    val p = (ct - BLOOM_RISE_MS).toDouble() / BLOOM_SETTLE_MS
                    frames.set(frames.top, cell.pos, (255 + (natural - 255) * p).toInt())
                }
                else -> frames.set(frames.mid, cell.pos, natural)
            }
        }
        push()

        if (allDone) {
            val through = snap.fig.daysGrown
            scope.launch {
                FigRepository.get(applicationContext).markWitnessed(snap.weekKey, through)
            }
            snapshot = snap.copy(witnessedThrough = through)
            bloomCells = emptyList()
        }
    }

    private fun liveFrame(snap: Snapshot, t: Long, lively: Boolean) {
        frames.clearAll()
        frames.soil()
        if (snap.fig.cells.size <= 1 && snap.fig.daysGrown == 0) {
            // Monday, bare soil and a seed.
            frames.seedOnly(t, lively)
        } else {
            frames.figBody(snap.fig, t, lively)
        }
        if (!snap.todayResolved) frames.awaitingTip(snap.fig, t, lively)
        push()
    }

    private fun pushStill() {
        main.removeCallbacks(ticker)
        val snap = snapshot ?: return
        // Heartbeats never animate; a pending ceremony keeps the pressed
        // fig on the matrix until it is witnessed on the next flip.
        frames.clearAll()
        if (snap.ceremonyFig != null) {
            frames.soil()
            frames.figBody(snap.ceremonyFig, 0L, lively = false)
        } else {
            frames.soil()
            if (snap.fig.cells.size <= 1 && snap.fig.daysGrown == 0) {
                frames.seedOnly(0L, lively = false)
            } else {
                frames.figBody(snap.fig, 0L, lively = false)
            }
            if (!snap.todayResolved) frames.awaitingTip(snap.fig, 0L, lively = false)
        }
        push()
    }

    private fun push() {
        val g = gm ?: return
        if (!registered) return
        runCatching {
            val frame = GlyphMatrixFrame.Builder()
                .addLow(frames.low)
                .addMid(frames.mid)
                .addTop(frames.top)
                .build(applicationContext)
            g.setMatrixFrame(frame.render())
        }
    }

    companion object {
        private const val FRAME_MS = 125L          // ≤ 8 fps, lively window only
        private const val LIVELY_MS = 12_000L
        private const val REPLAY_DAY_MS = 220L
        private const val DRAIN_MS = 1400L
        private const val EMBER_MS = 600L
        private const val SPROUT_MS = 500L
        private const val BLOOM_STAGGER_MS = 140L
        private const val BLOOM_RISE_MS = 250L
        private const val BLOOM_SETTLE_MS = 750L
    }
}
