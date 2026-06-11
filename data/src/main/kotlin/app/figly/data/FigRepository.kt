package app.figly.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.edit
import app.figly.core.DayReading
import app.figly.core.DaySlot
import app.figly.core.Fig
import app.figly.core.Grow
import app.figly.core.WeekKeys
import app.figly.core.weekSeed
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

/**
 * The one place all three surfaces read the week from. The live fig is
 * always recomputed — Schrödinger architecture: the fig doesn't exist
 * until observed.
 */
class FigRepository private constructor(private val context: Context) {

    val db: FiglyDb = FiglyDb.get(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("figly", Context.MODE_PRIVATE)

    // ── Identity ────────────────────────────────────────────────────────

    /** Per-install salt: figs are this phone's flora, not a global function. */
    val installSalt: String
        get() = prefs.getString(KEY_SALT, null) ?: synchronized(this) {
            prefs.getString(KEY_SALT, null) ?: UUID.randomUUID().toString().also {
                prefs.edit { putString(KEY_SALT, it) }
                prefs.edit { putString(KEY_INSTALL_WEEK, WeekKeys.isoWeekKey(LocalDate.now())) }
            }
        }

    val installWeek: String
        get() {
            installSalt // ensure initialized
            return prefs.getString(KEY_INSTALL_WEEK, null)
                ?: WeekKeys.isoWeekKey(LocalDate.now())
        }

    fun seedFor(isoWeek: String): UInt = weekSeed(isoWeek, installSalt)

    // ── Settings ────────────────────────────────────────────────────────

    var screenBudgetMin: Int
        get() = prefs.getInt(KEY_BUDGET, 180)
        set(v) = prefs.edit { putInt(KEY_BUDGET, v.coerceIn(30, 12 * 60)) }

    /** The practice the thorn marks — renameable: EFFORT, RUN, CLIMB… */
    var effortLabel: String
        get() = prefs.getString(KEY_EFFORT_LABEL, "EFFORT")!!
        set(v) = prefs.edit {
            putString(KEY_EFFORT_LABEL, v.trim().uppercase(Locale.ROOT).take(12).ifEmpty { "EFFORT" })
        }

    /** Probe asks by existing; the 21:30 reminder is default OFF. */
    var reminderOn: Boolean
        get() = prefs.getBoolean(KEY_REMINDER, false)
        set(v) = prefs.edit { putBoolean(KEY_REMINDER, v) }

    /** Onboarding: The Key, shown once after setup. */
    var keySeen: Boolean
        get() = prefs.getBoolean(KEY_KEY_SEEN, false)
        set(v) = prefs.edit { putBoolean(KEY_KEY_SEEN, v) }

    // ── Drafts: Probe's partial answers before the seal ────────────────

    fun draft(date: LocalDate): Draft = Draft(
        mood = prefs.getInt("draft:$date:mood", 0).takeIf { it in 1..5 },
        bedMin = prefs.getInt("draft:$date:bed", -1).takeIf { it >= 0 },
        durMin = prefs.getInt("draft:$date:dur", -1).takeIf { it >= 0 },
        body = prefs.getInt("draft:$date:effort", 0).takeIf { it in 1..5 },
        mind = prefs.getInt("draft:$date:mind", 0).takeIf { it in 1..5 },
        underBudget = when (prefs.getInt("draft:$date:budget", -1)) {
            1 -> true; 0 -> false; else -> null
        },
        sleepConfirmed = prefs.getBoolean("draft:$date:sleepok", false),
    )

    fun updateDraft(date: LocalDate, mutate: Draft.() -> Draft) {
        val d = draft(date).mutate()
        prefs.edit {
            putInt("draft:$date:mood", d.mood ?: 0)
            putInt("draft:$date:bed", d.bedMin ?: -1)
            putInt("draft:$date:dur", d.durMin ?: -1)
            putInt("draft:$date:effort", d.body ?: 0)
            putInt("draft:$date:mind", d.mind ?: 0)
            putInt("draft:$date:budget", when (d.underBudget) { true -> 1; false -> 0; null -> -1 })
            putBoolean("draft:$date:sleepok", d.sleepConfirmed)
        }
    }

    private fun clearDraft(date: LocalDate) = prefs.edit {
        listOf("mood", "bed", "dur", "effort", "mind", "budget", "sleepok").forEach {
            remove("draft:$date:$it")
        }
    }

    data class Draft(
        val mood: Int?,
        val bedMin: Int?,
        val durMin: Int?,
        val body: Int?,
        val mind: Int?,
        val underBudget: Boolean?,
        val sleepConfirmed: Boolean,
    ) {
        val complete: Boolean
            get() = mood != null && bedMin != null && durMin != null &&
                body != null && mind != null && underBudget != null && sleepConfirmed
        /** Of the six readings — bed and duration confirm together as two. */
        val answeredCount: Int
            get() = listOf(mood != null, body != null, mind != null, underBudget != null)
                .count { it } + if (sleepConfirmed) 2 else 0
    }

    // ── The week, resolved ─────────────────────────────────────────────

    /**
     * Resolved day slots of [isoWeek] up to (and including) resolved days.
     * A past day without a sealed row is Missed; today (and a yesterday
     * still inside its grace window) is simply absent — the fig is
     * listening, not scarred. Append-only: a day never un-scars.
     */
    suspend fun daySlots(isoWeek: String, today: LocalDate): List<DaySlot> {
        val monday = WeekKeys.mondayOf(isoWeek)
        val rows = db.days().byWeek(isoWeek).associateBy { it.date }
        val out = ArrayList<DaySlot>(7)
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            if (date > today) break
            val row = rows[date.toString()]
            when {
                row != null && !row.missed -> out += DaySlot.Sealed(row.toReading())
                row != null -> out += DaySlot.Missed
                // Unresolved: today always waits; yesterday waits inside grace.
                date == today -> break
                date == today.minusDays(1) && isoWeek == WeekKeys.isoWeekKey(today) -> break
                else -> out += DaySlot.Missed
            }
        }
        return out
    }

