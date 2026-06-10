package app.figly.core

/**
 * Deterministic randomness. The seed contributes organic jitter only —
 * data must dominate form. Same data, same fig, always.
 */

/** FNV-1a 32-bit hash of a string. */
fun fnv1a32(s: String): UInt {
    var h = 0x811C9DC5u
    for (b in s.encodeToByteArray()) {
        h = h xor b.toUByte().toUInt()
        h *= 0x01000193u
    }
    return h
}

/** mulberry32 PRNG. Tiny, fast, and identical on every platform that runs it. */
class Mulberry32(seed: UInt) {
    private var a: UInt = seed

    /** Next double in [0, 1). */
    fun next(): Double {
        a += 0x6D2B79F5u
        var t = a
        t = (t xor (t shr 15)) * (t or 1u)
        t = t xor (t + (t xor (t shr 7)) * (t or 61u))
        return (t xor (t shr 14)).toDouble() / 4294967296.0
    }
}

/** The seed of a week: fnv1a(isoWeekKey + installSalt). */
fun weekSeed(isoWeekKey: String, installSalt: String): UInt = fnv1a32(isoWeekKey + installSalt)
