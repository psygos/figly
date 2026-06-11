package app.figly.glyph

import app.figly.core.Matrix
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartwoodTest {

    private fun ascii(frames: LoomFrames): String = buildString {
        for (r in 0 until Matrix.SIZE) {
            val line = StringBuilder()
            for (c in 0 until Matrix.SIZE) {
                val i = r * Matrix.SIZE + c
                val v = maxOf(frames.low[i], frames.mid[i], frames.top[i])
                line.append(
                    when {
                        !Matrix.inMask(r, c) -> ' '
                        v == 0 -> '·'
                        v < 50 -> '~'
                        v < 140 -> 'W'
                        else -> '@'
                    },
                ).append(' ')
            }
            appendLine(line.toString().trimEnd())
        }
    }

    private fun renderAt(secondsInCycle: Long, lively: Boolean = false): LoomFrames {
        val frames = LoomFrames()
        // Cycle 7077 chosen arbitrarily; any epoch works — it is a clock.
        val epochMs = (7077L * Heartwood.CYCLE_S + secondsInCycle) * 1000
        Heartwood.render(frames, epochMs, lively, animMs = 0)
        return frames
    }

    @Test
    fun `the cycle tells its story`() {
        for ((label, s) in listOf(
            "early growth" to 60L, "mid growth" to 170L, "the held heart" to 300L,
            "mid fall" to 440L, "bare branches" to 588L, "dissolved" to 599L,
        )) {
            println("== $label · t=${s}s")
            println(ascii(renderAt(s)))
        }
    }

    @Test
    fun `the heart completes and the branches survive the fall`() {
        val full = renderAt(300)
        val lit = full.mid.count { it > 0 }
        assertTrue("the held heart is full ($lit cells)", lit >= 45)

        val bare = renderAt(588)
        val wood = bare.mid.count { it in 60..150 }
        val leaves = bare.mid.count { it > 150 }
        assertTrue("branches remain ($wood)", wood >= 10)
        assertTrue("no leaf survives autumn ($leaves)", leaves == 0)
    }

    @Test
    fun `every cycle grows its own tree, deterministically`() {
        val a = Heartwood.Build(7077)
        val b = Heartwood.Build(7077)
        val c = Heartwood.Build(7078)
        assertTrue(a.wood == b.wood && a.leaves == b.leaves)
        assertTrue(a.wood != c.wood || a.leaves != c.leaves)
    }
}
