package org.rsmod.game.debug

/** osrs-plugins-reverse: world-wide switches for device tests, set by the test `::` commands. */
public object TestFlags {
    /** When set, NPCs neither wander nor walk back to their spawn (`::freeze`). */
    @Volatile public var npcsFrozen: Boolean = false
}
