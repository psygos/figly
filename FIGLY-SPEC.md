# FIGLY — Build Specification v1.0

**Target:** Nothing Phone (4a) Pro · Android 16 · Kotlin
**Deliverable:** One APK containing the triad — a Glyph Matrix toy, a home-screen widget, and a gallery app.
**Companion file:** `figly-reference-plate.svg` — the canonical visual target for specimen plates. When in doubt, match the plate.

---

## 0. READ THIS FIRST — intent

Figly grows one **fig** per week: a small alien plant, rendered on the phone's rear Glyph Matrix, whose *shape is a truthful record of how the week was lived*. Five readings per day — when you slept, how long, how the day felt (0–10), gym, screen time under budget — are the only inputs. At week's end the fig is **pressed** into an archive, like a botanist pressing a specimen, and a new seed is set.

The fiction that governs every design decision: **a traveler is cataloguing flora that grow from human weeks.** The tone is Scavengers Reign by way of Nothing — quiet, alien, precise, field-notebook. Wonder without whimsy. No gamification vocabulary anywhere: no streaks, no scores, no badges, no congratulations. The fig never judges; it only records.

**The Taste Law (non-negotiable):**

- NO donut charts, rings, progress bars, gauges, sparklines, or any recognizable data-viz idiom. The fig **is** the chart.
- NO emoji, confetti, mascot illustrations, or stock iconography.
- NO Material dynamic color, no default Material cards/elevation, no skeleton-shimmer loaders.
- NO streak-shaming or motivational copy ("Keep it up!" is forbidden). Copy is field-notebook register: terse, lowercase-calm or smallcaps-technical.
- Color is rationed (see §7). If a screen is more than ~6% colored by area, it's wrong.
- When unsure, remove one thing.

**Build order:** §3 morphology core first (pure Kotlin + golden tests) → §4 Loom → §5 Probe → §6 Herbarium. The core is the product; the three surfaces are windows onto it.

---

## 1. The triad

Three units, one organism. Shared design language, but each unit has one job and a distinct material identity — this is how they differ:

| Unit | Surface | Job | Material identity |
|---|---|---|---|
| **LOOM** | Glyph Matrix (rear AOD toy) | *Weaves* — grows the living fig over the week | **Light.** Glowing, breathing, monochrome, alive. The only place the fig is alive. |
| **PROBE** | Home-screen widget | *Asks* — takes the day's five readings | **Voice.** Terse, instrumental, ephemeral. Exists to ask one day's questions, then seals and goes quiet. |
| **HERBARIUM** | The app | *Keeps* — archives and displays pressed figs | **Ink.** Matte, permanent, annotated. The only place color exists. |

The differentiating rule, stated once and enforced everywhere: **Loom glows, Probe asks, Herbarium presses.** A pressed fig in Herbarium must never glow (no blur, no bloom, no shadow) — pressing kills the light and leaves ink. The live fig on Loom must never carry color — the hardware is mono and that's its truth.

Naming in UI: the units are referred to by these names in settings and onboarding ("Loom is on the back of your phone"). The weekly specimen is a **fig**; archived IDs read `FIG-2026-W24`.

---

## 2. Hardware & platform facts

- Device registration: `Glyph.DEVICE_25111p` (Phone (4a) Pro). Matrix length **13** — always fetch via `Common.getDeviceMatrixLength()`, never hardcode.
- The panel is 13×13 logical, **137 physical LEDs** in a circular arrangement (corners absent). Mono white, per-pixel brightness 0–255.
- (4a) Pro has **no Glyph Button/Touch** and supports **AOD toys only**: manifest must set `com.nothing.glyph.toy.aod_support = 1`. The toy activates via *Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy* — i.e., **the fig is visible when the phone is face down.** Design assumes this.
- AOD toys receive `EVENT_AOD` roughly once per minute while selected. Two render regimes:
  - **Lively window** — just after the phone is flipped and the service binds: run animation at ≤8 fps via Handler; shimmer/breath allowed.
  - **Heartbeat** — per-minute `EVENT_AOD`: push one static frame, no loops.
