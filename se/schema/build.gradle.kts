plugins {
    `java-library`
}

// PURE. The DSL as a typed LANGUAGE DEFINITION — grammar, the ParamSpec type
// system, and diagnostics. Zero Bukkit; JUnit only (supplied by the root).
// This is a load-bearing purity boundary: a compiler must be deterministically
// testable (docs/architecture.md §2.1).
