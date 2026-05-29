package com.neonote.engine

/**
 * Small abstraction used by engine APIs that need caller-provided stable ids.
 */
public fun interface IdGenerator {
    public fun nextId(prefix: String): String
}