    suspend fun liveFig(now: ZonedDateTime = ZonedDateTime.now()): Fig {
        val today = now.toLocalDate()
        val key = WeekKeys.isoWeekKey(today)
        return Grow.grow(seedFor(key), daySlots(key, today))
    }

    suspend fun todayRow(now: ZonedDateTime = ZonedDateTime.now()): DayReadingEntity? =
        db.days().byDate(now.toLocalDate().toString())

    /** Seal a day's five readings. ≤ 20 seconds, ≤ 6 taps, then quiet. */
    suspend fun sealDay(date: LocalDate, reading: DayReading, now: ZonedDateTime = ZonedDateTime.now()) {
        db.days().upsert(
            DayReadingEntity(
                date = date.toString(),
                isoWeek = WeekKeys.isoWeekKey(date),
                dayIndex = WeekKeys.dayIndex(date),
                missed = false,
                mood = reading.mood,
                bedMinutesAfterNoon = reading.bedMinutesAfterNoon,
                durationMin = reading.durationMin,
                effort = reading.body,
                mentalEffort = reading.mind,
                underBudget = reading.underBudget,
                sealedAt = now.toInstant().toEpochMilli(),
                zoneOffsetMin = now.offset.totalSeconds / 60,
            ),
        )
        clearDraft(date)
        pressIfDue(now)
    }

    /** Scar a day by explicit choice (grace's `scar it`). */
    suspend fun scarDay(date: LocalDate, now: ZonedDateTime = ZonedDateTime.now()) {
        db.days().insertIfAbsent(scarRow(date, now))
        clearDraft(date)
    }

