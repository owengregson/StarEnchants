plugins {
    `java-library`
}

// ── Cross-version overlay (the OPTIONAL 1.8.9 fork) — docs/legacy-1.8.9-codeshare-design.md §1, §4 ──
// The concrete Sink impl (DispatchSink) and the main-hand read (HeldItem) fork by server version and live
// under overlay/<target>, added as a srcDir of `main` by -Pse.target (default `modern`). The Sink +
// SinkReadback interfaces, the systems, the pipeline, the kinds, AbilityExecutor and FactPopulator stay
// shared in src/ (1.8-safe). `-Pse.target=legacy` compiles main + overlay/legacy against the real Spigot
// 1.8.8 server jar, so the legacy DispatchSink's NMS is javac-checked, not assumed.
val legacyTarget = (project.findProperty("se.target") as String?) == "legacy"

if (legacyTarget) {
    repositories {
        mavenLocal {
            content {
                includeGroup("org.bukkit")
                includeGroup("org.spigotmc")
            }
        }
    }
}

sourceSets["main"].java.srcDir(if (legacyTarget) "overlay/legacy" else "overlay/modern")

dependencies {
    // Floor API only for modern; the legacy lane swaps in the real 1.8.8 server jar (org.bukkit + v1_8_R3
    // NMS) so the legacy DispatchSink is type-checked against the actual 1.8 surface. compileOnly — the
    // server provides it.
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
    } else {
        compileOnly(libs.paper.api.floor)
    }

    // The compiled world the engine walks: Ability, Snapshot, Affinity, the
    // CompiledEffect/Condition flyweights (brings :schema transitively).
    api(project(":compile"))

    // The affinity-routed Sink dispatcher (engine/sink) is "the only code that knows about
    // threads" (docs/architecture.md §3.6): it routes intents through platform.sched.Scheduling
    // and resolves interned handle ids to live objects through platform.resolve.RuntimeHandles.
    // api (not implementation) because RuntimeHandles appears on DispatchSink's public surface.
    // Acyclic: platform → compile, engine → {compile, platform}; platform never depends on engine.
    api(project(":platform"))

    // Tests touch Bukkit SPI types (Sink/EffectCtx signatures), so paper-api is
    // on the test classpath too.
    testImplementation(libs.paper.api.floor)

    // Mock-host effect tests: a mocked Sink + mocked entities verify a kind emits
    // the right intents, without a server (docs/architecture.md §1.3).
    testImplementation(libs.mockito.core)

    // Shared fixtures: FakeEffectCtx (a real, strict EffectCtx that throws on an unset param) and
    // SpecDrivenCtx, used by the collapsed effect-kind wiring tables in place of per-test ctx mocks.
    testImplementation(project(":testfx"))
}

// `./gradlew regenDocs` runs the drift tests in regen mode; the engine half rewrites the DSL
// reference + the docs-site catalog from the live registries.
tasks.register<Test>("regenDocs") {
    group = "documentation"
    description = "Regenerate dsl-reference.md and website catalog.json from the engine registries."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("*ReferenceDocDriftTest")
        includeTestsMatching("*ReferenceCatalogDriftTest")
    }
    systemProperty("se.doc.regen", "true")
    outputs.upToDateWhen { false }
}

// The RUNTIME. Bukkit-aware, FLOOR API only, version-agnostic. Stateless systems
// (one per trigger family) walk a pre-flattened WornState and execute abilities
// through the Sink — the single mutation boundary. Holds the effect/condition/
// selector/trigger kinds, the fixed activation pipeline, the contribute-then-
// resolve arbiters, and the affinity-routed Dispatcher (docs/architecture.md §3, §6).
