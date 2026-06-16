plugins {
    `java-library`
}

dependencies {
    // The compiler is built on the schema's type system + diagnostics.
    api(project(":schema"))
}

// PURE. The COMPILER: authored YAML+DSL → immutable validated Snapshot. Stays
// Bukkit-free by taking a PlatformResolvers facade by INJECTION (tests pass a
// fake; production passes se-platform/resolve), so cross-version resolution is a
// compile dependency without coupling the compiler to Bukkit
// (docs/architecture.md §2.1, §9).
