plugins {
    `java-library`
}

dependencies {
    // The compiler is built on the schema's type system + diagnostics.
    api(project(":schema"))

    // SnakeYAML parses authored content files into AbilityDefs (compile.load), carrying
    // file:line:col Source marks for diagnostics. compileOnly — the server bundles SnakeYAML,
    // so it is NOT shaded (no version coupling); tests + validateContent supply it themselves.
    // The compose()/Node/Mark API used is stable across the server's SnakeYAML 1.x and 2.x.
    compileOnly("org.yaml:snakeyaml:2.2")
    testImplementation("org.yaml:snakeyaml:2.2")

    // Shared compiler-shape builders (Defs) — replaces the per-stage-test def(...) helpers so an
    // AbilityDef/LoweredAbility record-arity change is a one-place change.
    testImplementation(project(":testfx"))
}

// PURE. The COMPILER: authored YAML+DSL → immutable validated Snapshot. Stays
// Bukkit-free by taking a PlatformResolvers facade by INJECTION (tests pass a
// fake; production passes se-platform/resolve), so cross-version resolution is a
// compile dependency without coupling the compiler to Bukkit
// (docs/architecture.md §2.1, §9).