- SDK: `GlyphMatrixSDK` (kit: `Nothing-Developer-Programme/GlyphMatrix-Developer-Kit`). Permission `com.nothing.ketchum.permission.ENABLE`. Toy = exported Service with `com.nothing.glyph.TOY` intent filter + name/image metadata. Frames via `GlyphMatrixFrame.Builder` (max 3 layers: low/mid/top).
- After setup, deep-link users to the toys manager: component `com.nothing.thirdparty / …ToysManagerActivity`.

**Circular mask** — valid columns per row (row 0 = top):

```
row:    0      1      2      3      4–8     9      10     11     12
cols: 4..8   2..10  1..11  1..11  0..12  1..11  1..11  2..10  4..8
```

---

## 3. The morphology grammar (the heart)

The fig is a **pure deterministic function**: `fig = grow(weekSeed, readings[7])`. Same data → same fig, always. This is what makes scrubbing, re-rendering, and the archive trustworthy.

`weekSeed = fnv1a(isoWeekKey + installSalt)` feeding a `mulberry32` PRNG. The seed contributes *organic jitter only* — data must dominate form (target: a reader who knows the key in §6.4 can reconstruct ~85% of the week from the fig alone).

### 3.1 Daily readings → morphological channels

Each reading shapes exactly one channel. This 1:1 mapping is the product's legibility — never blend channels.

| Reading | Channel | Rule |
|---|---|---|
| Day rating 0–10 | **Internode length** (vigor) | segments that day `L`: 0–2 → 1 · 3–5 → 2 · 6–8 → 3 · 9–10 → 4 |
| Sleep duration | **Leaves** (rest) | ≥ 7h → leaf pair (both sides of mid-node) · 5–7h → single leaf (day-side) · < 5h → bare |
| Sleep start time | **Tropism** (bend) | early nights climb, late nights droop sideways — see weights below |
| Gym (bool) | **Thorn** (strength) | true → one spike off the day's last node, pointing down-outward |
| Screen under budget (bool) | **Drupe** (discipline fruit) | true → day's terminal node flagged DRUPE (the brightest thing on the fig) |
| *Missed check-in* | **Scar** | one dim cell straight up; no ornaments. Honest gaps. |

### 3.2 Growth algorithm

```
state: cells map (key = r*13+c → {type, day}), tip = (10,6)
SOIL is static: row 11, cols 3..9 (not part of the fig)
plant SEED at (10,6) on Monday 00:00 local

for each day d in Mon..Sun, when its reading is sealed:
  lateness = clamp(minutesAfter(22:30, bedTime), 0, 270)   // 22:30 → 03:00
  upW   = lerp(3.0, 0.25, lateness/270)
  diagW = 1.0
  latW  = lerp(0.15, 1.6, lateness/270)
  side  = rng() < 0.5 ? LEFT : RIGHT      // chosen once per day, persisted

  repeat L(rating) times:
    candidates = { up:(0,-1)·upW, diag:(side,-1)·diagW, lat:(side,0)·latW }
    pick weighted by rng; accept iff inMask ∧ empty ∧ occupiedNeighbors8 ≤ 2
    if all rejected → stop early            // cramped weeks make cramped figs; keep it
    place WOOD, advance tip

  midNode  = ⌈L/2⌉-th cell placed today
  lastNode = final cell placed today
  leaves:  per 3.1, placed perpendicular to that segment's travel; skip a side if blocked
  thorn:   if gym → cell at lastNode + (−side, +1); skip if blocked
  drupe:   if underBudget → lastNode.type = DRUPE

missed day: place 1 SCAR cell straight up from tip (fallback diag if blocked)
```

Capacity check: worst case 7×4 wood + 14 leaves + 7 thorns ≈ 49 cells — fits 137 with air. The `≤ 2 neighbors` rule keeps the canopy lacy; never relax it.

### 3.3 Pressing

At the first event after Sunday 23:59 local (an `EVENT_AOD`, a Probe seal, or app open — whichever comes first): freeze `WeekRecord {isoWeek, readings, seed, cells, stats, seasonTint}`, write to Room, reset Loom to a new seed. The pressing *ceremony* (§4.3) plays on the next flip.

### 3.4 Golden weeks (ship as unit-test fixtures)

