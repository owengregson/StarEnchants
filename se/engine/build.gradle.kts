plugins {
    `java-library`
}

dependencies {
    // Floor API only: the runtime compiles against paper-api 1.17.1 and emits
    // Java 17 class files so the one universal jar loads across 1.17.1 → 26.1.x
    // (docs/architecture.md §1.1, §11). compileOnly — the server provides it.
    compileOnly(libs.paper.api.floor)

    // The compiled world the engine walks: Ability, Snapshot, Affinity, the
    // CompiledEffect/Condition flyweights (brings :schema transitively).
    api(project(":compile"))

    // Tests touch Bukkit SPI types (Sink/EffectCtx signatures), so paper-api is
    // on the test classpath too.
    testImplementation(libs.paper.api.floor)

    // Mock-host effect tests: a mocked Sink + mocked entities verify a kind emits
    // the right intents, without a server (docs/architecture.md §1.3).
    testImplementation(libs.mockito.core)
}

// The RUNTIME. Bukkit-aware, FLOOR API only, version-agnostic. Stateless systems
// (one per trigger family) walk a pre-flattened WornState and execute abilities
// through the Sink — the single mutation boundary. Holds the effect/condition/
// selector/trigger kinds, the fixed activation pipeline, the contribute-then-
// resolve arbiters, and the affinity-routed Dispatcher (docs/architecture.md §3, §6).
