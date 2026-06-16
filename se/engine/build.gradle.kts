plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches this module:
    // compileOnly(libs.paper.api.floor)
    // api(project(":compile"))
    // api(project(":item"))
    // api(project(":platform"))
    // api(project(":api"))
}

// The RUNTIME. Bukkit-aware, FLOOR API only, version-agnostic. Stateless systems
// (one per trigger family) walk a pre-flattened WornState and execute abilities
// through the Sink — the single mutation boundary. Holds the effect/condition/
// selector/trigger kinds, the fixed activation pipeline, the contribute-then-
// resolve arbiters, and the affinity-routed Dispatcher (docs/architecture.md §3, §6).
