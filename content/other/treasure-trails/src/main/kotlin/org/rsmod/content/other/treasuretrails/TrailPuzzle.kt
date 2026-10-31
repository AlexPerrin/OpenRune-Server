package org.rsmod.content.other.treasuretrails

import dev.openrune.ServerCacheManager

/**
 * The 5x5 slide puzzle (`trail_slidepuzzle`, 306): slot `i` of `trail_puzzleinv` holds a piece obj
 * or nothing (the gap). The picture's pieces are enum 1864[puzzle] (position -> obj); it is solved
 * when slots 0-23 hold pieces 0-23 in order and slot 24 is the gap.
 */
object TrailPuzzle {
    const val SIZE = 5
    const val SLOTS = SIZE * SIZE
    const val SCRAMBLE_MOVES = 101
    private const val PIECE_ENUMS = 1864

    fun pieces(puzzle: Int): List<Int> {
        val pieceEnum = ServerCacheManager.getEnum(PIECE_ENUMS)?.values?.get(puzzle) as? Int ?: return emptyList()
        val values = ServerCacheManager.getEnum(pieceEnum)?.values ?: return emptyList()
        return (0 until SLOTS - 1).mapNotNull { values[it] as? Int }
    }

    fun solved(puzzle: Int): List<Int?> = pieces(puzzle) + null

    fun isSolved(puzzle: Int, slots: List<Int?>): Boolean = slots == solved(puzzle)

    /** Where the gap is, if [slot] is next to it (no diagonals). */
    fun gapBeside(slots: List<Int?>, slot: Int): Int? {
        if (slot !in 0 until SLOTS || slots[slot] == null) return null
        val x = slot % SIZE
        val y = slot / SIZE
        return listOfNotNull(
                (slot - 1).takeIf { x > 0 },
                (slot + 1).takeIf { x < SIZE - 1 },
                (slot - SIZE).takeIf { y > 0 },
                (slot + SIZE).takeIf { y < SIZE - 1 },
            )
            .firstOrNull { slots[it] == null }
    }

    fun slide(slots: List<Int?>, slot: Int): List<Int?>? {
        val gap = gapBeside(slots, slot) ?: return null
        return slots.toMutableList().also {
            it[gap] = it[slot]
            it[slot] = null
        }
    }

    /** Mod Ash: a puzzle box is scrambled with 101 random slides into the gap from the solved state. */
    fun scramble(puzzle: Int, nextInt: (Int) -> Int): List<Int?> {
        var slots = solved(puzzle)
        repeat(SCRAMBLE_MOVES) {
            val gap = slots.indexOf(null)
            val movable = (0 until SLOTS).filter { gapBeside(slots, it) == gap }
            slots = checkNotNull(slide(slots, movable[nextInt(movable.size)]))
        }
        return slots
    }
}
