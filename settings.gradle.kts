// StarEnchants — Gradle multi-module build.
// The module spine is the CONTENT LIFECYCLE (schema → compile → engine → content),
// with version/Folia edges as leaves and one universal shaded jar.
// See docs/architecture.md §2 for the rationale behind each boundary.

plugins {
    // Auto-provision the Java 17 floor toolchain (and the 21 used by the matrix)
    // on any machine or CI runner that lacks it, so a single installed JDK is
    // enough to build. See docs/architecture.md §11.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "starenchants"

// Every module lives under se/; the leaf name drops the se- prefix (the parent
// directory already namespaces them). Project paths are flat (:schema, :engine, …)
// so cross-module dependencies read terse: api(project(":schema")).
listOf(
    // Pure language + compiler (zero Bukkit; deterministically unit-testable).
    "schema",        // the DSL as a typed LANGUAGE DEFINITION (grammar, type system, diagnostics)
    "compile",       // authored YAML+DSL → immutable validated Snapshot
    // The runtime + item data + feature shells (Bukkit-aware, floor API).
    "engine",        // stateless systems, the activation pipeline, kinds, arbiters, the Sink
    "item",          // PDC codec, ItemView cache, WornState resolver, lore render
    "feature",       // thin Bukkit feature shells (enchants/armor/crystals/heroic/souls/…)
    // Version + Folia absorption, migration, public API.
    "platform",      // boot-time resolvers, Scheduling, capabilities, protection/economy SPIs
    "migrate",       // legacy NBT reader + EE/EA/AE config importer
    "pack",          // config packs: ZIP snapshot of the whole config surface (export/apply/swap)
    "api",           // public surface: events + the registration SPI + read-only queries
    "bootstrap",     // the StarEnchants JavaPlugin: composition root, content load, /se reload
    // Newer-than-floor edge, behind a Capabilities probe. (A modern-only edge module is
    // unnecessary: the few newer-than-floor surfaces — Brigadier, profile heads, BlockData
    // sends — are gated inline via se-platform/caps + the bootstrap overlay.)
    "compat-folia",  // Folia region/entity/global schedulers
    // Live Paper + Folia in-server matrix harness.
    "tester",
    // Test-support ONLY (never shipped): the shared unit-test fixtures that exist today —
    // FakeEffectCtx, SpecDrivenCtx, Defs — so a record-arity change lives in ONE place instead
    // of being copy-pasted per module. The others the design records (YamlFixture/RenderGolden/
    // TtlStoreAdapter/CorpusLoader) are built just-in-time when first needed
    // (docs/dev/internals/testing-architecture.md).
    "testfx",
    // Tool-only (like tester, NOT shipped in the plugin jar): a Java2D generator that renders item
    // tooltips + GUIs to committable PNGs, reusing the plugin's own LoreRenderer so previews can't drift
    // (docs/screenshot-rendering.md). Run via `./gradlew :imagegen:renderImages`.
    "imagegen",
    // Tool-only (like tester/imagegen, NEVER shipped): the JMH microbenchmarks that guard the combat hot path
    // (docs/architecture.md §8, performance-hot-paths). `:bench:jmhCheck` runs a short pass and FAILS the build
    // on a throughput floor or a per-op allocation budget regression; bootstrap never depends on it.
    "bench",
    // Third-party plugin integrations — bundled INTO the core fat jar and active out of the box, but
    // SOFT: each plugin's API is compileOnly (never bundled) and each bridge only loads when its plugin is
    // present, so no integration plugin is ever required (docs/decisions/0027, superseding 0017).
    "integrate",
).forEach { name ->
    include(name)
    project(":$name").projectDir = file("se/$name")
}