1. **AURIC WEEK** — all ratings 9–10, all sleep 23:00/8h, 5 budget days, 3 gym days → assert: ≥ 24 wood cells, height reaches row ≤ 2, 7 leaf pairs, 3 thorns, 5 drupes, 0 scars; silhouette predominantly vertical.
2. **ASHFALL WEEK** — ratings 1–3, sleep 02:30/4h, 0 budget, 0 gym, 2 missed days → assert: ≤ 12 cells, height ≥ row 7 (stunted), 0 leaves, 0 drupes, 2 scars; pronounced lateral droop.
3. **MIXED WEEK** (the reference plate) — assert exact cell-type counts equal the plate label: 16 cells · 5 leaves · 2 thorns · 3 drupes · 1 scar.

Golden test = serialize `grow()` output and snapshot-compare. Determinism test: two runs, identical maps.

---

## 4. LOOM — the Glyph toy (light)

### 4.1 Brightness law (semantic, mono)

| Element | Brightness | Motion |
|---|---|---|
| Soil | 38 | none |
| Scar | 60 | none — scars never move |
| Wood / thorn | 126 | breath only |
| Leaf | 176 | shimmer ±22, sin(t/620 + phase) — lively window only |
| Drupe | 205–250 | slow pulse, 1.4s period — the only near-max light |
| Awaiting tip (today unread) | 80↔140 | 2s pulse — the fig is listening |
| Today's commit | bloom-in: 0→255 in 250ms, settle to natural over 750ms | once, on first flip after sealing |
| Global breath | ×(1 + 0.045·sin(t/2700)) | lively window only; heartbeat frames are static |

Layer mapping: **low** = soil, **mid** = fig body, **top** = bloom/pulse effects.

### 4.2 Weekly lifecycle on the matrix

- **Mon 00:00** — bare soil + seed cell at (10,6), brightness 90, slow pulse.
- **Each day, pre-reading** — current form + awaiting-tip pulse.
- **On seal** — nothing changes until the phone is next flipped; then the day's growth blooms in cell-by-cell, 140ms apart. *Growth must be witnessed* — never animate to an empty room.
- **Sunday night, pressed** — next flip plays the **pressing ceremony**: replay the week's 7 growth days fast (220ms/day) → all brightness drains downward into the soil over 1400ms → a single ember (1 px, 38) → 600ms hold → new seed sprouts. Total ≤ 4.5s, then normal AOD.

### 4.3 Engineering notes

- The service holds no background state: on every bind/heartbeat, recompute from Room (`grow()` is cheap). Schrödinger architecture — the fig doesn't exist until observed.
- Heartbeat frame budget: < 8ms to build. No allocations in the render loop.
- Toy preview image (manifest metadata): the reference-plate fig silhouette, white on transparent, per the dev kit's icon spec.

---

## 5. PROBE — the widget (voice)

Jetpack **Glance**. Primary size 4×2 (support 3×2 min). Dark, hairline-bordered, no card elevation. Probe's entire personality: an instrument that asks five questions, takes ≤ 20 seconds and ≤ 6 taps, seals, and shuts up.

### 5.1 Layout

Left third: today's contribution as a 5×5 dot stamp (live preview of what today will add — recomputed from current partial answers). Right two-thirds: the readings, stacked:

```
MOOD     · · · · ● · · · · · ·        ← 11 tappable dots (0–10), filled to selection
SLEPT    01:10 → 08:30  [confirm]     ← auto-suggested; tap value → slim bottom sheet (15-min steppers)
GYM      [thorn mark — toggles]
SCREEN   UNDER ✓ (auto)               ← auto-resolved from UsageStats vs budget x; tap to override
                              [ SEAL DAY ]
```

### 5.2 States

| State | When | Shows |
|---|---|---|
| ASKING | unsealed, after 17:00 | full question strip |
| EARLY | unsealed, before 17:00 | dimmed strip + `readings open` microcopy; still tappable |
| PARTIAL | some answered | answered rows collapse to their mark; remaining stay live |
| SEALED | all five in | the day's stamp + `DAY SEALED · WED` — no celebration, just the record |
| PRESSING | Sunday after seal | `FIG-2026-W24 PRESSED` + the pressed silhouette, until Monday |
| GRACE | yesterday missed, < 24h | one back-row: `yesterday? [fill] [scar it]` — explicit choice, default scar |