    /** Grace lapsed quietly: days before yesterday scar themselves. */
    suspend fun resolveLapsedGraces(now: ZonedDateTime = ZonedDateTime.now()) {
        val today = now.toLocalDate()
        val key = WeekKeys.isoWeekKey(today)
        val monday = WeekKeys.mondayOf(key)
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            if (date >= today.minusDays(1)) break
            if (db.days().byDate(date.toString()) == null) {
                db.days().insertIfAbsent(scarRow(date, now))
            }
        }
    }

    /**
     * Daily housekeeping for hot paths: lapsed graces and due presses run
     * once per day here — a widget tap can't afford the full sweep. Seals,
     * app opens and Loom binds still run the real thing.
     */
    suspend fun housekeepDaily(now: ZonedDateTime = ZonedDateTime.now()) {
        val today = now.toLocalDate().toString()
        if (prefs.getString(KEY_HOUSEKEPT, null) == today) return
        resolveLapsedGraces(now)
        pressIfDue(now)
        prefs.edit { putString(KEY_HOUSEKEPT, today) }
    }

    /** Yesterday, if it is still unresolved and inside its grace window. */
    suspend fun graceDate(now: ZonedDateTime = ZonedDateTime.now()): LocalDate? {
        val yesterday = now.toLocalDate().minusDays(1)
        if (WeekKeys.isoWeekKey(yesterday) != WeekKeys.isoWeekKey(now.toLocalDate())) return null
        return if (db.days().byDate(yesterday.toString()) == null) yesterday else null
    }

    private fun scarRow(date: LocalDate, now: ZonedDateTime) = DayReadingEntity(
        date = date.toString(),
        isoWeek = WeekKeys.isoWeekKey(date),
        dayIndex = WeekKeys.dayIndex(date),
        missed = true,
        mood = 0, bedMinutesAfterNoon = 0, durationMin = 0, effort = 0,
        mentalEffort = 0,
        underBudget = false,
        sealedAt = now.toInstant().toEpochMilli(),
        zoneOffsetMin = now.offset.totalSeconds / 60,
    )

    // ── Pressing ───────────────────────────────────────────────────────

    /**
     * At the first event after Sunday 23:59 local — an EVENT_AOD, a Probe
     * seal, or app open, whichever comes first — every finished week since
     * install is pressed: frozen to Room, thumbnail rastered, a new seed
     * implied for the new week. Idempotent; never skips a week. A week
     * with no readings at all still presses — the archive is honest.
     */
    suspend fun pressIfDue(now: ZonedDateTime = ZonedDateTime.now()): List<WeekEntity> {
        val today = now.toLocalDate()
        val currentMonday = WeekKeys.monday(today)
        var monday = WeekKeys.mondayOf(installWeek)
        val pressed = ArrayList<WeekEntity>()
        while (monday < currentMonday) {
            val key = WeekKeys.isoWeekKey(monday)
            if (db.weeks().byKey(key) == null) {
                pressed += press(key, monday, now)
                prefs.edit { remove(KEY_WITNESSED + key) }
            }
            monday = monday.plusWeeks(1)
        }
        return pressed
    }

    private suspend fun press(key: String, monday: LocalDate, now: ZonedDateTime): WeekEntity {
        // Complete the record: unfilled days scar.
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            if (db.days().byDate(date.toString()) == null) {
                db.days().insertIfAbsent(scarRow(date, now))
            }
        }
        val rows = db.days().byWeek(key)
        val slots = rows.map { row ->
            if (row.missed) DaySlot.Missed else DaySlot.Sealed(row.toReading())
        }
        return pressWeek(key, slots, resolveCity(), ceremonyPending = true, at = now)
    }

    /** Freeze a week from resolved slots. The press, as a single verb. */
    suspend fun pressWeek(
        isoWeek: String,
        slots: List<DaySlot>,
        collected: String?,
        ceremonyPending: Boolean = true,
        at: ZonedDateTime = ZonedDateTime.now(),
    ): WeekEntity {
        val seed = seedFor(isoWeek)
        val fig = Grow.grow(seed, slots)
        val entity = WeekEntity(
            isoWeek = isoWeek,
            seed = seed.toLong(),
            pressedAt = at.toInstant().toEpochMilli(),
            ceremonyPlayedAt = if (ceremonyPending) null else at.toInstant().toEpochMilli(),
            seasonName = fig.stats.season.name,
            seasonTint = fig.stats.season.hex,
            statsJson = fig.stats.toJson(),
            cellsJson = fig.cellsToJson(),
            collected = collected,
        )
        db.weeks().insert(entity)
        PlateThumbs.write(context, isoWeek, fig, seed)
        return entity
    }

    /** A pressed week whose Loom ceremony hasn't been witnessed yet. */
    suspend fun pendingCeremony(): WeekEntity? = db.weeks().pendingCeremony()

    suspend fun pressedWeeks(): List<WeekEntity> = db.weeks().all()

    fun observePressedWeeks(): kotlinx.coroutines.flow.Flow<List<WeekEntity>> =
        db.weeks().observeAll()

    suspend fun pressedWeek(isoWeek: String): WeekEntity? = db.weeks().byKey(isoWeek)

    suspend fun dayRows(isoWeek: String): List<DayReadingEntity> = db.days().byWeek(isoWeek)

    fun observeDayRows(isoWeek: String): kotlinx.coroutines.flow.Flow<List<DayReadingEntity>> =
        db.days().observeWeek(isoWeek)

    suspend fun markCeremonyPlayed(isoWeek: String) =
        db.weeks().markCeremonyPlayed(isoWeek, System.currentTimeMillis())

    fun figOf(week: WeekEntity): Fig {
        val cells = cellsFromJson(week.cellsJson)
        val stats = statsFromJson(week.statsJson)
        return Fig(cells = cells, tip = cells.last().pos, daysGrown = 7, stats = stats)
    }

    // ── Loom's witness ledger ──────────────────────────────────────────

    /** Highest day index whose growth Loom has bloomed for the live week. */
    fun witnessedThrough(isoWeek: String): Int = prefs.getInt(KEY_WITNESSED + isoWeek, 0)

    fun markWitnessed(isoWeek: String, dayIndex: Int) =
        prefs.edit { putInt(KEY_WITNESSED + isoWeek, dayIndex) }

    // ── Collected: coarse city, never requested here ───────────────────

    /** Last-known coarse city. Only reads if permission already granted. */
    @SuppressLint("MissingPermission")
    fun resolveCity(): String? = runCatching {
        val granted = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.allProviders.firstNotNullOfOrNull { lm.getLastKnownLocation(it) } ?: return null
        @Suppress("DEPRECATION")
        val addr = Geocoder(context, Locale.getDefault())
            .getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull() ?: return null
        (addr.locality ?: addr.subAdminArea ?: addr.adminArea)?.uppercase(Locale.ROOT)?.take(18)
    }.getOrNull()

    // ── Erasure ────────────────────────────────────────────────────────

    suspend fun eraseEverything() {
        db.weeks().deleteAll()
        db.days().deleteAll()
        PlateThumbs.clear(context)
        prefs.edit { clear() }
    }

    suspend fun burnSpecimen(isoWeek: String) {
        db.weeks().delete(isoWeek)
        PlateThumbs.delete(context, isoWeek)
    }

    private fun DayReadingEntity.toReading() = DayReading(
        mood = mood,
        bedMinutesAfterNoon = bedMinutesAfterNoon,
        durationMin = durationMin,
        body = effort,
        // Rows sealed before the split carry 0: unrecorded reads as 1 —
        // no thorn invented after the fact.
        mind = mentalEffort.coerceIn(1, 5).takeIf { mentalEffort != 0 } ?: 1,
        underBudget = underBudget,
    )

    companion object {
        private const val KEY_SALT = "installSalt"
        private const val KEY_INSTALL_WEEK = "installWeek"
        private const val KEY_BUDGET = "screenBudgetMin"
        private const val KEY_EFFORT_LABEL = "effortLabel"
        private const val KEY_REMINDER = "reminderOn"
        private const val KEY_KEY_SEEN = "keySeen"
        private const val KEY_WITNESSED = "witnessed:"
        private const val KEY_HOUSEKEPT = "housekeptDate"

        @Volatile private var instance: FigRepository? = null

        fun get(context: Context): FigRepository =
            instance ?: synchronized(this) {
                instance ?: FigRepository(context.applicationContext).also { instance = it }
            }
    }
}
