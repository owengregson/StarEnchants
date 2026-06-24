plugins {
    `java-library`
}

// Config packs (ADR-0023): a pack is a single ZIP snapshot of the whole authored config surface
// (config.yml, lang.yml, content/, items/, menus/) plus a pack.yml manifest. This module is the pure,
// dependency-free codec + on-disk store — ZIP read/write via java.util.zip and filesystem staging/swap.
// It knows nothing about Bukkit, the compiler, or the reload; the bootstrap wires apply() to the
// transactional ContentReloader. Self-contained on purpose (no module deps), so the JUnit test stack
// supplied by the root convention is all it needs.