Sleep auto-suggestion: last screen-off ≥ 23:00 to first screen-on heuristic (needs UsageStats); always confirm-not-assume. Screen-budget auto-resolve at 23:00 or on seal, whichever first. Optional 21:30 reminder notification, **default OFF** — Probe asks by existing, it never nags.

### 5.3 Copy register

All-caps mono micro-labels, tracking 0.14em. No exclamation marks. Vocabulary: *readings, seal, pressed, scar, drupe.* Errors are field notes: `NO USAGE ACCESS — SCREEN READING IS MANUAL` (links to grant).

---

## 6. HERBARIUM — the app (ink)

Compose, single activity, dark only. Navigation is one vertical stack — no bottom bar, no tabs.

### 6.1 Information architecture

1. **This Week** (home) — the living plate: today's fig state rendered LARGE in plate style but with one concession to life: the awaiting tip may pulse (the only motion on this screen). Beneath it, the seven day-marks row (sealed days show their stamp; unsealed show `·`). One line of state: `DAY 4 OF 7 · 19 CELLS`.
2. **The Drawer** (scroll continues / pull up) — archive of pressed plates as a collector's drawer: 3-column grid of fig thumbnails, pure ink on plate, *no labels until tap* (the silhouettes are the index — after a month, you recognize your weeks by shape). Reverse chronological. Tap → Plate.
3. **Plate** — full specimen view. Match `figly-reference-plate.svg` exactly: dot field upper two-thirds, label block lower-left, corner ticks. Tap any fig node → the label block's last line swaps to that day's annotation: `THU · SLEPT 01:12 · 5H40 · MOOD 4 · NO GYM · DRUPE`. Tap elsewhere reverts. Overflow actions: export (PNG 2048px + SVG), delete (two-step, copy: `burn this specimen?`).
4. **The Key** — "How to read a fig": one screen, a single labeled specimen diagram (hand-author this SVG; callout hairlines from each feature to its meaning: internode→day rating, leaf→sleep, bend→bedtime, thorn→gym, drupe→screen, scar→missed). This screen *is* the onboarding, shown once after setup, reachable from Plate overflow.
5. **Settings** — screen budget `x` (default 3h), week start (locked Mon v1, show why), gym row label (rename to any practice, e.g. "run"), usage access, export all, erase all. Footer: `FIGLY KEEPS EVERYTHING ON THIS PHONE. THERE IS NO ACCOUNT, NO CLOUD, NO NETWORK.` — and make it true (§9).

### 6.2 Plate rendering (the SVG craft)

Render figs as SVG/Canvas with the **pressing treatment**:

- The 13×13 dot paper is faintly present (unlit positions at `ink-faint` 45% — pressed specimens keep their grid, like graph-paper herbarium sheets).
- Lit cells become **matte ink dots**: wood r 9.5 · leaf r 8 at 80% ink · thorn r 6 · scar r 8 in `ink-faint` · drupe r 10 filled with the week's **season tint** — the only color on the plate.
- Organic press: every dot's radius jittered ±7% and center ±0.6px, seeded by `weekSeed` (deterministic — the same plate always presses the same way).
- **Absolutely no glow, blur, gradient, or shadow on plates.** Ink is ink.

### 6.3 Season tints (the meaningful color)

Each pressed week gets one tint from its **mean day-rating** — a climate, not a grade:

| Season | Mean rating | Hex |
|---|---|---|
| ASHFALL | 0–1.9 | `#A3653F` |
| OVERCAST | 2–3.9 | `#7C8B94` |
| TEMPERATE | 4–5.9 | `#8FA381` |
| VERDANT | 6–7.9 | `#5FA98B` |
| AURIC | 8–10 | `#D6B36A` |

Color appears in exactly three places: drupe dots, the 18×3px season tick beside the season word, and the specimen-ID's 2px underline on the Plate screen. Nowhere else — not buttons, not nav, not states. Bad weeks aren't red; ASHFALL is rust-beautiful. Climates, never verdicts.

### 6.4 Empty & edge states

- First launch, empty drawer: a single empty plate — `THE DRAWER IS EMPTY. YOUR FIRST FIG IS GROWING.` + soil-and-seed mark.
- Fully-missed week: it still presses — a scar-column specimen. Never skip a week; the archive is honest.
- Long city names / no location: `COLLECTED: —`. Location is coarse city string from last known (no permission nagging; if absent, the dash).
- Year boundary: ISO week rules; `FIG-2027-W01` follows `FIG-2026-W53` cleanly.

