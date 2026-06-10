# figly

One fig per week — a small alien plant, rendered on the rear Glyph Matrix
of a Nothing Phone (4a) Pro, whose shape is a truthful record of how the
week was lived. At week's end the fig is pressed into an archive, like a
botanist pressing a specimen, and a new seed is set.

The fig never judges; it only records.

## The triad

One APK, three surfaces, one organism:

| unit | surface | job | material |
|---|---|---|---|
| **LOOM** | Glyph Matrix (AOD toy) | weaves — grows the living fig | light |
| **PROBE** | home-screen widget | asks — takes the day's five readings | voice |
| **HERBARIUM** | the app | keeps — archives pressed figs | ink |

Loom glows, Probe asks, Herbarium presses. A pressed fig never glows;
the live fig never carries color.

## The grammar

The fig is a pure deterministic function: `fig = grow(weekSeed, readings)`.
Same data, same fig, always. Five readings a day, all scales 1–5:

- **mood** → internode length (more segments, better day)
- **sleep duration** → leaves (pair ≥ 7h · one 5–7h · bare below)
- **bedtime** → tropism (early nights climb, late nights droop)
- **physical effort** → a thorn, grown at 4 and above
- **screen under budget** → the drupe, the only near-max light
- a missed check-in → a scar, straight up. honest gaps.

Three golden weeks (AURIC, ASHFALL, MIXED) are pinned as ASCII snapshots
in `core-morphology` — changing the grammar is a product decision, not a
refactor. `docs/plates/` holds their pressed plates, regenerated from the
same geometry that renders in the app, on the matrix, and in every export.

`figly-reference-plate.svg` (repo root) is the original hand-authored
style bar — dot sizing, jitter, label typography, tint dosage. Its fig
predates the 1–5 scales and was drawn freehand (its wood is not
step-connected), so morphological truth lives in the goldens; visual
truth lives in the reference plate. When in doubt, match the plate.

## Privacy

The manifest never asks for `INTERNET` — verify:

```
aapt dump permissions app-release.apk
```

No analytics, no crash reporting, no network stack. Optional, opt-in:
usage access (screen budget + sleep suggestion), coarse location (the
`COLLECTED` city on plates), notifications (a silent 21:30 reminder,
default off). Figs may move house to a new phone via device transfer;
they are excluded from cloud backup.

## Building

```
JAVA_HOME=<jdk17> ./gradlew :app:assembleRelease
./gradlew :core-morphology:test          # the grammar + golden weeks
./gradlew :core-morphology:renderGoldens # press the goldens to /tmp/figly-plates
```

Modules: `:core-morphology` (pure Kotlin, zero Android) · `:data` (Room,
the press lifecycle) · `:glyph` (Loom) · `:widget` (Probe, Glance) ·
`:app` (Herbarium, Compose foundation — no Material).

The Glyph Matrix SDK is vendored in `libs/` from Nothing's
[GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
Fonts: Space Grotesk and JetBrains Mono, both OFL.

## On device

1. Install the APK.
2. First launch shows The Key (how to read a fig), offers to place
   Probe, and deep-links to the Glyph Toys manager.
3. Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy →
   figly. Face down, the fig is awake.