---

## 7. Design tokens

| Token | Value | Usage |
|---|---|---|
| `ground` | `#0A0B0D` | app background |
| `plate` | `#111317` | plate surfaces, widget body |
| `hairline` | `#20232A` | all rules/borders, 1px only |
| `ink` | `#E7E2D5` | primary text, fig dots (warm bone) |
| `ink-dim` | `#6B675C` | secondary text, microcopy |
| `ink-faint` | `#3A3933` | dot paper, scars, disabled |
| season tints | §6.3 | drupes + season tick + ID underline, nothing else |

**Type:** Display = **Space Grotesk** (specimen IDs, the wordmark, large day numbers — geometric, Nothing-adjacent). Data = **JetBrains Mono** (every label, reading, annotation), all-caps micro-labels at 10–11sp with 0.14em tracking. No third face.

**Space:** 4pt grid — `s1 4 · s2 8 · s3 12 · s4 16 · s6 24 · s8 32 · s12 48`. **Radius:** 0 on plates and widget (square = pressed paper), 2px max on tap targets. **Hairlines** never thicker than 1px.

**Motion:** standard fade/translate 180ms `cubic-bezier(0.2, 0, 0, 1)`; plate open 240ms; node-annotation swap 120ms crossfade; pressing ceremony per §4.2. Respect `prefers-reduced-motion`/Animator scale: kill shimmer and pulses, keep state changes instant. Nothing in Herbarium loops forever except the This-Week awaiting tip.

---

## 8. Reference plate

`figly-reference-plate.svg` is the acceptance bar for: dot sizing/jitter, label-block typography and spacing, corner ticks, tint dosage, overall restraint. The Plate screen should be indistinguishable from it in style. Its fig corresponds to Golden Week 3.

---

## 9. Architecture & data

- **Modules:** `:core-morphology` (pure Kotlin, zero Android deps, golden-tested) · `:glyph` (Loom service) · `:widget` (Probe, Glance) · `:app` (Herbarium, Compose) · `:data` (Room).
- **Room:** `WeekEntity(isoWeek PK, seed, pressedAt, seasonTint, statsJson, cellsJson)` · `DayReadingEntity(date PK, isoWeek, bedMin, durMin, mood, gym, underBudget, sealedAt, late)`. Live week recomputed, never cached stale.
- **Privacy is a feature:** the manifest declares **no `INTERNET` permission**. No analytics, no crash SDK that phones home, no network stack at all — verifiable by inspection. Optional permissions: `PACKAGE_USAGE_STATS` (screen budget + sleep suggestion; everything degrades gracefully to manual without it).
- Exports via MediaStore (user-initiated only). All times local; store zone offsets with readings so travel doesn't corrupt weeks.
- Performance: cold start < 800ms to This Week; drawer scrolls 60fps with 100+ plates (thumbnails pre-rastered on press); widget update < 50ms; Loom heartbeat frame < 8ms.
- Accessibility: every fig surface gets a contentDescription summarizing the data ("week 24, mixed, 16 cells, one scar"); node annotations reachable via talkback custom actions; all tap targets ≥ 44dp; mono palette already clears contrast — verify season tints on `plate` ≥ 3:1.

---

## 10. Acceptance checklist

- [ ] `grow()` deterministic; three golden weeks pass; mixed week matches the reference plate's counts exactly.
- [ ] Loom: seed→growth→pressing ceremony lifecycle verified on device; growth only ever animates while visible; heartbeat frames static.
- [ ] Probe: 5 readings sealable in ≤ 6 taps; all six states reachable; grace flow works; default-off reminder.
- [ ] Herbarium: plate visually matches `figly-reference-plate.svg`; node-tap annotations; drawer is label-free; The Key explains all six channels on one screen.
- [ ] Color audit: tints appear only in the three sanctioned places. Glow audit: zero blur/shadow anywhere in Herbarium; zero color on Loom.
- [ ] `aapt dump permissions` shows no INTERNET.
- [ ] The Taste Law violations: none. Read §0 again before declaring done.
